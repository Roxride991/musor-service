CREATE INDEX IF NOT EXISTS idx_audit_events_target
    ON audit_events(target_type, target_id, created_at DESC);
