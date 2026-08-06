# Fitness LitRPG — Backend

A Spring Boot 3 / Java 17 backend (with a standalone vanilla-JS frontend) for
a gamified fitness LitRPG. Characters earn XP by completing workout "quests",
build streaks for XP multipliers, level up via a polynomial curve, lose XP
overnight if they go inactive, get a bonus-boosted "Daily Focus" quest each
day, can friend other players and share activity with them, and can recover
their account with a security question if they forget their password.

## Stack

- Java 17, Spring Boot 3.3.x (Spring Web, Spring Security, Spring Data JPA, Validation)
- PostgreSQL, accessed through Spring Data JPA/Hibernate
- Schema via raw idempotent DDL (`schema.sql`, `CREATE TABLE IF NOT EXISTS` /
  `ADD COLUMN IF NOT EXISTS`) run automatically on every startup
- Frontend: two static, framework-free HTML/CSS/JS pages served by Spring Boot itself

## Package layout

```
com.litrpg.fitness
├── config       DataSourceConfig (DATABASE_URL support), SecurityConfig, WebConfig (CORS)
├── security     JwtService, JwtAuthenticationFilter, UserPrincipal — player JWT auth
├── model        User, Character, CharacterStat, WorkoutLog, Quest, StatType,
│                DailyQuestAssignment, Friendship, FriendshipStatus, FriendVisibility,
│                RevokedToken
├── repository   JpaRepository interfaces
├── service      AuthService, GameEngineService, CharacterService, DailyQuestService,
│                FriendService, MidnightDecayService, GameFormulas
├── dto          Auth/Character/Quest/Friend/DailyQuest request & response DTOs
├── controller   AuthController, CharacterController, QuestController,
│                FriendController, AdminController
└── exception    ResourceNotFoundException, GlobalExceptionHandler
```

## Domain model

- **Character** — a level-1-start avatar with overall level/XP, a login
  streak, and four independently-leveled stats: `STR`, `DEX`, `CON`, `WIL`.
- **Quest** — an admin-curated catalog of tasks gated by `minLevel` and tied
  to a target stat. "Logging a workout" means claiming a quest.
- **WorkoutLog** — an immutable, append-only record of every claim (quest,
  stat trained, XP earned, streak multiplier applied), used for the progress
  dashboard and the friend activity feed.
- **DailyQuestAssignment** — the one quest highlighted as a character's
  "Daily Focus" for a given date.
- **User / Friendship** — player accounts and the friend graph between them,
  with per-friendship visibility overrides.

## Game rules

- **Leveling:** `nextLevelXp = floor(100 * level^1.5)`. XP carries over on level-up.
- **Streak multiplier:** `1.0 + min(streak * 0.05, 0.50)` (caps at +50%).
- **Streak soft-landing:** consecutive day → +1; missed day(s) → halved (min 1),
  never reset to 0. Claiming a quest a second time on the same day is
  rejected (409) — the streak/XP pipeline only advances once per day.
- **Daily Focus quest:** on first request each day, a character is assigned
  one quest — biased toward whichever of their four stats is currently
  weakest — as their "Daily Focus". Claiming it grants an extra +25% XP on
  top of the normal reward pipeline.
- **Bonus challenge:** every claim has a 15% chance of rolling a harder,
  stat-specific optional variant of the quest, worth an extra 1.5× XP.
- **Midnight decay:** nightly at 00:00, every stat of an inactive character
  loses `floor(5% * nextLevelXp)`; XP floors at 0, levels never drop, and a stat
  at 0 XP becomes `"Rusty"`. Gaining XP later flips it back to `"Active"`.

## Auth, roles & account recovery

A single mechanism covers both regular players and admins: register/login
against `/api/auth/**` (open, except as noted below) to get a JWT, then send
it as `Authorization: Bearer <token>` on every `/api/character/**` and
`/api/friends/**` call. Each character is owned by the account that created
it; requests for another account's character get a 404 (not a 403, so
ownership isn't leaked). Tokens expire after `jwt.expiration-ms` (default
24h). `POST /api/auth/logout` revokes the bearer token immediately (requires
auth) by recording its JWT ID until natural expiry.

