ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS confirmation_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    ADD COLUMN IF NOT EXISTS idempotence_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS provider_payload VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_payments_status_created
    ON payments(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payments_provider
    ON payments(provider);
