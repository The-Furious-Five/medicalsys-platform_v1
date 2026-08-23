-- ms-documental · documents_db · MySQL 8 + MinIO
-- Modelo del Diccionario de Datos MedicalSys (seccion 10 - documents_db).
-- La base de datos conserva metadatos e integridad; los bytes de los archivos
-- se almacenan en MinIO.
--
-- Las referencias a otros microservicios se almacenan como UUID publicos en
-- BINARY(16) y NO llevan claves foraneas fisicas.
-- Los tipos ENUM del diccionario se implementan como VARCHAR + CHECK para
-- mantener los valores controlados y permitir la validacion de Hibernate.

-- ------------------------------------------------------------------------ document
CREATE TABLE document (
    id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                    BINARY(16)      NOT NULL,
    organization_public_id       BINARY(16)      NOT NULL,
    patient_public_id            BINARY(16)      NULL,
    type                         VARCHAR(30)     NOT NULL,
    original_filename            VARCHAR(255)    NOT NULL,
    mime_type                    VARCHAR(120)    NOT NULL,
    size_bytes                   BIGINT UNSIGNED NOT NULL,
    storage_provider             VARCHAR(20)     NOT NULL DEFAULT 'S3',
    region                       VARCHAR(40)     NULL,
    bucket                       VARCHAR(120)    NOT NULL,
    key_object                   VARCHAR(500)    NOT NULL,
    object_version               VARCHAR(80)     NULL,
    sha256_hash                  CHAR(64)        NOT NULL,
    server_side_encryption       VARCHAR(20)     NOT NULL DEFAULT 'SSE_KMS',
    encryption_key_public_id     BINARY(16)      NULL,
    is_dicom                     BOOLEAN         NOT NULL DEFAULT FALSE,
    metadata                     JSON            NULL,
    status                       VARCHAR(20)     NOT NULL DEFAULT 'UPLOAD_PENDING',
    antivirus_status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    uploaded_by_user_public_id   BINARY(16)      NULL,
    uploaded_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_until              DATE            NULL,
    voided_at                    TIMESTAMP       NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_document_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_document_bucket_key_object
        UNIQUE (bucket, key_object),
    CONSTRAINT ck_document_type CHECK (
        type IN (
            'X_RAY','CT_SCAN','MRI','ULTRASOUND','DICOM_STUDY','LAB_RESULT',
            'MEDICAL_REPORT','INFORMED_CONSENT','PRESCRIPTION_PDF','INVOICE_PDF',
            'INVOICE_XML','CLINICAL_PHOTO','IDENTITY_DOCUMENT','SIGNATURE_IMAGE','OTHER'
        )
    ),
    CONSTRAINT ck_document_size_bytes CHECK (
        size_bytes > 0
    ),
    CONSTRAINT ck_document_storage_provider CHECK (
        storage_provider IN ('S3','MINIO','GCS','AZURE_BLOB')
    ),
    CONSTRAINT ck_document_server_side_encryption CHECK (
        server_side_encryption IN ('SSE_S3','SSE_KMS','NONE')
    ),
    CONSTRAINT ck_document_status CHECK (
        status IN ('UPLOAD_PENDING','AVAILABLE','QUARANTINED','VOIDED','ARCHIVED','PURGED')
    ),
    CONSTRAINT ck_document_antivirus_status CHECK (
        antivirus_status IN ('NOT_APPLICABLE','PENDING','CLEAN','INFECTED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_document_organization_public_id_type_uploaded_at
    ON document (organization_public_id, type, uploaded_at);
CREATE INDEX ix_document_patient_public_id
    ON document (patient_public_id);
CREATE INDEX ix_document_status
    ON document (status);
CREATE INDEX ix_document_sha256_hash
    ON document (sha256_hash);
CREATE INDEX ix_document_encryption_key_public_id
    ON document (encryption_key_public_id);
CREATE INDEX ix_document_uploaded_by_user_public_id
    ON document (uploaded_by_user_public_id);

-- ------------------------------------------------------------------- document_link
CREATE TABLE document_link (
    id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id                BIGINT UNSIGNED NOT NULL,
    entity_type                VARCHAR(30)     NOT NULL,
    linked_entity_public_id    BINARY(16)      NOT NULL,
    description                VARCHAR(250)    NULL,
    linked_by_user_public_id   BINARY(16)      NULL,
    created_at                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_document_link_document_id_entity_type_linked_enti_key
        UNIQUE (document_id, entity_type, linked_entity_public_id),
    CONSTRAINT fk_document_link_document
        FOREIGN KEY (document_id) REFERENCES document (id),
    CONSTRAINT ck_document_link_entity_type CHECK (
        entity_type IN (
            'MEDICAL_RECORD','ENCOUNTER','STUDY_ORDER','STUDY_RESULT','CONSENT',
            'PRESCRIPTION','INVOICE','APPOINTMENT','PROFESSIONAL'
        )
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_document_link_entity_type_linked_entity_public_id
    ON document_link (entity_type, linked_entity_public_id);
CREATE INDEX ix_document_link_linked_by_user_public_id
    ON document_link (linked_by_user_public_id);

-- ---------------------------------------------------------------- consent_template
CREATE TABLE consent_template (
    id                            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                     BINARY(16)        NOT NULL,
    organization_public_id        BINARY(16)        NULL,
    code                          VARCHAR(40)       NOT NULL,
    version                       SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    title                         VARCHAR(200)      NOT NULL,
    specialty_code                VARCHAR(20)       NULL,
    markdown_body                 MEDIUMTEXT        NOT NULL,
    variables                     JSON              NULL,
    requires_witness              BOOLEAN           NOT NULL DEFAULT FALSE,
    requires_guardian_for_minor   BOOLEAN           NOT NULL DEFAULT TRUE,
    valid_from                    DATE              NOT NULL,
    valid_until                   DATE              NULL,
    status                        VARCHAR(20)       NOT NULL DEFAULT 'DRAFT',
    body_hash                     CHAR(64)          NOT NULL,
    created_by_user_public_id     BINARY(16)        NULL,
    created_at                    TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_consent_template_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_consent_template_organization_public_id_code_version
        UNIQUE (organization_public_id, code, version),
    CONSTRAINT ck_consent_template_status CHECK (
        status IN ('DRAFT','PUBLISHED','DEPRECATED')
    ),
    CONSTRAINT ck_consent_template_validity CHECK (
        valid_until IS NULL OR valid_until >= valid_from
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_consent_template_organization_public_id_status
    ON consent_template (organization_public_id, status);
CREATE INDEX ix_consent_template_specialty_code
    ON consent_template (specialty_code);
CREATE INDEX ix_consent_template_created_by_user_public_id
    ON consent_template (created_by_user_public_id);

-- --------------------------------------------------------------- informed_consent
CREATE TABLE informed_consent (
    id                              BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                       BINARY(16)        NOT NULL,
    organization_public_id          BINARY(16)        NOT NULL,
    template_id                     BIGINT UNSIGNED   NOT NULL,
    template_version                SMALLINT UNSIGNED NOT NULL,
    patient_public_id               BINARY(16)        NOT NULL,
    medical_record_public_id        BINARY(16)        NOT NULL,
    appointment_public_id           BINARY(16)        NULL,
    clinical_encounter_public_id    BINARY(16)        NULL,
    health_professional_public_id   BINARY(16)        NOT NULL,
    service_offering_public_id      BINARY(16)        NULL,
    rendered_body_hash              CHAR(64)          NOT NULL,
    status                          VARCHAR(20)       NOT NULL DEFAULT 'GENERATED',
    signature_method                VARCHAR(30)       NULL,
    signer_type                     VARCHAR(20)       NOT NULL DEFAULT 'PATIENT',
    signing_patient_public_id       BINARY(16)        NULL,
    signer_document_number          VARCHAR(25)       NULL,
    witness_name                    VARCHAR(160)      NULL,
    witness_document_number         VARCHAR(25)       NULL,
    pdf_document_id                 BIGINT UNSIGNED   NULL,
    document_sha256_hash            CHAR(64)          NULL,
    digital_signature               VARBINARY(4096)   NULL,
    digital_certificate_public_id   BINARY(16)        NULL,
    tsa_timestamp                   VARBINARY(2048)   NULL,
    generated_at                    TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at                      TIMESTAMP         NULL DEFAULT NULL,
    signed_at                       TIMESTAMP         NULL DEFAULT NULL,
    signature_ip                    VARBINARY(16)     NULL,
    signature_user_agent            VARCHAR(300)      NULL,
    signature_geolocation           VARCHAR(60)       NULL,
    revoked_at                      TIMESTAMP         NULL DEFAULT NULL,
    revocation_reason               VARCHAR(300)      NULL,
    encryption_key_public_id        BINARY(16)        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_informed_consent_public_id
        UNIQUE (public_id),
    CONSTRAINT fk_informed_consent_template
        FOREIGN KEY (template_id) REFERENCES consent_template (id),
    CONSTRAINT fk_informed_consent_pdf_document
        FOREIGN KEY (pdf_document_id) REFERENCES document (id),
    CONSTRAINT ck_informed_consent_status CHECK (
        status IN ('GENERATED','SENT','VIEWED','SIGNED','REJECTED','REVOKED','EXPIRED')
    ),
    CONSTRAINT ck_informed_consent_signature_method CHECK (
        signature_method IS NULL OR signature_method IN (
            'DIGITAL_CERTIFICATE','OTP_WHATSAPP','OTP_SMS','BIOMETRIC_TRACE','SCANNED_HANDWRITTEN'
        )
    ),
    CONSTRAINT ck_informed_consent_signer_type CHECK (
        signer_type IN ('PATIENT','LEGAL_GUARDIAN','REPRESENTATIVE')
    ),
    CONSTRAINT ck_informed_consent_signed_data CHECK (
        status <> 'SIGNED'
        OR (
            signed_at IS NOT NULL
            AND signature_method IS NOT NULL
            AND document_sha256_hash IS NOT NULL
        )
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_informed_consent_patient_public_id_status
    ON informed_consent (patient_public_id, status);
CREATE INDEX ix_informed_consent_medical_record_public_id
    ON informed_consent (medical_record_public_id);
CREATE INDEX ix_informed_consent_organization_public_id_status_ge_key
    ON informed_consent (organization_public_id, status, generated_at);
CREATE INDEX ix_informed_consent_appointment_public_id
    ON informed_consent (appointment_public_id);
CREATE INDEX ix_informed_consent_clinical_encounter_public_id
    ON informed_consent (clinical_encounter_public_id);
CREATE INDEX ix_informed_consent_template_id
    ON informed_consent (template_id);
CREATE INDEX ix_informed_consent_health_professional_public_id
    ON informed_consent (health_professional_public_id);
CREATE INDEX ix_informed_consent_service_offering_public_id
    ON informed_consent (service_offering_public_id);
CREATE INDEX ix_informed_consent_pdf_document_id
    ON informed_consent (pdf_document_id);
CREATE INDEX ix_informed_consent_digital_certificate_public_id
    ON informed_consent (digital_certificate_public_id);
CREATE INDEX ix_informed_consent_signing_patient_public_id
    ON informed_consent (signing_patient_public_id);
CREATE INDEX ix_informed_consent_encryption_key_public_id
    ON informed_consent (encryption_key_public_id);

-- ---------------------------------------------------------------- consent_event
CREATE TABLE consent_event (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    informed_consent_id      BIGINT UNSIGNED NOT NULL,
    type                     VARCHAR(30)     NOT NULL,
    occurred_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_account_public_id   BINARY(16)      NULL,
    ip_origin                VARBINARY(16)   NULL,
    user_agent               VARCHAR(300)    NULL,
    detail                   JSON            NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_consent_event_informed_consent
        FOREIGN KEY (informed_consent_id) REFERENCES informed_consent (id) ON DELETE CASCADE,
    CONSTRAINT ck_consent_event_type CHECK (
        type IN (
            'GENERATED','SENT_WHATSAPP','SENT_EMAIL','OPEN','FULLY_READ',
            'OTP_REQUESTED','OTP_VALIDATED','SIGNED','REJECTED','REVOKED','DOWNLOADED'
        )
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_consent_event_informed_consent_id_occurred_at
    ON consent_event (informed_consent_id, occurred_at);
CREATE INDEX ix_consent_event_user_account_public_id
    ON consent_event (user_account_public_id);
