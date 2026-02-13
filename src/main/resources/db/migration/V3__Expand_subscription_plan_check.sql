ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_plan_check;

ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_plan_check
        CHECK (plan IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'));
