CREATE TABLE organization_reference (
    organization_id CHAR(36) NOT NULL,
    legal_name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (organization_id)
) ENGINE=InnoDB;

CREATE TABLE patient_reference (
    patient_id CHAR(36) NOT NULL,
    organization_id CHAR(36) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    whatsapp_number VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (patient_id),
    KEY ix_patient_reference_org (organization_id)
) ENGINE=InnoDB;

CREATE TABLE notification_preference_reference (
    patient_id CHAR(36) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    category VARCHAR(24) NOT NULL,
    opted_in BOOLEAN NOT NULL DEFAULT FALSE,
    opted_in_at DATETIME(6),
    opted_out_at DATETIME(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (patient_id, channel, category),
    CONSTRAINT ck_preference_channel CHECK (channel IN ('WHATSAPP','SMS','EMAIL','PUSH')),
    CONSTRAINT ck_preference_category CHECK (category IN ('UTILITY','MARKETING')),
    CONSTRAINT ck_preference_opt_in CHECK (opted_in = FALSE OR opted_in_at IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE message_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id CHAR(36),
    code VARCHAR(60) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    category VARCHAR(24) NOT NULL DEFAULT 'UTILITY',
    provider_template_name VARCHAR(120),
    language_code VARCHAR(10) NOT NULL DEFAULT 'es',
    subject VARCHAR(200),
    body TEXT NOT NULL,
    variables JSON,
    approval_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_message_template_public_id (public_id),
    UNIQUE KEY uq_message_template_scope (organization_id, code, channel, language_code),
    CONSTRAINT ck_template_channel CHECK (channel IN ('WHATSAPP','SMS','EMAIL','PUSH')),
    CONSTRAINT ck_template_category CHECK (category IN ('UTILITY','MARKETING','AUTHENTICATION','SERVICE')),
    CONSTRAINT ck_template_approval CHECK (approval_status IN ('DRAFT','IN_REVIEW','APPROVED','REJECTED','PAUSED'))
) ENGINE=InnoDB;

CREATE TABLE notification_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id CHAR(36) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    template_id BIGINT UNSIGNED NOT NULL,
    offset_minutes INT NOT NULL DEFAULT 0,
    sending_window_from TIME,
    sending_window_to TIME,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_rule_public_id (public_id),
    UNIQUE KEY uq_notification_rule (organization_id, event_type, channel, offset_minutes),
    CONSTRAINT fk_rule_template FOREIGN KEY (template_id) REFERENCES message_template (id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    origin_event_id CHAR(36),
    organization_id CHAR(36) NOT NULL,
    patient_id CHAR(36),
    destination_user_id CHAR(36),
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    category VARCHAR(24) NOT NULL DEFAULT 'UTILITY',
    template_id BIGINT UNSIGNED,
    rule_id BIGINT UNSIGNED,
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    entity_id CHAR(36),
    destination VARCHAR(160) NOT NULL,
    variables JSON,
    rendered_body TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    scheduled_for DATETIME(6) NOT NULL,
    attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    sent_at DATETIME(6),
    delivered_at DATETIME(6),
    read_at DATETIME(6),
    provider_message_id VARCHAR(160),
    error_code VARCHAR(40),
    error_detail VARCHAR(500),
    estimated_cost_usd DECIMAL(10,4),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_public_id (public_id),
    UNIQUE KEY uq_notification_origin_event (origin_event_id),
    UNIQUE KEY uq_notification_provider_id (provider_message_id),
    KEY ix_notification_queue (status, scheduled_for),
    KEY ix_notification_patient (patient_id, created_at),
    KEY ix_notification_org (organization_id, event_type, created_at),
    CONSTRAINT fk_notification_template FOREIGN KEY (template_id) REFERENCES message_template (id),
    CONSTRAINT fk_notification_rule FOREIGN KEY (rule_id) REFERENCES notification_rule (id),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('WHATSAPP','SMS','EMAIL','PUSH')),
    CONSTRAINT ck_notification_category CHECK (category IN ('UTILITY','MARKETING','AUTHENTICATION','SERVICE')),
    CONSTRAINT ck_notification_status CHECK (status IN ('SCHEDULED','QUEUED','SENT','DELIVERED','READ','FAILED','CANCELLED','REJECTED_OPT_OUT')),
    CONSTRAINT ck_notification_recipient CHECK (patient_id IS NOT NULL OR destination_user_id IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE notification_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    error_code VARCHAR(40),
    webhook_payload JSON,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_event (notification_id, status, occurred_at),
    CONSTRAINT fk_event_notification FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE CASCADE,
    CONSTRAINT ck_event_status CHECK (status IN ('ACCEPTED','SENT','DELIVERED','READ','FAILED','DELETED'))
) ENGINE=InnoDB;

CREATE TABLE incoming_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id CHAR(36),
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    source_phone VARCHAR(20) NOT NULL,
    patient_id CHAR(36),
    provider_message_id VARCHAR(160),
    notification_id BIGINT UNSIGNED,
    message_text TEXT,
    intent VARCHAR(24) NOT NULL DEFAULT 'UNRECOGNIZED',
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    performed_action VARCHAR(120),
    received_at DATETIME(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_incoming_public_id (public_id),
    UNIQUE KEY uq_incoming_provider_id (provider_message_id),
    KEY ix_incoming_pending (processed, received_at),
    CONSTRAINT fk_incoming_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT ck_incoming_intent CHECK (intent IN ('CONFIRM','CANCEL','RESCHEDULE','QUESTION','MARKETING_OPT_OUT','OTHER','UNRECOGNIZED'))
) ENGINE=InnoDB;

CREATE TABLE conversation_window (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id CHAR(36) NOT NULL,
    patient_id CHAR(36) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    destination VARCHAR(20) NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    last_patient_message_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) GENERATED ALWAYS AS (DATE_ADD(last_patient_message_at, INTERVAL 24 HOUR)) STORED,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_conversation_window (organization_id, patient_id, channel),
    KEY ix_conversation_expiration (expires_at)
) ENGINE=InnoDB;
