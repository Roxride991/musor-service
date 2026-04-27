CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(12) NOT NULL,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    telegram_id BIGINT UNIQUE,
    telegram_username VARCHAR(32),
    avatar_url VARCHAR(500),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verification_method VARCHAR(20),
    phone_verification_date TIMESTAMP,
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason VARCHAR(500),
    registration_ip VARCHAR(45),
    last_login TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT users_role_check CHECK (role IN ('CLIENT', 'COURIER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS service_zones (
    zone_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    coordinates JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS subscriptions (
    subscription_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan VARCHAR(32) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    service_address VARCHAR(255),
    service_lat DOUBLE PRECISION,
    service_lng DOUBLE PRECISION,
    pickup_slot VARCHAR(32),
    next_pickup_at TIMESTAMPTZ,
    pause_started_at TIMESTAMPTZ,
    paused_days_used INTEGER NOT NULL DEFAULT 0,
    cadence_days INTEGER NOT NULL DEFAULT 2,
    price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_allowed_orders INTEGER NOT NULL,
    used_orders INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT subscriptions_plan_check CHECK (plan IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT subscriptions_status_check CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT subscriptions_pickup_slot_check CHECK (pickup_slot IN ('SLOT_8_11', 'SLOT_13_16', 'SLOT_19_21') OR pickup_slot IS NULL),
    CONSTRAINT subscriptions_positive_totals_check CHECK (total_allowed_orders >= 0 AND used_orders >= 0),
    CONSTRAINT subscriptions_positive_cadence_check CHECK (cadence_days > 0)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    courier_id BIGINT,
    subscription_id BIGINT,
    address VARCHAR(255) NOT NULL,
    pickup_time TIMESTAMPTZ NOT NULL,
    comment TEXT,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_orders_client FOREIGN KEY (client_id) REFERENCES users (user_id),
    CONSTRAINT fk_orders_courier FOREIGN KEY (courier_id) REFERENCES users (user_id),
    CONSTRAINT fk_orders_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (subscription_id),
    CONSTRAINT orders_status_check CHECK (
        status IN (
            'PUBLISHED',
            'ACCEPTED',
            'ON_THE_WAY',
            'PICKED_UP',
            'COMPLETED',
            'CANCELLED_BY_CUSTOMER',
            'CANCELLED_BY_COURIER'
        )
    )
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    external_id VARCHAR(100) UNIQUE,
    provider VARCHAR(32),
    confirmation_url VARCHAR(500),
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    idempotence_key VARCHAR(64),
    provider_payload VARCHAR(4000),
    order_id BIGINT UNIQUE,
    subscription_id BIGINT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (subscription_id),
    CONSTRAINT payments_type_check CHECK (type IN ('ONE_TIME', 'SUBSCRIPTION')),
    CONSTRAINT payments_status_check CHECK (status IN ('PENDING', 'SUCCEEDED', 'CANCELED', 'FAILED')),
    CONSTRAINT payments_amount_positive_check CHECK (amount > 0),
    CONSTRAINT payments_owner_check CHECK (
        (order_id IS NOT NULL AND subscription_id IS NULL)
        OR (order_id IS NULL AND subscription_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON subscriptions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_client_status ON orders(client_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_courier_status ON orders(courier_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_pickup_time ON orders(pickup_time);
CREATE INDEX IF NOT EXISTS idx_payments_status_created_at ON payments(status, created_at DESC);
