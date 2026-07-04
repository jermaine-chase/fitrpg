-- ===========================================================================
--  Fitness LitRPG schema bootstrap
--  Executed automatically on every startup by Spring's SQL init
--  (spring.sql.init.mode=always). Every statement is idempotent.
-- ===========================================================================

CREATE TABLE IF NOT EXISTS characters (
    id                UUID         PRIMARY KEY,
    character_name    VARCHAR(255) NOT NULL,
    current_level     INT          NOT NULL DEFAULT 1,
    overall_xp        INT          NOT NULL DEFAULT 0,
    streak_count      INT          NOT NULL DEFAULT 0,
    last_workout_date DATE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    base_xp_earned     INT,
    multiplier_applied NUMERIC(5, 2),
    final_xp_awarded   INT,
    logged_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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

-- Helpful secondary indexes for FK lookups.
CREATE INDEX IF NOT EXISTS idx_character_stats_character_id ON character_stats (character_id);
CREATE INDEX IF NOT EXISTS idx_workout_logs_character_id    ON workout_logs (character_id);
