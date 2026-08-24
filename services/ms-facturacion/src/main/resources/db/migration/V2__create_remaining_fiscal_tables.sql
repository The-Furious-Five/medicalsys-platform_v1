-- =====================================================================================
-- MedicalSys - ms-facturacion - billing_db (cinco tablas restantes del capitulo 11)
-- Fuente normativa: MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 11.
--
-- V1 creo las siete tablas del guion de la demostracion. Esta migracion completa el
-- capitulo 11 con las cinco que faltaban: economic_activity, payment, credit_debit_note,
-- significant_event y tax_submission. Con V1 + V2, billing_db tiene las 12 tablas que
-- declara el diccionario.
--
-- Reglas aplicadas (capitulo 13 del diccionario):
--   * Identificadores fisicos en ingles y snake_case, copiados literalmente del capitulo 11.
--   * Solo FK locales. Los identificadores de otros servicios son UUID sin FK y llevan
--     indice propio para busquedas y joins en memoria.
--   * UUID publico en BINARY(16); el id numerico es clave tecnica local.
--   * Instantes en UTC; el dinero es DECIMAL, nunca coma flotante.
--
-- Alcance: solo DDL. Estas cinco tablas todavia no tienen entidad JPA, repositorio ni
-- endpoint; el flujo que las usa (pagos, anulacion, contingencia, envio al SIAT) sigue en
-- la deuda consciente del README. El esquema queda conforme al diccionario desde ya.
--
-- Nota sobre tax_submission.entity_public_id: el diccionario lo tipa BIGINT UNSIGNED pese
-- a que el nombre sugiere un UUID. Se implementa tal cual lo especifica el documento.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- economic_activity - actividad_economica
-- Actividad economica habilitada para facturar. Cuelga de la configuracion del tenant.
-- -------------------------------------------------------------------------------------
CREATE TABLE economic_activity (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    billing_configuration_id BIGINT UNSIGNED NOT NULL,
    -- Codigo de actividad economica (CAEB) del SIN.
    caeb_code                VARCHAR(20)     NOT NULL,
    description              VARCHAR(300)    NOT NULL,
    type                     ENUM('PRIMARY', 'SECONDARY') NOT NULL DEFAULT 'SECONDARY',
    active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_economic_activity PRIMARY KEY (id),
    CONSTRAINT uq_economic_activity_billing_configuration_id_caeb_code
        UNIQUE (billing_configuration_id, caeb_code),
    CONSTRAINT fk_economic_activity_billing_configuration
        FOREIGN KEY (billing_configuration_id) REFERENCES billing_configuration (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- payment - pago
-- Pago aplicado a una factura. invoice_id es NULL en anticipos o pagos a cuenta.
-- -------------------------------------------------------------------------------------
CREATE TABLE payment (
    id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                  BINARY(16)      NOT NULL,
    organization_public_id     BINARY(16)      NOT NULL,
    invoice_id                 BIGINT UNSIGNED NULL,
    patient_public_id          BINARY(16)      NOT NULL,
    amount                     DECIMAL(14,2)   NOT NULL,
    currency                   CHAR(3)         NOT NULL DEFAULT 'BOB',
    method                     ENUM('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'QR_SIMPLE', 'TRANSFER',
                                    'MOBILE_WALLET', 'INSURANCE', 'INTERNAL_CREDIT')
                               NOT NULL DEFAULT 'CASH',
    -- Ej. QR Simple BCP, Tigo Money, Libelula.
    gateway                    VARCHAR(80)     NULL,
    external_transaction_id    VARCHAR(120)    NULL,
    status                     ENUM('PENDING', 'CONFIRMED', 'REJECTED', 'REVERSED') NOT NULL DEFAULT 'PENDING',
    received_at                TIMESTAMP       NULL,
    recorded_by_user_public_id BINARY(16)      NULL,
    observation                VARCHAR(300)    NULL,
    created_at                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uq_payment_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_gateway_external_transaction_id UNIQUE (gateway, external_transaction_id),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_payment_invoice_id                              ON payment (invoice_id);
CREATE INDEX ix_payment_patient_public_id_created_at            ON payment (patient_public_id, created_at);
CREATE INDEX ix_payment_organization_public_id_status_created_at
    ON payment (organization_public_id, status, created_at);
CREATE INDEX ix_payment_recorded_by_user_public_id              ON payment (recorded_by_user_public_id);

-- -------------------------------------------------------------------------------------
-- credit_debit_note - nota_credito_debito
-- Documento que modifica una factura emitida. Nunca se borra la factura original.
-- -------------------------------------------------------------------------------------
CREATE TABLE credit_debit_note (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_public_id BINARY(16)      NOT NULL,
    original_invoice_id    BIGINT UNSIGNED NOT NULL,
    point_of_sale_id       BIGINT UNSIGNED NOT NULL,
    cufd_id                BIGINT UNSIGNED NOT NULL,
    type                   ENUM('CREDIT', 'DEBIT') NOT NULL DEFAULT 'CREDIT',
    note_number            BIGINT UNSIGNED NOT NULL,
    cuf                    VARCHAR(80)     NOT NULL,
    issued_at              TIMESTAMP       NOT NULL,
    credit_effect_amount   DECIMAL(14,2)   NOT NULL,
    reason                 VARCHAR(300)    NOT NULL,
    status                 ENUM('ISSUED', 'SEND_PENDING', 'VALIDATED', 'REJECTED', 'VOIDED')
                           NOT NULL DEFAULT 'ISSUED',
    tax_receipt_code       VARCHAR(50)     NULL,
    xml_document_public_id BINARY(16)      NULL,
    pdf_document_public_id BINARY(16)      NULL,
    created_at             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_credit_debit_note PRIMARY KEY (id),
    CONSTRAINT uq_credit_debit_note_cuf UNIQUE (cuf),
    CONSTRAINT uq_credit_debit_note_point_of_sale_id_type_note_number
        UNIQUE (point_of_sale_id, type, note_number),
    CONSTRAINT fk_credit_debit_note_original_invoice
        FOREIGN KEY (original_invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_credit_debit_note_point_of_sale
        FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id),
    CONSTRAINT fk_credit_debit_note_cufd FOREIGN KEY (cufd_id) REFERENCES cufd (id),
    CONSTRAINT ck_credit_debit_note_amount CHECK (credit_effect_amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_credit_debit_note_original_invoice_id    ON credit_debit_note (original_invoice_id);
CREATE INDEX ix_credit_debit_note_organization_public_id_issued_at
    ON credit_debit_note (organization_public_id, issued_at);
CREATE INDEX ix_credit_debit_note_cufd_id                ON credit_debit_note (cufd_id);
CREATE INDEX ix_credit_debit_note_xml_document_public_id ON credit_debit_note (xml_document_public_id);
CREATE INDEX ix_credit_debit_note_pdf_document_public_id ON credit_debit_note (pdf_document_public_id);

-- -------------------------------------------------------------------------------------
-- significant_event - evento_significativo
-- Contingencia reportada al SIN. event_cufd_id es el CUFD vigente al momento de reportar.
-- -------------------------------------------------------------------------------------
CREATE TABLE significant_event (
    id                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    point_of_sale_id      BIGINT UNSIGNED  NOT NULL,
    cufd_id               BIGINT UNSIGNED  NOT NULL,
    event_cufd_id         BIGINT UNSIGNED  NULL,
    -- 1=corte internet, 2=inaccesibilidad SIN, 3=corte energia...
    event_code            TINYINT UNSIGNED NOT NULL,
    description           VARCHAR(300)     NOT NULL,
    starts_at             TIMESTAMP        NOT NULL,
    ends_at               TIMESTAMP        NULL,
    offline_invoice_count INT UNSIGNED     NOT NULL DEFAULT 0,
    event_receipt_code    VARCHAR(50)      NULL,
    status                ENUM('OPEN', 'REPORTED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    created_at            TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_significant_event PRIMARY KEY (id),
    CONSTRAINT fk_significant_event_point_of_sale
        FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id),
    CONSTRAINT fk_significant_event_cufd       FOREIGN KEY (cufd_id)       REFERENCES cufd (id),
    CONSTRAINT fk_significant_event_event_cufd FOREIGN KEY (event_cufd_id) REFERENCES cufd (id),
    CONSTRAINT ck_significant_event_period CHECK (ends_at IS NULL OR ends_at >= starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_significant_event_point_of_sale_id_starts_at
    ON significant_event (point_of_sale_id, starts_at);
CREATE INDEX ix_significant_event_status        ON significant_event (status);
CREATE INDEX ix_significant_event_cufd_id       ON significant_event (cufd_id);
CREATE INDEX ix_significant_event_event_cufd_id ON significant_event (event_cufd_id);

-- -------------------------------------------------------------------------------------
-- tax_submission - envio_sin
-- Intento de envio simulado o real hacia el sistema fiscal, con reintento por backoff.
-- -------------------------------------------------------------------------------------
CREATE TABLE tax_submission (
    id                     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    organization_public_id BINARY(16)        NOT NULL,
    entity_type            ENUM('INVOICE', 'CREDIT_DEBIT_NOTE', 'SIGNIFICANT_EVENT', 'CANCELLATION',
                                'OFFLINE_PACKAGE', 'CUFD_REQUEST', 'CUIS_REQUEST', 'CATALOG_SYNC')
                           NOT NULL,
    entity_public_id       BIGINT UNSIGNED   NULL,
    -- Metodo del servicio SIAT invocado.
    operation              VARCHAR(80)       NOT NULL,
    payload_hash           CHAR(64)          NULL,
    status                 ENUM('PENDING', 'SENDING', 'SENT', 'ERROR', 'DISMISSED') NOT NULL DEFAULT 'PENDING',
    attempt_count          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    -- Backoff exponencial ante fallas de red.
    next_attempt_at        TIMESTAMP         NULL,
    last_error             VARCHAR(500)      NULL,
    response_code          VARCHAR(20)       NULL,
    response               JSON              NULL,
    created_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_tax_submission PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_tax_submission_status_next_attempt_at ON tax_submission (status, next_attempt_at);
CREATE INDEX ix_tax_submission_entity_type_entity_public_id
    ON tax_submission (entity_type, entity_public_id);
CREATE INDEX ix_tax_submission_organization_public_id_created_at
    ON tax_submission (organization_public_id, created_at);
