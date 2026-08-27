-- ms-pacientes · patients_db · MySQL 8
-- Modelo del Diccionario de Datos MedicalSys (seccion 6 - patients_db).
-- Las referencias a otros microservicios se almacenan como UUID publicos en
-- BINARY(16) y NO llevan claves foraneas fisicas.
--
-- Los tipos ENUM del diccionario se implementan como VARCHAR + CHECK para
-- mantener los valores controlados y permitir la validacion del esquema por
-- Hibernate sin ambiguedades de tipos JDBC.

-- ------------------------------------------------------------------------- patient
CREATE TABLE patient (
    id                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                      BINARY(16)      NOT NULL,
    user_account_public_id         BINARY(16)      NULL,
    document_type                  VARCHAR(30)     NOT NULL DEFAULT 'CI',
    document_number                VARCHAR(20)     NOT NULL,
    document_complement            VARCHAR(5)      NOT NULL DEFAULT '',
    issued_department              VARCHAR(20)     NULL,
    given_names                    VARCHAR(90)     NOT NULL,
    paternal_surname               VARCHAR(60)     NOT NULL,
    maternal_surname               VARCHAR(60)     NULL,
    full_name                      VARCHAR(290)
        GENERATED ALWAYS AS (
            CONCAT_WS(' ', paternal_surname, maternal_surname, given_names)
        ) STORED,
    birth_date                     DATE            NOT NULL,
    sex                            VARCHAR(20)     NOT NULL DEFAULT 'UNDECLARED',
    marital_status                 VARCHAR(30)     NOT NULL DEFAULT 'UNDECLARED',
    blood_type                     VARCHAR(10)     NOT NULL DEFAULT 'UNKNOWN',
    occupation                     VARCHAR(120)    NULL,
    phone_e164                     VARCHAR(20)     NULL,
    alternate_phone                VARCHAR(20)     NULL,
    email                          VARCHAR(100)    NULL,
    encrypted_address              VARBINARY(1024) NULL,
    city                           VARCHAR(80)     NULL,
    department                     VARCHAR(20)     NULL,
    emergency_contact_name         VARCHAR(160)    NULL,
    emergency_contact_phone        VARCHAR(20)     NULL,
    emergency_contact_relationship VARCHAR(60)     NULL,
    guardian_patient_id            BIGINT UNSIGNED NULL,
    encryption_key_public_id       BINARY(16)      NULL,
    data_consent_at                TIMESTAMP       NULL DEFAULT NULL,
    status                         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    death_date                     DATE            NULL,
    created_at                     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_patient_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_patient_document_type_document_number_document_complement
        UNIQUE (document_type, document_number, document_complement),
    CONSTRAINT uq_patient_user_account_public_id
        UNIQUE (user_account_public_id),
    CONSTRAINT fk_patient_guardian_patient
        FOREIGN KEY (guardian_patient_id) REFERENCES patient (id),
    CONSTRAINT ck_patient_document_type CHECK (
        document_type IN ('CI','FOREIGN_ID_CARD','PASSPORT','BIRTH_CERTIFICATE','NO_DOCUMENT')
    ),
    CONSTRAINT ck_patient_issued_department CHECK (
        issued_department IS NULL OR issued_department IN (
            'LA_PAZ','COCHABAMBA','SANTA_CRUZ','ORURO','POTOSI',
            'CHUQUISACA','TARIJA','BENI','PANDO','FOREIGN'
        )
    ),
    CONSTRAINT ck_patient_sex CHECK (
        sex IN ('M','F','INTERSEX','UNDECLARED')
    ),
    CONSTRAINT ck_patient_marital_status CHECK (
        marital_status IN (
            'SINGLE','MARRIED','DIVORCED','WIDOWED',
            'DOMESTIC_PARTNERSHIP','UNDECLARED'
        )
    ),
    CONSTRAINT ck_patient_blood_type CHECK (
        blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-','UNKNOWN')
    ),
    CONSTRAINT ck_patient_department CHECK (
        department IS NULL OR department IN (
            'LA_PAZ','COCHABAMBA','SANTA_CRUZ','ORURO','POTOSI',
            'CHUQUISACA','TARIJA','BENI','PANDO','FOREIGN'
        )
    ),
    CONSTRAINT ck_patient_status CHECK (
        status IN ('ACTIVE','INACTIVE','DECEASED')
    ),
    CONSTRAINT ck_patient_status_death_date CHECK (
        (status <> 'DECEASED' AND death_date IS NULL)
        OR (status = 'DECEASED' AND death_date IS NOT NULL)
    ),
    CONSTRAINT ck_patient_birth_date CHECK (
        birth_date >= '1900-01-01'
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_patient_full_name
    ON patient (full_name);
CREATE INDEX ix_patient_phone_e164
    ON patient (phone_e164);
CREATE INDEX ix_patient_guardian_patient_id
    ON patient (guardian_patient_id);
CREATE INDEX ix_patient_encryption_key_public_id
    ON patient (encryption_key_public_id);

-- --------------------------------------------------------- notification_preference
CREATE TABLE notification_preference (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    patient_id     BIGINT UNSIGNED NOT NULL,
    channel        VARCHAR(20)     NOT NULL,
    -- El PDF indica UTILITY como default, pero el dominio final solo permite
    -- CLINICAL y ADMINISTRATIVE. Se usa ADMINISTRATIVE para que el default
    -- pertenezca al conjunto de valores controlados del modelo MVP.
    category       VARCHAR(20)     NOT NULL DEFAULT 'ADMINISTRATIVE',
    opt_in         BOOLEAN         NOT NULL DEFAULT FALSE,
    consent_source VARCHAR(30)     NULL,
    opt_in_at      TIMESTAMP       NULL DEFAULT NULL,
    opt_out_at     TIMESTAMP       NULL DEFAULT NULL,
    updated_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_preference_patient_id_channel_category
        UNIQUE (patient_id, channel, category),
    CONSTRAINT fk_notification_preference_patient
        FOREIGN KEY (patient_id) REFERENCES patient (id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_preference_channel CHECK (
        channel IN ('WHATSAPP','SMS','EMAIL','PUSH')
    ),
    CONSTRAINT ck_notification_preference_category CHECK (
        category IN ('CLINICAL','ADMINISTRATIVE')
    ),
    CONSTRAINT ck_notification_preference_consent_source CHECK (
        consent_source IS NULL OR consent_source IN (
            'WEB_FORM','RECEPTION','WHATSAPP_RESPONSE','PHYSICAL_CONTRACT','IMPORT'
        )
    ),
    CONSTRAINT ck_notification_preference_opt_in_at CHECK (
        opt_in = FALSE OR opt_in_at IS NOT NULL
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------------- patient_merge
CREATE TABLE patient_merge (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    surviving_patient_id     BIGINT UNSIGNED NOT NULL,
    merged_patient_id        BIGINT UNSIGNED NOT NULL,
    reason                   VARCHAR(500)    NOT NULL,
    merged_by_user_public_id BINARY(16)      NOT NULL,
    merged_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_public_id          BINARY(16)      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_patient_merge_merged_patient_id
        UNIQUE (merged_patient_id),
    CONSTRAINT uq_patient_merge_event_public_id
        UNIQUE (event_public_id),
    CONSTRAINT fk_patient_merge_surviving_patient
        FOREIGN KEY (surviving_patient_id) REFERENCES patient (id),
    CONSTRAINT fk_patient_merge_merged_patient
        FOREIGN KEY (merged_patient_id) REFERENCES patient (id),
    CONSTRAINT ck_patient_merge_distinct_patients CHECK (
        surviving_patient_id <> merged_patient_id
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_patient_merge_surviving_patient_id
    ON patient_merge (surviving_patient_id);
