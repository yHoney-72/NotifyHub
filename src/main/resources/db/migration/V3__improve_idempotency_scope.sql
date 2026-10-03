ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS notifications_idempotency_key_key;

UPDATE notifications n
SET source_application = u.username
FROM users u
WHERE n.user_id = u.id
  AND n.source_application IS NULL;

ALTER TABLE notifications
    ALTER COLUMN source_application SET NOT NULL;

ALTER TABLE notifications
    ADD CONSTRAINT uk_notifications_idempotency
        UNIQUE (user_id, source_application, idempotency_key);