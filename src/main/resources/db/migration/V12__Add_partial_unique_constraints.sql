-- Keep only one active/paused subscription per user before unique index creation.
WITH ranked_subscriptions AS (
    SELECT subscription_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY
                   CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                   created_at DESC,
                   subscription_id DESC
           ) AS rn
    FROM subscriptions
    WHERE status IN ('ACTIVE', 'PAUSED')
)
UPDATE subscriptions s
SET status = 'CANCELED'
FROM ranked_subscriptions r
WHERE s.subscription_id = r.subscription_id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_user_active_or_paused
    ON subscriptions (user_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

-- Keep only one active zone before unique index creation.
WITH ranked_zones AS (
    SELECT zone_id,
           ROW_NUMBER() OVER (
               ORDER BY created_at DESC, zone_id DESC
           ) AS rn
    FROM service_zones
    WHERE active = true
)
UPDATE service_zones z
SET active = false
FROM ranked_zones r
WHERE z.zone_id = r.zone_id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_service_zones_single_active
    ON service_zones (active)
    WHERE active = true;
