-- ms-tenencia · tenancy_db · MySQL 8
-- Modelo del Diccionario de Datos MedicalSys (seccion 3 - tenancy_db).
-- Las referencias a otros microservicios se almacenan mediante sus
-- identificadores publicos y NO llevan claves foraneas fisicas.
--
-- Los tipos ENUM del diccionario se implementan como VARCHAR + CHECK para
-- mantener los valores controlados y permitir la validacion de Hibernate.

-- --------------------------------------------------------------- subscription_plan
CREATE TABLE subscription_plan (
    id                    SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                  VARCHAR(30)       NOT NULL,
    name                  VARCHAR(80)       NOT NULL,
    monthly_price_bob     DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    max_users             SMALLINT UNSIGNED NULL,
    max_branches          SMALLINT UNSIGNED NULL,
    max_storage_gb        SMALLINT UNSIGNED NULL,
    includes_whatsapp     BOOLEAN           NOT NULL DEFAULT TRUE,
    includes_billing      BOOLEAN           NOT NULL DEFAULT TRUE,
    features              JSON              NULL,
    active                BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_subscription_plan_code
        UNIQUE (code),
    CONSTRAINT ck_subscription_plan_monthly_price_bob CHECK (
        monthly_price_bob >= 0
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------------- organization
CREATE TABLE organization (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                BINARY(16)      NOT NULL,
    code                     VARCHAR(30)     NOT NULL,
    type                     VARCHAR(30)     NOT NULL,
    legal_name               VARCHAR(180)    NOT NULL,
    trade_name               VARCHAR(180)    NOT NULL,
    nit                      VARCHAR(15)     NOT NULL,
    trade_registry_number    VARCHAR(40)     NULL,
    contact_email            VARCHAR(160)    NOT NULL,
    phone_e164               VARCHAR(20)     NULL,
    city                     VARCHAR(80)     NULL,
    department               VARCHAR(20)     NULL,
    time_zone                VARCHAR(40)     NOT NULL DEFAULT 'America/La_Paz',
    currency                 CHAR(3)         NOT NULL DEFAULT 'BOB',
    logo_url                 VARCHAR(500)    NULL,
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ONBOARDING',
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_organization_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_organization_code
        UNIQUE (code),
    CONSTRAINT uq_organization_nit
        UNIQUE (nit),
    CONSTRAINT ck_organization_type CHECK (
        type IN (
            'PRIVATE_CLINIC','PUBLIC_CLINIC','OFFICE',
            'INDEPENDENT_PHYSICIAN','DIAGNOSTIC_CENTER'
        )
    ),
    CONSTRAINT ck_organization_department CHECK (
        department IS NULL OR department IN (
            'LA_PAZ','COCHABAMBA','SANTA_CRUZ','ORURO','POTOSI',
            'CHUQUISACA','TARIJA','BENI','PANDO'
        )
    ),
    CONSTRAINT ck_organization_status CHECK (
        status IN ('ONBOARDING','ACTIVE','SUSPENDED','CANCELLED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_organization_status
    ON organization (status);

-- ------------------------------------------------------------------- subscription
CREATE TABLE subscription (
    id                       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    organization_id          BIGINT UNSIGNED   NOT NULL,
    subscription_plan_id     SMALLINT UNSIGNED NOT NULL,
    billing_cycle            VARCHAR(20)       NOT NULL DEFAULT 'MONTHLY',
    start_date               DATE              NOT NULL,
    end_date                 DATE              NULL,
    agreed_price_bob         DECIMAL(10,2)     NOT NULL,
    status                   VARCHAR(20)       NOT NULL DEFAULT 'TRIAL',
    created_at               TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_subscription_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_subscription_subscription_plan
        FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plan (id),
    CONSTRAINT ck_subscription_billing_cycle CHECK (
        billing_cycle IN ('MONTHLY','YEARLY')
    ),
    CONSTRAINT ck_subscription_status CHECK (
        status IN ('TRIAL','ACTIVE','PAST_DUE','CANCELLED')
    ),
    CONSTRAINT ck_subscription_validity CHECK (
        end_date IS NULL OR end_date >= start_date
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_subscription_organization_id_status
    ON subscription (organization_id, status);
CREATE INDEX ix_subscription_subscription_plan_id
    ON subscription (subscription_plan_id);

-- ------------------------------------------------------------------------- branch
CREATE TABLE branch (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id             BINARY(16)      NOT NULL,
    organization_id       BIGINT UNSIGNED NOT NULL,
    code                  VARCHAR(20)     NOT NULL,
    name                  VARCHAR(120)    NOT NULL,
    is_headquarters       BOOLEAN         NOT NULL DEFAULT FALSE,
    address               VARCHAR(250)    NOT NULL,
    city                  VARCHAR(80)     NOT NULL,
    department            VARCHAR(20)     NOT NULL,
    phone_e164            VARCHAR(20)     NULL,
    latitude              DECIMAL(9,6)    NULL,
    longitude             DECIMAL(9,6)    NULL,
    opening_hours         JSON            NULL,
    status                VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_branch_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_branch_organization_id_code
        UNIQUE (organization_id, code),
    CONSTRAINT fk_branch_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT ck_branch_department CHECK (
        department IN (
            'LA_PAZ','COCHABAMBA','SANTA_CRUZ','ORURO','POTOSI',
            'CHUQUISACA','TARIJA','BENI','PANDO'
        )
    ),
    CONSTRAINT ck_branch_status CHECK (
        status IN ('ACTIVE','INACTIVE')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_branch_organization_id_status
    ON branch (organization_id, status);

-- --------------------------------------------------------------- service_offering
CREATE TABLE service_offering (
    id                          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                   BINARY(16)        NOT NULL,
    organization_id             BIGINT UNSIGNED   NOT NULL,
    code                        VARCHAR(30)       NOT NULL,
    name                        VARCHAR(160)      NOT NULL,
    description                 VARCHAR(500)      NULL,
    type                        VARCHAR(30)       NOT NULL,
    specialty_code              VARCHAR(20)       NULL,
    duration_minutes            SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    base_price_bob              DECIMAL(12,2)     NOT NULL DEFAULT 0.00,
    requires_consent            BOOLEAN           NOT NULL DEFAULT FALSE,
    consent_template_public_id  BINARY(16)        NULL,
    required_resource_type      VARCHAR(30)       NOT NULL DEFAULT 'OFFICE',
    tax_product_code            VARCHAR(30)       NULL,
    tax_unit_code               VARCHAR(10)       NULL,
    active                      BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_service_offering_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_service_offering_organization_id_code
        UNIQUE (organization_id, code),
    CONSTRAINT fk_service_offering_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT ck_service_offering_type CHECK (
        type IN (
            'CONSULTATION','FOLLOW_UP','PROCEDURE','SURGERY','STUDY',
            'TELECONSULTATION','VACCINATION','OTHER'
        )
    ),
    CONSTRAINT ck_service_offering_required_resource_type CHECK (
        required_resource_type IN (
            'NONE','OFFICE','OPERATING_ROOM','PROCEDURE_ROOM','EQUIPMENT'
        )
    ),
    CONSTRAINT ck_service_offering_duration_minutes CHECK (
        duration_minutes BETWEEN 5 AND 720
    ),
    CONSTRAINT ck_service_offering_base_price_bob CHECK (
        base_price_bob >= 0
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_service_offering_organization_id_active
    ON service_offering (organization_id, active);
CREATE INDEX ix_service_offering_specialty_code
    ON service_offering (specialty_code);

-- --------------------------------------------------------------------- plan_usage
CREATE TABLE plan_usage (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id    BIGINT UNSIGNED NOT NULL,
    metric             VARCHAR(20)     NOT NULL,
    current_value      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    period_start       DATE            NOT NULL,
    period_end         DATE            NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_plan_usage_organization_id_metric_period_start
        UNIQUE (organization_id, metric, period_start),
    CONSTRAINT fk_plan_usage_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT ck_plan_usage_metric CHECK (
        metric IN ('USERS','BRANCHES','STORAGE_BYTES')
    ),
    CONSTRAINT ck_plan_usage_current_value CHECK (
        current_value >= 0
    ),
    CONSTRAINT ck_plan_usage_period CHECK (
        period_end IS NULL OR period_end >= period_start
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_plan_usage_organization_id
    ON plan_usage (organization_id);
