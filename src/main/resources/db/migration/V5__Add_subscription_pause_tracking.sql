ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS pause_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS paused_days_used INTEGER NOT NULL DEFAULT 0;

UPDATE subscriptions
SET paused_days_used = 0
WHERE paused_days_used IS NULL OR paused_days_used < 0;
