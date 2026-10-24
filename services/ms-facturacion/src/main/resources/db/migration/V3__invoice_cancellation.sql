ALTER TABLE invoice ADD COLUMN voided_by_user_public_id BINARY(16) NULL;
ALTER TABLE invoice ADD COLUMN demo_emission BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE invoice SET demo_emission = TRUE WHERE status = 'DEMO_ACCEPTED';
