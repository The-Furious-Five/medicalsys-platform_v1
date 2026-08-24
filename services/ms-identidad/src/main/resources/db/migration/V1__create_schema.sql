-- ms-identidad · identity_db · MySQL 8
-- Modelo del Diccionario de Datos MedicalSys (seccion 2 - identity_db).
-- Los tipos ENUM del diccionario se implementan como VARCHAR + CHECK para que
-- Hibernate pueda validar el esquema sin ambiguedad de tipos JDBC.

CREATE TABLE `role` (
    id          SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300) NULL,
    scope       VARCHAR(20)  NOT NULL DEFAULT 'ORGANIZATION',
    is_system   BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_role_code UNIQUE (code),
    CONSTRAINT ck_role_code CHECK (code IN ('PLATFORM_ADMIN','ADMINISTRATOR','PHYSICIAN','RECEPTIONIST','PATIENT','BILLING_CLERK')),
    CONSTRAINT ck_role_scope CHECK (scope IN ('PLATFORM','ORGANIZATION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE permission (
    id           SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(60)  NOT NULL,
    module       VARCHAR(30)  NOT NULL,
    description  VARCHAR(300) NOT NULL,
    is_sensitive BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_permission_code UNIQUE (code),
    CONSTRAINT ck_permission_module CHECK (module IN ('IDENTITY','TENANCY','CATALOGS','CRYPTOGRAPHY','PATIENTS','PROFESSIONALS','SCHEDULING','CLINICAL','DOCUMENT_MANAGEMENT','BILLING','ADMINISTRATION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_permission_module ON permission (module);

CREATE TABLE role_permission (
    role_id       SMALLINT UNSIGNED NOT NULL,
    permission_id SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES `role` (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_role_permission_permission_id ON role_permission (permission_id);

CREATE TABLE user_account (
    id                       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                BINARY(16)        NOT NULL,
    organization_public_id   BINARY(16)        NULL,
    email                    VARCHAR(160)      NOT NULL,
    phone_e164               VARCHAR(20)       NULL,
    full_name                VARCHAR(180)      NOT NULL,
    password_hash            VARCHAR(255)      NOT NULL,
    hash_algorithm           VARCHAR(20)       NOT NULL DEFAULT 'argon2id',
    password_updated_at      TIMESTAMP         NULL DEFAULT NULL,
    mfa_enabled              BOOLEAN           NOT NULL DEFAULT FALSE,
    encrypted_mfa_secret     VARBINARY(512)    NULL,
    encryption_key_public_id BINARY(16)        NULL,
    language                 VARCHAR(5)        NOT NULL DEFAULT 'es-BO',
    status                   VARCHAR(20)       NOT NULL DEFAULT 'PENDING_ACTIVATION',
    failed_attempts          TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    locked_until             TIMESTAMP         NULL DEFAULT NULL,
    last_access_at           TIMESTAMP         NULL DEFAULT NULL,
    terms_accepted_at        TIMESTAMP         NULL DEFAULT NULL,
    data_policy_accepted_at  TIMESTAMP         NULL DEFAULT NULL,
    created_at               TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_account_public_id UNIQUE (public_id),
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT ck_user_account_status CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','LOCKED','INACTIVE')),
    CONSTRAINT ck_user_account_email CHECK (email LIKE '%_@_%._%')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_user_account_organization_public_id_status ON user_account (organization_public_id, status);
CREATE INDEX ix_user_account_phone_e164 ON user_account (phone_e164);

CREATE TABLE user_role (
    id                     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    user_account_id        BIGINT UNSIGNED   NOT NULL,
    role_id                SMALLINT UNSIGNED NOT NULL,
    organization_public_id BINARY(16)        NULL,
    branch_public_id       BINARY(16)        NULL,
    valid_from             DATE              NOT NULL,
    valid_until            DATE              NULL,
    assigned_by_user_id    BIGINT UNSIGNED   NULL,
    created_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_role_user_account_id_role_id_organization_pu_key
        UNIQUE (user_account_id, role_id, organization_public_id, branch_public_id),
    CONSTRAINT fk_user_role_user_account FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES `role` (id),
    CONSTRAINT fk_user_role_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES user_account (id),
    CONSTRAINT ck_user_role_validity CHECK (valid_until IS NULL OR valid_until >= valid_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_user_role_organization_public_id_role_id ON user_role (organization_public_id, role_id);
CREATE INDEX ix_user_role_branch_public_id ON user_role (branch_public_id);
CREATE INDEX ix_user_role_assigned_by_user_id ON user_role (assigned_by_user_id);