**Admin role** — `User.isAdmin` gates `/api/admin/**` (`hasRole("ADMIN")`,
enforced from an `admin` claim baked into the JWT at login time). There's no
separate admin account: **the very first account ever registered on the
instance becomes an admin automatically**, and every account after that is a
regular player. An admin can see and edit every character and the entire
quest catalog regardless of owner. There is currently no endpoint to promote
additional accounts — do it directly in the database
(`UPDATE users SET is_admin = TRUE WHERE username = '...'`) if you need more
than one, then have that account log in again (the role is baked into the
token at issuance, so an already-issued token won't pick up the change).

**Password recovery** uses a security question set at registration (required)
rather than email, since the app has no mail infrastructure:

- `POST /api/auth/forgot-password` `{username}` (open) → returns that
  account's security question, 404 if none is configured.
- `POST /api/auth/reset-password` `{username, securityAnswer, newPassword}`
  (open) → verifies the answer (case/whitespace-insensitive) and sets a new
  password, signing the player in immediately.
- `GET`/`PUT /api/auth/security-question` (authenticated) → view or set/change
  your own recovery question at any time; updating requires your current
  password. Accounts created before this feature have no question until they
  set one this way.

Note: a security-question flow inherently confirms whether a username
exists, and there's no rate-limiting on any endpoint in this app — treat this
as best-effort recovery for a personal project, not a hardened flow.

Override in production via env vars: `JWT_SECRET` (random string, ≥32 bytes)
and `JWT_EXPIRATION_MS`.

```bash
# Register (also returns a token, no separate login needed right after)
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"ragnar","password":"correct-horse-battery","securityQuestion":"First pet?","securityAnswer":"Sparky"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"ragnar","password":"correct-horse-battery"}'

# Your own account info (username + admin flag), read fresh from the DB
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

## Friends & visibility

Player accounts can send/accept/decline friend requests. Once accepted, each
side controls how much of their character sheet the other can see:

- **`NONE`** — connected, but no character details shown.
- **`BASIC`** — name and level only.
- **`FULL`** — the entire sheet (XP, streak, stats).

Visibility has an account-wide default (`/api/friends/settings`) that can be
overridden per friendship (`PUT /api/friends/{id}/visibility`). The same
visibility level also gates the **activity feed**
(`GET /api/friends/feed`), which surfaces friends' recent quest claims —
`NONE` friends are omitted entirely, `BASIC` shows only that a claim
happened, `FULL` shows the quest, stat, and XP earned.

## Progress dashboard

`GET /api/character/{id}/history` returns the character's full claim history
(most recent first), which the frontend uses to chart daily XP earned over
the last 14 days and the all-time XP split across the four stats.

## Run locally

Requires a local Postgres named `fitrpg_db` (or edit `application.properties`).

```bash
mvn spring-boot:run
```

The app boots on `http://localhost:8080`. `schema.sql` creates/upgrades the
tables on every start and is a no-op if nothing changed. `DataSourceConfig`
will build the datasource from a `DATABASE_URL` env var (the
`postgres://user:pass@host:port/db` convention used by many hosting
providers) if one is set, otherwise it falls back to the
`spring.datasource.*` properties for local development.

## Frontend (standalone UI)

`src/main/resources/static/` holds two self-contained pages (no build step),
served by Spring Boot itself at the app's root:

- **`index.html`** — the player terminal. Covers registration (including
  setting a security question), login, forgot-password recovery, character
  creation, the quest board (with a highlighted Daily Focus quest), attribute
  bars, a progress dashboard (XP-over-time chart + per-stat XP breakdown), a
  combat log, an account-security panel for updating your recovery question,
  and an "Allies" overlay for friend requests, per-friend visibility, and the
  Guild Hall activity feed. All game state (XP, levels, streak, stats) is
  owned by the server; the JWT and character id are cached in `localStorage`.
  Claiming a quest animates the XP bars (CSS width transitions) and plays a
  short synthesized cue via the Web Audio API — a distinct ascending chime
  plus a golden HUD flash on level-up, a separate sparkle cue and violet
  flash when the 15% bonus-challenge roll fires — with no audio assets or
  build tooling involved.
- **`admin.html`** — the admin console for managing the quest catalog and
  character roster directly. It reuses the same player JWT `index.html`
  stores in `localStorage` (there's no separate admin login); if the signed-in
  account isn't an admin — or no one is signed in — it shows an access-denied
  screen instead. The topbar "⚙ Admin" link on `index.html` itself is only
  shown to admin accounts. See [Auth, roles & account recovery](#auth-roles--account-recovery).

Both pages default to **same-origin** relative API calls, since Spring serves
them itself. If you host the frontend separately from the API, repoint it
without editing any files: pass `?api=https://your-api-host` once (the value
is cached in `localStorage` under `ironpath_api_base` and reused on later
visits) or set it directly — `index.html` has an "API Base URL" field in the
Account overlay, `admin.html` has one in its topbar. Clearing the field (or
passing `?api=`) reverts to same-origin.

CORS is handled by `WebConfig` (`app.cors.allowed-origins`, default `*`).
Tighten it in production via the `APP_CORS_ALLOWED_ORIGINS` env var.

## API quick reference

```bash
# Register + get a token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"ragnar","password":"correct-horse-battery","securityQuestion":"First pet?","securityAnswer":"Sparky"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Create a character (owned by the authenticated account)
curl -X POST http://localhost:8080/api/character \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"characterName":"Ragnar"}'
```

### Character & quests

```bash
# Full character sheet
curl http://localhost:8080/api/character/{id} -H "Authorization: Bearer $TOKEN"

# Claim quest rewards
curl -X POST http://localhost:8080/api/character/{id}/claim \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"questId":"Q-1001"}'

# Today's Daily Focus quest (assigned on first call of the day)
curl http://localhost:8080/api/character/{id}/daily -H "Authorization: Bearer $TOKEN"

# Claim history, most recent first
curl http://localhost:8080/api/character/{id}/history -H "Authorization: Bearer $TOKEN"

# Quest catalog, optionally filtered to a level
curl "http://localhost:8080/api/quests?level=10"
```

`GET` and `claim` both return the character sheet:

```json
{
  "id": "…",
  "characterName": "Ragnar",
  "currentLevel": 1,
  "overallXp": 50,
  "xpForNextLevel": 100,
  "streakCount": 1,
  "lastWorkoutDate": "2026-06-15",
  "createdAt": "2026-06-15T09:00:00",
  "stats": [
    { "statType": "CON", "currentLevel": 1, "currentXp": 30, "xpForNextLevel": 100, "status": "Active" },
    ...
  ]
}
```

### Friends

```bash
# Send a friend request
curl -X POST http://localhost:8080/api/friends/requests \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"username":"other_player"}'

# Accept a request
curl -X POST http://localhost:8080/api/friends/requests/{friendshipId}/accept \
  -H "Authorization: Bearer $TOKEN"

# Recent activity from accepted friends, trimmed to what they've chosen to share
curl http://localhost:8080/api/friends/feed -H "Authorization: Bearer $TOKEN"
```

### Password recovery

```bash
# Step 1: get the security question
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H 'Content-Type: application/json' -d '{"username":"ragnar"}'

# Step 2: answer it and set a new password
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d '{"username":"ragnar","securityAnswer":"Sparky","newPassword":"new-correct-horse"}'
```
