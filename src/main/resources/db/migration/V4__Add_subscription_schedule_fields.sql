ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS service_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS service_lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS service_lng DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_slot VARCHAR(32),
    ADD COLUMN IF NOT EXISTS next_pickup_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cadence_days INTEGER NOT NULL DEFAULT 2;

UPDATE subscriptions
SET cadence_days = 2
WHERE cadence_days IS NULL OR cadence_days <= 0;

ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_pickup_slot_check;

ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_pickup_slot_check
        CHECK (pickup_slot IN ('SLOT_8_11', 'SLOT_13_16', 'SLOT_19_21') OR pickup_slot IS NULL);
