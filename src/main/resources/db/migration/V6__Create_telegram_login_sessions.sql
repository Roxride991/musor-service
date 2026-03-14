CREATE TABLE IF NOT EXISTS telegram_login_sessions (
    session_id VARCHAR(96) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    telegram_user_id BIGINT,
    phone VARCHAR(12),
    user_id BIGINT,
    bot_timestamp BIGINT,
    last_error VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tg_login_sessions_status
    ON telegram_login_sessions(status);

CREATE INDEX IF NOT EXISTS idx_tg_login_sessions_expires_at
    ON telegram_login_sessions(expires_at);

CREATE INDEX IF NOT EXISTS idx_tg_login_sessions_user_id
    ON telegram_login_sessions(user_id);
