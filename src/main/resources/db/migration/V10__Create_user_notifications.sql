CREATE TABLE IF NOT EXISTS user_notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    order_id BIGINT,
    subscription_id BIGINT,
    dedupe_key VARCHAR(128),
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    error_message VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_created
    ON user_notifications(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_queue
    ON user_notifications(status, scheduled_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notifications_dedupe_key
    ON user_notifications(dedupe_key)
    WHERE dedupe_key IS NOT NULL;
