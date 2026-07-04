# Fitness LitRPG — Backend

A production-ready Spring Boot 3 / Java 17 backend for a gamified fitness
LitRPG. Characters earn XP by completing workout "quests", build streaks for XP
multipliers, level up via a polynomial curve, and lose XP overnight if they go
inactive.

## Stack

- Java 17, Spring Boot 3.3.x (Spring Web, Spring Data JPA, Validation)
- PostgreSQL (Heroku Postgres in production)
- Schema via raw idempotent DDL (`schema.sql`) run automatically on startup
- Deploys on Heroku out of the box (reads `DATABASE_URL`)

## Package layout

```
com.litrpg.fitness
├── config       DataSourceConfig — parses Heroku DATABASE_URL into JDBC props
├── model        Character, CharacterStat, WorkoutLog, StatType
├── repository   JpaRepository interfaces
├── service      GameEngineService, CharacterService, MidnightDecayService, GameFormulas
├── dto          QuestDTO, CreateCharacterRequest, CharacterSheetResponse, StatResponse
├── controller   CharacterController
└── exception    ResourceNotFoundException, GlobalExceptionHandler
```

## Game rules

- **Leveling:** `nextLevelXp = floor(100 * level^1.5)`. XP carries over on level-up.
- **Streak multiplier:** `1.0 + min(streak * 0.05, 0.50)` (caps at +50%).
- **Streak soft-landing:** consecutive day → +1; missed day(s) → halved (min 1),
  never reset to 0.
- **Midnight decay:** nightly at 00:00, every stat of an inactive character
  loses `floor(5% * nextLevelXp)`; XP floors at 0, levels never drop, and a stat
  at 0 XP becomes `"Rusty"`. Gaining XP later flips it back to `"Active"`.

## Run locally

Requires a local Postgres named `fitrpg_db` (or edit `application.properties`).

```bash
mvn spring-boot:run
```

The app boots on `http://localhost:8080`. `schema.sql` creates the tables on
first start (and is a no-op thereafter).

## Deploy to Heroku

```bash
heroku create your-app-name
heroku addons:create heroku-postgresql:essential-0   # provisions DATABASE_URL
git push heroku main
```

No DB config needed: `DataSourceConfig` reads `DATABASE_URL`, converts the
`postgres://…` URI into a `jdbc:postgresql://…?sslmode=require` URL, and splits
out the username/password. The `Procfile` launches the built jar on `$PORT`.

## Frontend (standalone UI)

`frontend/index.html` is a single self-contained page (no build step) that
consumes this API. Open it directly in a browser, or serve it statically:

```bash
cd frontend
python3 -m http.server 5500   # then visit http://localhost:5500
```

On first load it asks for the API endpoint (default `http://localhost:8080`)
and an operative name, then calls `POST /api/character`. It stores only the
character **id** and a local combat-log narrative in `localStorage`; all game
state (XP, levels, streak, stats) is owned by the server. Use the ⚙ button to
repoint it at another instance (e.g. your Heroku URL). "Abandon Run" calls
`DELETE /api/character/{id}`.

CORS is handled by `WebConfig` (`app.cors.allowed-origins`, default `*`).
Tighten it in production via the `APP_CORS_ALLOWED_ORIGINS` env var.

> Note: each quest's reward maps to the `/claim` contract — one target stat
> plus character XP (e.g. `+50 STR XP · +100 Character XP`).


```bash
curl -X POST http://localhost:8080/api/character \
  -H 'Content-Type: application/json' \
  -d '{"characterName":"Ragnar"}'
```

### Get the full character sheet
```bash
curl http://localhost:8080/api/character/{id}
```

### Claim quest rewards
```bash
curl -X POST http://localhost:8080/api/character/{id}/claim \
  -H 'Content-Type: application/json' \
  -d '{
        "questId":"Q-1001",
        "title":"Morning 5k Run",
        "targetStat":"CON",
        "baseCharacterXp":50,
        "baseStatXp":30
      }'
```

Both `GET` and `claim` return the updated sheet:

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
