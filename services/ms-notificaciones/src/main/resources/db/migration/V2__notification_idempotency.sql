ALTER TABLE notification
    DROP INDEX uq_notification_origin_event,
    ADD COLUMN idempotency_key VARCHAR(160) NULL AFTER origin_event_id;

UPDATE notification
SET idempotency_key = CASE
    WHEN origin_event_id IS NOT NULL THEN CONCAT('manual:', origin_event_id)
    ELSE CONCAT('notification:', public_id)
END;

ALTER TABLE notification
    MODIFY COLUMN idempotency_key VARCHAR(160) NOT NULL,
    ADD UNIQUE KEY uq_notification_idempotency (idempotency_key),
    ADD KEY ix_notification_origin_event (origin_event_id);
