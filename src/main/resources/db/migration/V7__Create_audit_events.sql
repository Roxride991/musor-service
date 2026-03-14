CREATE TABLE IF NOT EXISTS audit_events (
    audit_id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    actor_user_id BIGINT,
    actor_role VARCHAR(32),
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    client_ip VARCHAR(64),
    details VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_created_at
    ON audit_events(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_event_type
    ON audit_events(event_type);

CREATE INDEX IF NOT EXISTS idx_audit_events_actor_user_id
    ON audit_events(actor_user_id);
