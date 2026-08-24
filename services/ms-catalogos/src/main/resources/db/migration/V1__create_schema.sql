-- =============================================================
-- ms-catalogos · catalogs_db
-- V1__create_schema.sql
-- Tablas: specialty, icd10_catalog, medication,
--         study_type, tax_catalog,
--         medication_active_ingredient, catalog_sync
-- Convención: snake_case inglés, sin FK entre bases.
-- =============================================================

-- -------------------------------------------------------------
-- specialty
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS specialty (
    id        SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(20)       NOT NULL,
    name      VARCHAR(120)      NOT NULL,
    description VARCHAR(300)                 DEFAULT NULL,
    active    BOOLEAN           NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_specialty            PRIMARY KEY (id),
    CONSTRAINT uq_specialty_code       UNIQUE      (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- icd10_catalog  (PK = code, sin surrogate)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS icd10_catalog (
    code            VARCHAR(10)  NOT NULL,
    description     VARCHAR(300) NOT NULL,
    chapter         VARCHAR(120)              DEFAULT NULL,
    category        VARCHAR(120)              DEFAULT NULL,
    applicable_sex  ENUM('BOTH','M','F')
                                 NOT NULL DEFAULT 'BOTH',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_icd10_catalog        PRIMARY KEY (code),
    INDEX ix_icd10_catalog_description (description(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- medication
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medication (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                  VARCHAR(30)     NOT NULL,
    generic_name          VARCHAR(160)    NOT NULL,
    trade_name            VARCHAR(160)                DEFAULT NULL,
    concentration         VARCHAR(60)                 DEFAULT NULL,
    pharmaceutical_form   ENUM(
        'TABLET','CAPSULE','SYRUP','SUSPENSION','INJECTABLE',
        'CREAM','OINTMENT','DROPS','SUPPOSITORY','PATCH',
        'AEROSOL','OTHER'
    )                                    NOT NULL DEFAULT 'TABLET',
    administration_route  ENUM(
        'ORAL','INTRAVENOUS','INTRAMUSCULAR','SUBCUTANEOUS',
        'TOPICAL','OPHTHALMIC','OTIC','NASAL','RECTAL',
        'INHALATION','OTHER'
    )                                    NOT NULL DEFAULT 'ORAL',
    is_controlled         BOOLEAN         NOT NULL DEFAULT FALSE,
    requires_prescription BOOLEAN         NOT NULL DEFAULT TRUE,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_medication            PRIMARY KEY (id),
    CONSTRAINT uq_medication_code       UNIQUE      (code),
    INDEX ix_medication_generic_name    (generic_name(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- medication_active_ingredient
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medication_active_ingredient (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    medication_id           BIGINT UNSIGNED NOT NULL,
    active_ingredient_code  VARCHAR(40)     NOT NULL,
    active_ingredient_name  VARCHAR(160)    NOT NULL,
    quantity                DECIMAL(12,4)               DEFAULT NULL,
    unit                    VARCHAR(30)                 DEFAULT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_medication_active_ingredient
        PRIMARY KEY (id),
    CONSTRAINT uq_medication_active_ingredient_medication_id_active_key
        UNIQUE (medication_id, active_ingredient_code),
    CONSTRAINT fk_medication_active_ingredient_medication
        FOREIGN KEY (medication_id) REFERENCES medication(id)
        ON DELETE CASCADE,
    INDEX ix_medication_active_ingredient_active_ingredient_name
        (active_ingredient_name(100)),
    CONSTRAINT chk_medication_active_ingredient_quantity
        CHECK (quantity IS NULL OR quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- study_type
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_type (
    id               SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code             VARCHAR(30)       NOT NULL,
    name             VARCHAR(160)      NOT NULL,
    category         ENUM(
        'LABORATORY','IMAGING','PATHOLOGY',
        'CARDIOLOGY','ENDOSCOPY','OTHER'
    )                                  NOT NULL,
    requires_fasting BOOLEAN           NOT NULL DEFAULT FALSE,
    generates_image  BOOLEAN           NOT NULL DEFAULT FALSE,
    result_unit      VARCHAR(30)                    DEFAULT NULL,
    reference_min    DECIMAL(12,4)                  DEFAULT NULL,
    reference_max    DECIMAL(12,4)                  DEFAULT NULL,
    active           BOOLEAN           NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_study_type           PRIMARY KEY (id),
    CONSTRAINT uq_study_type_code      UNIQUE      (code),
    INDEX ix_study_type_category       (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- tax_catalog
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_catalog (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    catalog_type ENUM(
        'IDENTITY_DOCUMENT_TYPE','PAYMENT_METHOD_TYPE',
        'UNIT_OF_MEASURE','ECONOMIC_ACTIVITY',
        'PRODUCT_SERVICE','INVOICE_CAPTION',
        'CANCELLATION_REASON','SIGNIFICANT_EVENT_TYPE',
        'TAX_DOCUMENT_TYPE','EMISSION_TYPE',
        'POINT_OF_SALE_TYPE'
    )                            NOT NULL,
    code         VARCHAR(30)     NOT NULL,
    description  VARCHAR(500)    NOT NULL,
    parent_code  VARCHAR(30)                 DEFAULT NULL,
    active       BOOLEAN         NOT NULL DEFAULT TRUE,
    synced_at    TIMESTAMP                   DEFAULT NULL,

    CONSTRAINT pk_tax_catalog           PRIMARY KEY (id),
    CONSTRAINT uq_tax_catalog_catalog_type_code
        UNIQUE (catalog_type, code),
    INDEX ix_tax_catalog_catalog_type_active
        (catalog_type, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- catalog_sync
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog_sync (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    catalog_type      VARCHAR(50)     NOT NULL,
    source            VARCHAR(120)    NOT NULL,
    source_version    VARCHAR(80)     NOT NULL,
    started_at        TIMESTAMP       NOT NULL,
    finished_at       TIMESTAMP                   DEFAULT NULL,
    status            ENUM('IN_PROGRESS','SUCCESSFUL','FAILED')
                                      NOT NULL DEFAULT 'IN_PROGRESS',
    processed_records INT UNSIGNED    NOT NULL DEFAULT 0,
    sha256_hash       CHAR(64)                    DEFAULT NULL,
    error_detail      VARCHAR(1000)               DEFAULT NULL,

    CONSTRAINT pk_catalog_sync        PRIMARY KEY (id),
    INDEX ix_catalog_sync_catalog_type_started_at
        (catalog_type, started_at),
    CONSTRAINT chk_catalog_sync_processed_records
        CHECK (processed_records >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
