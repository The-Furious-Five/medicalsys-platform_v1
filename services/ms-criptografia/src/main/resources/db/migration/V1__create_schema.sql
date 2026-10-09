-- =============================================================
-- ms-criptografia · cryptography_db
-- V1__create_schema.sql
-- Tablas: encryption_key, digital_certificate, key_usage
-- REGLA CRÍTICA: Nunca guardar material de clave en claro.
--               kek_reference apunta a Vault/KMS externo.
-- =============================================================

-- -------------------------------------------------------------
-- encryption_key
-- Metadatos y ciclo de vida; NUNCA la clave maestra en texto.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS encryption_key (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id             BINARY(16)      NOT NULL,
    organization_public_id BINARY(16)                  DEFAULT NULL,
    purpose               ENUM(
        'MEDICAL_RECORD','DOCUMENTS','CONTACT_DATA',
        'CREDENTIALS','BILLING','DIGITAL_SIGNATURE'
    )                                     NOT NULL,
    algorithm             ENUM(
        'AES-256-GCM','AES-256-CBC','CHACHA20-POLY1305'
    )                                     NOT NULL DEFAULT 'AES-256-GCM',
    version               SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    kek_reference         VARCHAR(300)    NOT NULL,
    wrapped_dek           VARBINARY(512)  NOT NULL,
    status                ENUM(
        'ACTIVE','AT_ROTACION','RETIRED','COMPROMISED'
    )                                     NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at            TIMESTAMP                   DEFAULT NULL,
    retired_at            TIMESTAMP                   DEFAULT NULL,

    CONSTRAINT pk_encryption_key        PRIMARY KEY (id),
    CONSTRAINT uq_encryption_key_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_encryption_key_organization_public_id_purpose_version
        UNIQUE (organization_public_id, purpose, version),
    INDEX ix_encryption_key_status      (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- digital_certificate
-- Certificados X.509 para firma y verificación.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS digital_certificate (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id             BINARY(16)      NOT NULL,
    organization_public_id BINARY(16)     NOT NULL,
    holder_user_public_id  BINARY(16)                  DEFAULT NULL,
    `usage`               ENUM(
        'CONSENT_SIGNATURE','PRESCRIPTION_SIGNATURE',
        'TAX_INVOICE_SIGNATURE','ORGANIZATION_SEAL'
    )                                     NOT NULL,
    certificate_authority VARCHAR(160)    NOT NULL,
    serial_number         VARCHAR(100)    NOT NULL,
    subject               VARCHAR(300)    NOT NULL,
    sha256_fingerprint    CHAR(64)        NOT NULL,
    valid_from            TIMESTAMP       NOT NULL,
    valid_until           TIMESTAMP       NOT NULL,
    status                ENUM(
        'ACTIVE','EXPIRING_SOON','EXPIRED','REVOKED'
    )                                     NOT NULL DEFAULT 'ACTIVE',
    encryption_key_id     BIGINT UNSIGNED             DEFAULT NULL,
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_digital_certificate   PRIMARY KEY (id),
    CONSTRAINT uq_digital_certificate_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_digital_certificate_certificate_authority_serial_number
        UNIQUE (certificate_authority, serial_number),
    CONSTRAINT fk_digital_certificate_encryption_key
        FOREIGN KEY (encryption_key_id) REFERENCES encryption_key(id),
    CONSTRAINT chk_digital_certificate_valid_until
        CHECK (valid_until > valid_from),
    INDEX ix_digital_certificate_organization_public_id_status
        (organization_public_id, status),
    INDEX ix_digital_certificate_holder_user_public_id
        (holder_user_public_id),
    INDEX ix_digital_certificate_encryption_key_id
        (encryption_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- key_usage
-- Bitácora de operaciones criptográficas (append-only).
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS key_usage (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    encryption_key_id   BIGINT UNSIGNED NOT NULL,
    operation           ENUM(
        'ENCRYPT','DECRYPT','WRAP','UNWRAP',
        'ROTATE','SIGN','VERIFY'
    )                                   NOT NULL,
    requesting_service  VARCHAR(80)     NOT NULL,
    subject_type        VARCHAR(60)     NOT NULL,
    subject_public_id   BINARY(16)                  DEFAULT NULL,
    correlation_id      BINARY(16)      NOT NULL,
    result              ENUM('SUCCESS','FAILURE')
                                        NOT NULL,
    error_code          VARCHAR(80)                 DEFAULT NULL,
    occurred_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_key_usage             PRIMARY KEY (id),
    CONSTRAINT fk_key_usage_encryption_key
        FOREIGN KEY (encryption_key_id) REFERENCES encryption_key(id),
    INDEX ix_key_usage_encryption_key_id_occurred_at
        (encryption_key_id, occurred_at),
    INDEX ix_key_usage_correlation_id   (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
