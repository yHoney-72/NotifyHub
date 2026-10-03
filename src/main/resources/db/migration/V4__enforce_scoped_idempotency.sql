ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_idempotency_key_key;

DROP INDEX IF EXISTS notifications_idempotency_key_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_notifications_idempotency'
          AND conrelid = 'notifications'::regclass
    ) THEN
        ALTER TABLE notifications
            ADD CONSTRAINT uk_notifications_idempotency
                UNIQUE (user_id, source_application, idempotency_key);
    END IF;
END
$$;
