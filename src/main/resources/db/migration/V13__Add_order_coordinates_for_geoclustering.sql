ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION;

UPDATE orders o
SET lat = s.service_lat,
    lng = s.service_lng
FROM subscriptions s
WHERE o.subscription_id = s.subscription_id
  AND (o.lat IS NULL OR o.lng IS NULL)
  AND s.service_lat IS NOT NULL
  AND s.service_lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_status_pickup_lat_lng
    ON orders(status, pickup_time, lat, lng);
