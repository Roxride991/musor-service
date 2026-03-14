CREATE TABLE IF NOT EXISTS order_proof_photos (
    proof_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    courier_id BIGINT NOT NULL,
    stage VARCHAR(16) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order_proof_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_order_proof_courier FOREIGN KEY (courier_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_order_proof_order_created
    ON order_proof_photos(order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_order_proof_stage
    ON order_proof_photos(stage);
