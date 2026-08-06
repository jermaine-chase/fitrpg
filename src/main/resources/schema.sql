-- ===========================================================================
--  Fitness LitRPG schema bootstrap
--  Executed automatically on every startup by Spring's SQL init
--  (spring.sql.init.mode=always). Every statement is idempotent.
-- ===========================================================================

CREATE TABLE IF NOT EXISTS users (
    id                         UUID         PRIMARY KEY,
    username                   VARCHAR(50)  NOT NULL,
    password_hash              VARCHAR(255) NOT NULL,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    default_friend_visibility  VARCHAR(20)  NOT NULL DEFAULT 'BASIC',
    security_question          VARCHAR(255),
    security_answer_hash       VARCHAR(255),
    is_admin                   BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- Add default_friend_visibility to existing users tables that predate friend visibility controls.
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_friend_visibility VARCHAR(20) NOT NULL DEFAULT 'BASIC';

-- Add password-recovery security question to existing users tables that predate it.
-- Nullable: accounts created before this feature (and anyone who skips setting one)
-- simply have no self-service recovery path until they set a question.
ALTER TABLE users ADD COLUMN IF NOT EXISTS security_question    VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS security_answer_hash VARCHAR(255);

-- Add the admin role to existing users tables that predate it.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- One-time backfill for installs that already had accounts before the admin
-- role existed: promote whichever account registered first, but only if no
-- one is an admin yet (so this is a no-op on every later startup).
UPDATE users SET is_admin = TRUE
WHERE id = (SELECT id FROM users ORDER BY created_at ASC, id ASC LIMIT 1)
AND NOT EXISTS (SELECT 1 FROM users WHERE is_admin = TRUE);

CREATE TABLE IF NOT EXISTS characters (
    id                UUID         PRIMARY KEY,
    user_id           UUID         REFERENCES users (id) ON DELETE CASCADE,
    character_name    VARCHAR(255) NOT NULL,
    current_level     INT          NOT NULL DEFAULT 1,
    overall_xp        INT          NOT NULL DEFAULT 0,
    streak_count      INT          NOT NULL DEFAULT 0,
    last_workout_date DATE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add user_id to existing characters tables that predate player accounts.
ALTER TABLE characters ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users (id) ON DELETE CASCADE;

-- Grace tokens that preserve a streak on a missed day instead of halving it.
ALTER TABLE characters ADD COLUMN IF NOT EXISTS streak_freeze_count INT NOT NULL DEFAULT 1;

-- Cosmetic-only customization: a picked avatar. No gameplay effect.
-- (The equipped-title column is added further down, after the achievements
-- table it references exists — see there.)
ALTER TABLE characters ADD COLUMN IF NOT EXISTS avatar_id VARCHAR(30) NOT NULL DEFAULT 'wolf';

CREATE TABLE IF NOT EXISTS character_stats (
    id            UUID        PRIMARY KEY,
    character_id  UUID        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    stat_type     VARCHAR(3)  NOT NULL,           -- 'STR','DEX','CON','WIL'
    current_level INT         NOT NULL DEFAULT 1,
    current_xp    INT         NOT NULL DEFAULT 0,
    status        VARCHAR(50) NOT NULL DEFAULT 'Active',
    CONSTRAINT uq_character_stat UNIQUE (character_id, stat_type)
);

CREATE TABLE IF NOT EXISTS workout_logs (
    id                 UUID         PRIMARY KEY,
    character_id       UUID         NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_id           VARCHAR(255),
    quest_title        VARCHAR(255),
    stat_type          VARCHAR(3),
    base_xp_earned     INT,
    multiplier_applied NUMERIC(5, 2),
    final_xp_awarded   INT,
    logged_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add stat_type to existing workout_logs tables that predate the per-stat dashboard.
ALTER TABLE workout_logs ADD COLUMN IF NOT EXISTS stat_type VARCHAR(3);

CREATE TABLE IF NOT EXISTS daily_quest_assignments (
    id             UUID        PRIMARY KEY,
    character_id   UUID        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_id       VARCHAR(50) NOT NULL REFERENCES quests (quest_id) ON DELETE CASCADE,
    assigned_date  DATE        NOT NULL,
    CONSTRAINT uq_daily_quest_assignment UNIQUE (character_id, assigned_date)
);

CREATE TABLE IF NOT EXISTS quests (
    quest_id           VARCHAR(50)  PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    target_stat        VARCHAR(3)   NOT NULL,
    base_character_xp  INT          NOT NULL DEFAULT 0,
    base_stat_xp       INT          NOT NULL DEFAULT 0,
    min_level          INT          NOT NULL DEFAULT 1
);

-- Add min_level to existing quests tables that were created before this column existed.
ALTER TABLE quests ADD COLUMN IF NOT EXISTS min_level INT NOT NULL DEFAULT 1;

-- Add the category chip + optional time estimate used by the quest-board filter chips.
ALTER TABLE quests ADD COLUMN IF NOT EXISTS tag VARCHAR(20);
ALTER TABLE quests ADD COLUMN IF NOT EXISTS estimated_minutes INT;

-- Seed quests (idempotent via ON CONFLICT DO NOTHING).
-- Tier 1 — available from level 1
INSERT INTO quests (quest_id, title, description, target_stat, base_character_xp, base_stat_xp, min_level)
VALUES
    ('Q-1001', 'Morning 5k Run',
        'Complete a 5 km run before noon.',
        'CON', 60, 40, 1),
    ('Q-1002', 'Weight Training Session',
        'Complete a full-body strength session: 3 sets of squats, bench press, and rows.',
        'STR', 50, 50, 1),
    ('Q-1003', 'Yoga Flow',
        'Complete a 30-minute yoga or stretching session.',
        'DEX', 40, 60, 1),
    ('Q-1004', 'Meditation',
        'Meditate for at least 15 minutes without distraction.',
        'WIL', 30, 70, 1),
    ('Q-1005', 'Sprint Intervals',
        'Run 8 × 100 m sprints with 60-second rest periods.',
        'DEX', 70, 30, 1),
    ('Q-1006', 'Long Hike',
        'Hike at least 10 km on a trail.',
        'CON', 80, 20, 1),
    ('Q-1007', 'Heavy Deadlift Day',
        'Perform 3 sets of 5 deadlifts at a challenging working weight.',
        'STR', 60, 60, 1),
    ('Q-1008', 'Cold-Water Plunge',
        'Submerge in cold water for at least 2 minutes.',
        'WIL', 50, 50, 1),

    -- Tier 2 — available from level 10
    ('Q-2001', 'Morning 10k Run',
        'Complete a 10 km run at a steady pace.',
        'CON', 100, 80, 10),
    ('Q-2002', 'Powerlifting Focus',
        'Complete 5 sets of 3 reps on squat, bench, and deadlift at 85% of your 1-rep max.',
        'STR', 110, 90, 10),
    ('Q-2003', 'Mobility Mastery',
        'Complete a 60-minute mobility session targeting all major joints.',
        'DEX', 80, 120, 10),
    ('Q-2004', 'Focus Block',
        'Complete two 25-minute deep-focus meditation sessions with a 5-minute break.',
        'WIL', 70, 130, 10),
    ('Q-2005', 'Hill Sprint Circuit',
        'Complete 10 × 200 m hill sprints at maximum effort.',
        'DEX', 120, 80, 10),

    -- Tier 3 — available from level 25
    ('Q-3001', 'Half Marathon',
        'Run 21.1 km at a sustained effort.',
        'CON', 200, 150, 25),
    ('Q-3002', 'One-Rep Max Attempt',
        'Attempt a personal record on squat, bench press, or deadlift after a full warm-up.',
        'STR', 180, 170, 25),
    ('Q-3003', 'Advanced Gymnastics Session',
        'Train handstands, L-sits, or ring work for 45 minutes.',
        'DEX', 160, 190, 25),
    ('Q-3004', 'Hour of Power Meditation',
        'Complete a single 60-minute uninterrupted meditation session.',
        'WIL', 140, 210, 25),

    -- Tier 4 — available from level 50
    ('Q-4001', 'Marathon',
        'Complete a full 42.2 km marathon.',
        'CON', 400, 300, 50),
    ('Q-4002', 'Iron Athlete Day',
        'Complete a 2-hour strength session: max-effort squats, bench, deadlift, and overhead press.',
        'STR', 380, 320, 50),
    ('Q-4003', 'Acrobatic Flow',
        'Execute a continuous 30-minute acrobatic flow including flips, handstand walks, or parkour.',
        'DEX', 360, 340, 50),
    ('Q-4004', 'Void Meditation',
        'Sit in complete sensory stillness for 90 minutes.',
        'WIL', 320, 380, 50)

ON CONFLICT (quest_id) DO NOTHING;

-- One-time backfill of tag/estimated_minutes for the seed quests above, for
-- installs that already had these rows before the columns existed. Guarded by
-- "tag IS NULL" so it never overwrites an admin's later edits.
UPDATE quests SET tag = 'CARDIO',    estimated_minutes = 30  WHERE quest_id = 'Q-1001' AND tag IS NULL;
UPDATE quests SET tag = 'STRENGTH',  estimated_minutes = 45  WHERE quest_id = 'Q-1002' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 30  WHERE quest_id = 'Q-1003' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 15  WHERE quest_id = 'Q-1004' AND tag IS NULL;
UPDATE quests SET tag = 'INTENSE',   estimated_minutes = 20  WHERE quest_id = 'Q-1005' AND tag IS NULL;
UPDATE quests SET tag = 'CARDIO',    estimated_minutes = 90  WHERE quest_id = 'Q-1006' AND tag IS NULL;
UPDATE quests SET tag = 'STRENGTH',  estimated_minutes = 50  WHERE quest_id = 'Q-1007' AND tag IS NULL;
UPDATE quests SET tag = 'INTENSE',   estimated_minutes = 10  WHERE quest_id = 'Q-1008' AND tag IS NULL;
UPDATE quests SET tag = 'CARDIO',    estimated_minutes = 55  WHERE quest_id = 'Q-2001' AND tag IS NULL;
UPDATE quests SET tag = 'STRENGTH',  estimated_minutes = 60  WHERE quest_id = 'Q-2002' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 60  WHERE quest_id = 'Q-2003' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 55  WHERE quest_id = 'Q-2004' AND tag IS NULL;
UPDATE quests SET tag = 'INTENSE',   estimated_minutes = 35  WHERE quest_id = 'Q-2005' AND tag IS NULL;
UPDATE quests SET tag = 'CARDIO',    estimated_minutes = 130 WHERE quest_id = 'Q-3001' AND tag IS NULL;
UPDATE quests SET tag = 'STRENGTH',  estimated_minutes = 75  WHERE quest_id = 'Q-3002' AND tag IS NULL;
UPDATE quests SET tag = 'INTENSE',   estimated_minutes = 45  WHERE quest_id = 'Q-3003' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 60  WHERE quest_id = 'Q-3004' AND tag IS NULL;
UPDATE quests SET tag = 'CARDIO',    estimated_minutes = 300 WHERE quest_id = 'Q-4001' AND tag IS NULL;
UPDATE quests SET tag = 'STRENGTH',  estimated_minutes = 120 WHERE quest_id = 'Q-4002' AND tag IS NULL;
UPDATE quests SET tag = 'INTENSE',   estimated_minutes = 30  WHERE quest_id = 'Q-4003' AND tag IS NULL;
UPDATE quests SET tag = 'RECOVERY',  estimated_minutes = 90  WHERE quest_id = 'Q-4004' AND tag IS NULL;

CREATE TABLE IF NOT EXISTS achievements (
    code          VARCHAR(50)  PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    icon          VARCHAR(10)  NOT NULL,
    criteria_type VARCHAR(30)  NOT NULL,
    threshold     INT          NOT NULL
);

-- Seed the badge catalog (idempotent via ON CONFLICT DO NOTHING).
INSERT INTO achievements (code, name, description, icon, criteria_type, threshold)
VALUES
    ('FIRST_STEPS',  'First Steps',       'Claim your very first quest.',                          '🥾', 'FIRST_CLAIM',             1),
    ('LEVEL_10',     'Rising Operative',  'Reach character level 10.',                              '⭐', 'LEVEL_MILESTONE',         10),
    ('LEVEL_25',     'Veteran Operative', 'Reach character level 25.',                              '🌟', 'LEVEL_MILESTONE',         25),
    ('LEVEL_50',     'Iron Legend',       'Reach character level 50.',                              '👑', 'LEVEL_MILESTONE',         50),
    ('STREAK_7',     'One Week Strong',   'Reach a 7-day training streak.',                         '🔥', 'STREAK_MILESTONE',        7),
    ('STREAK_30',    'Unbreakable',       'Reach a 30-day training streak.',                        '🌋', 'STREAK_MILESTONE',        30),
    ('CLAIMS_50',    'Grinder',           'Claim 50 quests over your lifetime.',                    '⚔️', 'TOTAL_CLAIMS_MILESTONE',  50),
    ('CLAIMS_200',   'Relentless',        'Claim 200 quests over your lifetime.',                   '🗡️', 'TOTAL_CLAIMS_MILESTONE',  200),
    ('BALANCED_10',  'Well Rounded',      'Bring all four attributes to level 10 simultaneously.',  '🧭', 'ALL_STATS_LEVEL',         10)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS character_achievements (
    id               UUID        PRIMARY KEY,
    character_id     UUID        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    achievement_code VARCHAR(50) NOT NULL REFERENCES achievements (code) ON DELETE CASCADE,
    unlocked_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_character_achievement UNIQUE (character_id, achievement_code)
);

CREATE INDEX IF NOT EXISTS idx_character_achievements_character_id ON character_achievements (character_id);

-- Cosmetic-only equipped title, drawn from the character's own unlocked
-- achievements (enforced in application code, not a DB constraint, since a
-- character can equip at most the achievements *they* hold). Added here,
-- after the achievements table it references.
ALTER TABLE characters ADD COLUMN IF NOT EXISTS title_achievement_code VARCHAR(50) REFERENCES achievements (code) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS friendships (
    id                   UUID        PRIMARY KEY,
    requester_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at         TIMESTAMP,
    requester_visibility VARCHAR(20),
    addressee_visibility VARCHAR(20),
    CONSTRAINT uq_friendship_pair UNIQUE (requester_id, addressee_id),
    CONSTRAINT chk_friendship_not_self CHECK (requester_id <> addressee_id)
);

-- Add per-friendship visibility overrides to existing friendships tables.
ALTER TABLE friendships ADD COLUMN IF NOT EXISTS requester_visibility VARCHAR(20);
ALTER TABLE friendships ADD COLUMN IF NOT EXISTS addressee_visibility VARCHAR(20);

-- JWTs logged out before their natural expiry. Rows are pruned once expires_at
-- passes, since an expired token is rejected on that basis alone.
CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti        VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP   NOT NULL
);

-- Helpful secondary indexes for FK lookups.
CREATE INDEX IF NOT EXISTS idx_character_stats_character_id ON character_stats (character_id);
CREATE INDEX IF NOT EXISTS idx_workout_logs_character_id    ON workout_logs (character_id);
CREATE INDEX IF NOT EXISTS idx_daily_quest_assignments_char ON daily_quest_assignments (character_id, assigned_date);
CREATE INDEX IF NOT EXISTS idx_characters_user_id           ON characters (user_id);
CREATE INDEX IF NOT EXISTS idx_friendships_requester_id     ON friendships (requester_id);
CREATE INDEX IF NOT EXISTS idx_friendships_addressee_id     ON friendships (addressee_id);
