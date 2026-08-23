-- =====================================================================================
-- MedicalSys - ms-historial - medical_records_db (schema inicial)
-- Fuente normativa: MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 9.
--
-- Reglas aplicadas (capitulo 13 del diccionario):
--   * Identificadores fisicos en ingles y snake_case.
--   * Solo FK locales. Los identificadores de otros servicios son UUID sin FK y llevan
--     indice propio para busquedas y joins en memoria.
--   * UUID publico en BINARY(16); el id numerico es clave tecnica local.
--   * Instantes en UTC; los campos cifrados guardan ciphertext de ms-criptografia.
--
-- Decision de implementacion registrada (guia Sprint 2, seccion 3.3):
--   La fila "encrypted / cifrada" de clinical_encounter aparece en el diccionario con el
--   tipo fisico "A", que no es un tipo MySQL valido. Se conserva el nombre normativo y se
--   implementa como VARBINARY(8192) NULL, coherente con las demas narrativas cifradas del
--   mismo encuentro (motivo de consulta cifrado).
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- medical_record - expediente_clinico
-- -------------------------------------------------------------------------------------
CREATE TABLE medical_record (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                 BINARY(16)      NOT NULL,
    organization_public_id    BINARY(16)      NOT NULL,
    patient_public_id         BINARY(16)      NOT NULL,
    record_number             VARCHAR(30)     NOT NULL,
    opened_on                 DATE            NOT NULL DEFAULT (CURRENT_DATE),
    opening_branch_public_id  BINARY(16)      NULL,
    encryption_key_public_id  BINARY(16)      NOT NULL,
    shares_with_network       BOOLEAN         NOT NULL DEFAULT FALSE,
    sharing_authorized_at     TIMESTAMP       NULL,
    status                    ENUM('ACTIVE', 'ARCHIVED', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    retention_until           DATE            NULL,
    created_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_medical_record PRIMARY KEY (id),
    CONSTRAINT uq_medical_record_public_id UNIQUE (public_id),
    CONSTRAINT uq_medical_record_organization_public_id_record_number
        UNIQUE (organization_public_id, record_number),
    CONSTRAINT uq_medical_record_organization_public_id_patient_public_id
        UNIQUE (organization_public_id, patient_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_medical_record_patient_public_id        ON medical_record (patient_public_id);
CREATE INDEX ix_medical_record_opening_branch_public_id ON medical_record (opening_branch_public_id);
CREATE INDEX ix_medical_record_encryption_key_public_id ON medical_record (encryption_key_public_id);

-- -------------------------------------------------------------------------------------
-- allergy - alergia
-- -------------------------------------------------------------------------------------
CREATE TABLE allergy (
    id                                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    medical_record_id                  BIGINT UNSIGNED NOT NULL,
    type                               ENUM('MEDICATION', 'FOOD', 'ENVIRONMENTAL', 'LATEX', 'STING', 'OTHER') NOT NULL,
    substance                          VARCHAR(160)    NOT NULL,
    medication_code                    VARCHAR(30)     NULL,
    reaction                           VARCHAR(300)    NULL,
    severity                           ENUM('MILD', 'MODERATE', 'SEVERE', 'ANAPHYLAXIS') NOT NULL DEFAULT 'MILD',
    status                             ENUM('ACTIVE', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'ACTIVE',
    recorded_by_professional_public_id BINARY(16)      NULL,
    recorded_at                        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_allergy PRIMARY KEY (id),
    CONSTRAINT uq_allergy_medical_record_id_type_substance
        UNIQUE (medical_record_id, type, substance),
    CONSTRAINT fk_allergy_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_record (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_allergy_medication_code                    ON allergy (medication_code);
CREATE INDEX ix_allergy_recorded_by_professional_public_id ON allergy (recorded_by_professional_public_id);

-- -------------------------------------------------------------------------------------
-- medical_background - antecedente
-- -------------------------------------------------------------------------------------
CREATE TABLE medical_background (
    id                                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    medical_record_id                  BIGINT UNSIGNED NOT NULL,
    type                               ENUM('PATHOLOGICAL', 'FAMILY', 'SURGICAL', 'TRAUMATIC', 'HABIT',
                                            'OBSTETRIC_GYNECOLOGICAL', 'IMMUNIZATION', 'TRANSFUSIONAL', 'OTHER') NOT NULL,
    title                              VARCHAR(160)    NOT NULL,
    encrypted_detail                   VARBINARY(4096) NULL,
    icd10_code                         VARCHAR(10)     NULL,
    event_date                         DATE            NULL,
    active                             BOOLEAN         NOT NULL DEFAULT TRUE,
    recorded_by_professional_public_id BINARY(16)      NULL,
    recorded_at                        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_medical_background PRIMARY KEY (id),
    CONSTRAINT fk_medical_background_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_record (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_medical_background_medical_record_id_type            ON medical_background (medical_record_id, type);
CREATE INDEX ix_medical_background_icd10_code                        ON medical_background (icd10_code);
CREATE INDEX ix_medical_background_recorded_by_professional_public_id ON medical_background (recorded_by_professional_public_id);

-- -------------------------------------------------------------------------------------
-- clinical_encounter - encuentro_clinico
-- -------------------------------------------------------------------------------------
CREATE TABLE clinical_encounter (
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                     BINARY(16)      NOT NULL,
    organization_public_id        BINARY(16)      NOT NULL,
    medical_record_id             BIGINT UNSIGNED NOT NULL,
    appointment_public_id         BINARY(16)      NULL,
    health_professional_public_id BINARY(16)      NOT NULL,
    branch_public_id              BINARY(16)      NOT NULL,
    type                          ENUM('INITIAL_CONSULTATION', 'FOLLOW_UP_CONSULTATION', 'EMERGENCY',
                                       'TELECONSULTATION', 'PROCEDURE', 'SURGERY', 'CONSULTATION_REFERRAL')
                                  NOT NULL DEFAULT 'INITIAL_CONSULTATION',
    starts_at                     TIMESTAMP       NOT NULL,
    ends_at                       TIMESTAMP       NULL,
    encrypted                     VARBINARY(8192) NULL,
    encrypted_subjective_notes    VARBINARY(8192) NULL,
    encrypted_objective_notes     VARBINARY(8192) NULL,
    encrypted_assessment          VARBINARY(8192) NULL,
    encrypted_plan                VARBINARY(8192) NULL,
    encryption_key_public_id      BINARY(16)      NOT NULL,
    suggested_next_appointment_at DATE            NULL,
    status                        ENUM('DRAFT', 'SIGNED', 'VOIDED') NOT NULL DEFAULT 'DRAFT',
    content_hash                  CHAR(64)        NULL,
    signed_at                     TIMESTAMP       NULL,
    digital_signature             VARBINARY(2048) NULL,
    digital_certificate_public_id BINARY(16)      NULL,
    voided_at                     TIMESTAMP       NULL,
    voided_by_user_public_id      BINARY(16)      NULL,
    cancellation_reason           VARCHAR(300)    NULL,
    created_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_clinical_encounter PRIMARY KEY (id),
    CONSTRAINT uq_clinical_encounter_public_id UNIQUE (public_id),
    CONSTRAINT uq_clinical_encounter_appointment_public_id UNIQUE (appointment_public_id),
    CONSTRAINT fk_clinical_encounter_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_record (id),
    CONSTRAINT ck_clinical_encounter_period
        CHECK (ends_at IS NULL OR ends_at >= starts_at),
    CONSTRAINT ck_clinical_encounter_signature
        CHECK (status <> 'SIGNED' OR (signed_at IS NOT NULL AND content_hash IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_clinical_encounter_medical_record_id_starts_at ON clinical_encounter (medical_record_id, starts_at);
CREATE INDEX ix_clinical_encounter_health_professional_public_id_key ON clinical_encounter (health_professional_public_id, starts_at);
CREATE INDEX ix_clinical_encounter_organization_public_id_status_key ON clinical_encounter (organization_public_id, status, starts_at);
CREATE INDEX ix_clinical_encounter_branch_public_id              ON clinical_encounter (branch_public_id);
CREATE INDEX ix_clinical_encounter_encryption_key_public_id      ON clinical_encounter (encryption_key_public_id);
CREATE INDEX ix_clinical_encounter_digital_certificate_public_id ON clinical_encounter (digital_certificate_public_id);
CREATE INDEX ix_clinical_encounter_voided_by_user_public_id      ON clinical_encounter (voided_by_user_public_id);

-- -------------------------------------------------------------------------------------
-- encounter_addendum - encuentro_addendum
-- -------------------------------------------------------------------------------------
CREATE TABLE encounter_addendum (
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    clinical_encounter_id         BIGINT UNSIGNED NOT NULL,
    health_professional_public_id BINARY(16)      NOT NULL,
    reason                        ENUM('CORRECTION', 'ADDITION', 'LATE_RESULT', 'OTHER') NOT NULL,
    encrypted_text                VARBINARY(8192) NOT NULL,
    encryption_key_public_id      BINARY(16)      NOT NULL,
    content_hash                  CHAR(64)        NOT NULL,
    created_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_encounter_addendum PRIMARY KEY (id),
    CONSTRAINT fk_encounter_addendum_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_encounter_addendum_clinical_encounter_id_created_at ON encounter_addendum (clinical_encounter_id, created_at);
CREATE INDEX ix_encounter_addendum_health_professional_public_id    ON encounter_addendum (health_professional_public_id);
CREATE INDEX ix_encounter_addendum_encryption_key_public_id         ON encounter_addendum (encryption_key_public_id);

-- -------------------------------------------------------------------------------------
-- vital_sign - signo_vital
-- -------------------------------------------------------------------------------------
CREATE TABLE vital_sign (
    id                         BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    clinical_encounter_id      BIGINT UNSIGNED   NOT NULL,
    measured_at                TIMESTAMP         NOT NULL,
    systolic_pressure          SMALLINT UNSIGNED NULL,
    diastolic_pressure         SMALLINT UNSIGNED NULL,
    heart_rate                 SMALLINT UNSIGNED NULL,
    respiratory_rate           SMALLINT UNSIGNED NULL,
    temperature_c              DECIMAL(4, 1)     NULL,
    oxygen_saturation          TINYINT UNSIGNED  NULL,
    blood_glucose_mg_dl        SMALLINT UNSIGNED NULL,
    weight_kg                  DECIMAL(6, 2)     NULL,
    height_cm                  DECIMAL(5, 1)     NULL,
    bmi                        DECIMAL(5, 2)     GENERATED ALWAYS AS
                                   (ROUND(weight_kg / POW(NULLIF(height_cm, 0) / 100, 2), 2)) STORED,
    abdominal_circumference_cm DECIMAL(5, 1)     NULL,
    pain_scale                 TINYINT UNSIGNED  NULL,
    recorded_by_user_public_id BINARY(16)        NULL,
    CONSTRAINT pk_vital_sign PRIMARY KEY (id),
    CONSTRAINT fk_vital_sign_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id),
    CONSTRAINT ck_vital_sign_pressure
        CHECK (systolic_pressure IS NULL OR diastolic_pressure IS NULL OR systolic_pressure >= diastolic_pressure),
    CONSTRAINT ck_vital_sign_temperature
        CHECK (temperature_c IS NULL OR temperature_c BETWEEN 25.0 AND 45.0),
    CONSTRAINT ck_vital_sign_oxygen_saturation
        CHECK (oxygen_saturation IS NULL OR oxygen_saturation BETWEEN 30 AND 100),
    CONSTRAINT ck_vital_sign_pain_scale
        CHECK (pain_scale IS NULL OR pain_scale BETWEEN 0 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_vital_sign_clinical_encounter_id_measured_at ON vital_sign (clinical_encounter_id, measured_at);
CREATE INDEX ix_vital_sign_recorded_by_user_public_id        ON vital_sign (recorded_by_user_public_id);

-- -------------------------------------------------------------------------------------
-- diagnosis - diagnostico
-- -------------------------------------------------------------------------------------
CREATE TABLE diagnosis (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    clinical_encounter_id BIGINT UNSIGNED NOT NULL,
    icd10_code            VARCHAR(10)     NOT NULL,
    type                  ENUM('PROVISIONAL', 'FINAL', 'DISMISSED') NOT NULL DEFAULT 'PROVISIONAL',
    is_primary            BOOLEAN         NOT NULL DEFAULT FALSE,
    is_chronic            BOOLEAN         NOT NULL DEFAULT FALSE,
    laterality            ENUM('NOT_APPLICABLE', 'LEFT', 'RIGHT', 'BILATERAL') NOT NULL DEFAULT 'NOT_APPLICABLE',
    encrypted_observation VARBINARY(2048) NULL,
    status                ENUM('ACTIVE', 'AT_REMISSION', 'RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_diagnosis PRIMARY KEY (id),
    CONSTRAINT uq_diagnosis_clinical_encounter_id_icd10_code
        UNIQUE (clinical_encounter_id, icd10_code),
    CONSTRAINT fk_diagnosis_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_diagnosis_icd10_code_is_chronic ON diagnosis (icd10_code, is_chronic);

-- -------------------------------------------------------------------------------------
-- prescription - receta
-- -------------------------------------------------------------------------------------
CREATE TABLE prescription (
    id                             BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                      BINARY(16)        NOT NULL,
    organization_public_id         BINARY(16)        NOT NULL,
    clinical_encounter_id          BIGINT UNSIGNED   NOT NULL,
    folio                          VARCHAR(30)       NOT NULL,
    issued_at                      TIMESTAMP         NOT NULL,
    validity_days                  SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    encrypted_general_instructions VARBINARY(4096)   NULL,
    status                         ENUM('ISSUED', 'PARTIALLY_DISPENSED', 'DISPENSED', 'EXPIRED', 'VOIDED')
                                   NOT NULL DEFAULT 'ISSUED',
    pdf_document_public_id         BINARY(16)        NULL,
    sha256_hash                    CHAR(64)          NULL,
    signed_at                      TIMESTAMP         NULL,
    digital_certificate_public_id  BINARY(16)        NULL,
    created_at                     TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_prescription PRIMARY KEY (id),
    CONSTRAINT uq_prescription_public_id UNIQUE (public_id),
    CONSTRAINT uq_prescription_organization_public_id_folio UNIQUE (organization_public_id, folio),
    CONSTRAINT fk_prescription_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_prescription_clinical_encounter_id         ON prescription (clinical_encounter_id);
CREATE INDEX ix_prescription_pdf_document_public_id        ON prescription (pdf_document_public_id);
CREATE INDEX ix_prescription_digital_certificate_public_id ON prescription (digital_certificate_public_id);

-- -------------------------------------------------------------------------------------
-- prescription_item - receta_detalle
-- -------------------------------------------------------------------------------------
CREATE TABLE prescription_item (
    id                     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    prescription_id        BIGINT UNSIGNED   NOT NULL,
    item_number            TINYINT UNSIGNED  NOT NULL,
    medication_code        VARCHAR(30)       NULL,
    free_text_description  VARCHAR(200)      NULL,
    dose                   VARCHAR(80)       NOT NULL,
    administration_route   ENUM('ORAL', 'INTRAVENOUS', 'INTRAMUSCULAR', 'SUBCUTANEOUS', 'TOPICAL',
                                'OPHTHALMIC', 'OTIC', 'NASAL', 'RECTAL', 'INHALATION', 'OTHER')
                           NOT NULL DEFAULT 'ORAL',
    frequency_hours        SMALLINT UNSIGNED NULL,
    duration_days          SMALLINT UNSIGNED NULL,
    quantity               DECIMAL(8, 2)     NOT NULL DEFAULT 1,
    unit                   VARCHAR(20)       NULL,
    encrypted_instructions VARBINARY(2048)   NULL,
    CONSTRAINT pk_prescription_item PRIMARY KEY (id),
    CONSTRAINT uq_prescription_item_prescription_id_item_number
        UNIQUE (prescription_id, item_number),
    CONSTRAINT fk_prescription_item_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescription (id) ON DELETE CASCADE,
    CONSTRAINT ck_prescription_item_medication
        CHECK (medication_code IS NOT NULL OR free_text_description IS NOT NULL),
    CONSTRAINT ck_prescription_item_quantity
        CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_prescription_item_medication_code ON prescription_item (medication_code);

-- -------------------------------------------------------------------------------------
-- study_order - orden_estudio
-- -------------------------------------------------------------------------------------
CREATE TABLE study_order (
    id                                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                           BINARY(16)      NOT NULL,
    organization_public_id              BINARY(16)      NOT NULL,
    clinical_encounter_id               BIGINT UNSIGNED NOT NULL,
    study_type_code                     VARCHAR(30)     NOT NULL,
    priority                            ENUM('ROUTINE', 'URGENT', 'STAT') NOT NULL DEFAULT 'ROUTINE',
    instructions                        VARCHAR(500)    NULL,
    icd10_code                          VARCHAR(10)     NULL,
    status                              ENUM('REQUESTED', 'SCHEDULED', 'SAMPLE_COLLECTED', 'IN_PROGRESS',
                                             'REPORTED', 'VOIDED') NOT NULL DEFAULT 'REQUESTED',
    requested_by_professional_public_id BINARY(16)      NOT NULL,
    requested_at                        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    collection_appointment_public_id    BINARY(16)      NULL,
    CONSTRAINT pk_study_order PRIMARY KEY (id),
    CONSTRAINT uq_study_order_public_id UNIQUE (public_id),
    CONSTRAINT fk_study_order_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_study_order_clinical_encounter_id                 ON study_order (clinical_encounter_id);
CREATE INDEX ix_study_order_organization_public_id_status_requested_at
                                                                  ON study_order (organization_public_id, status, requested_at);
CREATE INDEX ix_study_order_study_type_code                       ON study_order (study_type_code);
CREATE INDEX ix_study_order_requested_by_professional_public_id   ON study_order (requested_by_professional_public_id);
CREATE INDEX ix_study_order_icd10_code                            ON study_order (icd10_code);
CREATE INDEX ix_study_order_collection_appointment_public_id      ON study_order (collection_appointment_public_id);

-- -------------------------------------------------------------------------------------
-- study_result - resultado_estudio
-- -------------------------------------------------------------------------------------
CREATE TABLE study_result (
    id                                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    study_order_id                     BIGINT UNSIGNED NOT NULL,
    parameter                          VARCHAR(120)    NOT NULL DEFAULT 'RESULT',
    numeric_value                      DECIMAL(14, 4)  NULL,
    encrypted_text_value               VARBINARY(4096) NULL,
    unit                               VARCHAR(30)     NULL,
    reference_min                      DECIMAL(14, 4)  NULL,
    reference_max                      DECIMAL(14, 4)  NULL,
    out_of_range                       BOOLEAN         NOT NULL DEFAULT FALSE,
    status                             ENUM('PRELIMINARY', 'FINAL', 'CORRECTED') NOT NULL DEFAULT 'PRELIMINARY',
    document_public_id                 BINARY(16)      NULL,
    reported_by_professional_public_id BINARY(16)      NULL,
    reported_at                        TIMESTAMP       NULL,
    delivered_to_patient_at            TIMESTAMP       NULL,
    encryption_key_public_id           BINARY(16)      NULL,
    created_at                         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_study_result PRIMARY KEY (id),
    CONSTRAINT uq_study_result_study_order_id_parameter UNIQUE (study_order_id, parameter),
    CONSTRAINT fk_study_result_study_order
        FOREIGN KEY (study_order_id) REFERENCES study_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_study_result_status_reported_at                 ON study_result (status, reported_at);
CREATE INDEX ix_study_result_document_public_id                 ON study_result (document_public_id);
CREATE INDEX ix_study_result_reported_by_professional_public_id ON study_result (reported_by_professional_public_id);
CREATE INDEX ix_study_result_encryption_key_public_id           ON study_result (encryption_key_public_id);

-- -------------------------------------------------------------------------------------
-- performed_procedure - procedimiento_realizado
-- -------------------------------------------------------------------------------------
CREATE TABLE performed_procedure (
    id                                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    clinical_encounter_id              BIGINT UNSIGNED NOT NULL,
    service_offering_public_id         BINARY(16)      NOT NULL,
    physical_resource_public_id        BINARY(16)      NULL,
    responsible_professional_public_id BINARY(16)      NOT NULL,
    starts_at                          TIMESTAMP       NOT NULL,
    ends_at                            TIMESTAMP       NULL,
    anesthesia_type                    ENUM('NONE', 'LOCAL', 'REGIONAL', 'SEDATION', 'GENERAL') NOT NULL DEFAULT 'NONE',
    encrypted_description              VARBINARY(8192) NULL,
    encrypted_complications            VARBINARY(4096) NULL,
    informed_consent_public_id         BINARY(16)      NULL,
    status                             ENUM('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'SUSPENDED') NOT NULL DEFAULT 'SCHEDULED',
    created_at                         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_performed_procedure PRIMARY KEY (id),
    CONSTRAINT fk_performed_procedure_clinical_encounter
        FOREIGN KEY (clinical_encounter_id) REFERENCES clinical_encounter (id),
    CONSTRAINT ck_performed_procedure_period
        CHECK (ends_at IS NULL OR ends_at >= starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_performed_procedure_clinical_encounter_id              ON performed_procedure (clinical_encounter_id);
CREATE INDEX ix_performed_procedure_physical_resource_public_id_starts_at
                                                                       ON performed_procedure (physical_resource_public_id, starts_at);
CREATE INDEX ix_performed_procedure_service_offering_public_id         ON performed_procedure (service_offering_public_id);
CREATE INDEX ix_performed_procedure_responsible_professional_public_id ON performed_procedure (responsible_professional_public_id);
CREATE INDEX ix_performed_procedure_informed_consent_public_id         ON performed_procedure (informed_consent_public_id);
