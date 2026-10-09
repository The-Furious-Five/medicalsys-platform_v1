-- =====================================================================================
-- MedicalSys - ms-facturacion - billing_db (esquema fiscal reducido del MVP)
-- Fuente normativa: MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 11.
--
-- Reglas aplicadas (capitulo 13 del diccionario):
--   * Identificadores fisicos en ingles y snake_case.
--   * Solo FK locales. Los identificadores de otros servicios son UUID sin FK y llevan
--     indice propio para busquedas y joins en memoria.
--   * UUID publico en BINARY(16); el id numerico es clave tecnica local.
--   * Instantes en UTC; el dinero es DECIMAL, nunca coma flotante.
--
-- Alcance de esta migracion (tarea MVP-13, "migraciones fiscales reducidas"):
--   Incluye  : billing_configuration, point_of_sale, cuis, cufd, document_sequence,
--              invoice, invoice_item. Son las siete tablas que necesita el guion de la
--              demostracion: configurar, correlacionar, emitir y consultar una factura.
--   Difiere  : economic_activity, payment, credit_debit_note, significant_event y
--              tax_submission. Estan en el diccionario y llegan en una V2 cuando el
--              flujo las necesite; una migracion aplicada no se edita despues.
--
-- Esta base NO contiene ninguna tabla ni columna de narrativa clinica. La trazabilidad
-- hacia la atencion se guarda como UUID sueltos en invoice_item (cita y encuentro).
--
-- Decisiones de implementacion registradas (guia Sprint 2, seccion 3.3):
--   1. invoice.patient_public_id. El diccionario no lo lista entre las columnas de
--      invoice, pero declara el indice ix_invoice_patient_id_issued_at y la matriz de
--      referencias externas asigna billing_db -> patient_public_id (ms-pacientes, "DTO
--      fiscal minimo"). Se implementa como BINARY(16) NOT NULL sin FK, con el nombre
--      canonico de la plataforma (patient_public_id) y su indice.
--   2. invoice.status. Tampoco aparece en la lista de columnas, pero el diccionario
--      declara el indice ix_invoice_status_issued_at y el CHECK
--      "estado <> 'VOIDED' OR (voided_at IS NOT NULL AND ...)". Se implementa como ENUM
--      siguiendo los valores de credit_debit_note (ISSUED, SEND_PENDING, VALIDATED,
--      REJECTED, VOIDED) y se agrega DEMO_ACCEPTED, que es el estado que exige el
--      sprint para una factura de demostracion.
--   Ambas quedan anotadas tambien en README.md, seccion "Decisiones registradas".
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- billing_configuration - configuracion_facturacion
-- Configuracion fiscal de una organizacion. Una fila por tenant.
-- -------------------------------------------------------------------------------------
CREATE TABLE billing_configuration (
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_public_id        BINARY(16)      NOT NULL,
    nit                           VARCHAR(15)     NOT NULL,
    legal_name                    VARCHAR(180)    NOT NULL,
    modality                      ENUM('ONLINE_ELECTRONIC', 'ONLINE_COMPUTERIZED', 'MANUAL')
                                  NOT NULL DEFAULT 'ONLINE_COMPUTERIZED',
    environment                   ENUM('PILOT', 'PRODUCTION') NOT NULL DEFAULT 'PILOT',
    -- Token del SIN cifrado con la DEK de facturacion. En el MVP siempre es NULL: no se
    -- usan credenciales reales del SIN ni se guarda ningun secreto en esta base.
    encrypted_delegated_token     VARBINARY(2048) NULL,
    token_valid_until             TIMESTAMP       NULL,
    encryption_key_public_id      BINARY(16)      NULL,
    digital_certificate_public_id BINARY(16)      NULL,
    vat_caption                   VARCHAR(300)    NULL,
    notification_email            VARCHAR(160)    NULL,
    status                        ENUM('CONFIGURING', 'ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'CONFIGURING',
    created_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_billing_configuration PRIMARY KEY (id),
    CONSTRAINT uq_billing_configuration_organization_public_id UNIQUE (organization_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_billing_configuration_encryption_key_public_id
    ON billing_configuration (encryption_key_public_id);
CREATE INDEX ix_billing_configuration_digital_certificate_public_id
    ON billing_configuration (digital_certificate_public_id);

-- -------------------------------------------------------------------------------------
-- point_of_sale - punto_venta
-- Punto de venta fiscal asociado a una sucursal. tax_branch_code = 0 es casa matriz.
-- -------------------------------------------------------------------------------------
CREATE TABLE point_of_sale (
    id                     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    organization_public_id BINARY(16)        NOT NULL,
    branch_public_id       BINARY(16)        NOT NULL,
    tax_branch_code        SMALLINT UNSIGNED NOT NULL,
    point_of_sale_code     SMALLINT UNSIGNED NOT NULL,
    name                   VARCHAR(120)      NOT NULL,
    type                   ENUM('COMMISSION_AGENT', 'COLLECTION_DESK', 'MOBILE', 'YPFB', 'ATM', 'OTHER')
                           NOT NULL DEFAULT 'COLLECTION_DESK',
    status                 ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_point_of_sale PRIMARY KEY (id),
    CONSTRAINT uq_point_of_sale_organization_public_id_tax_branch_c_key
        UNIQUE (organization_public_id, tax_branch_code, point_of_sale_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_point_of_sale_branch_public_id_status ON point_of_sale (branch_public_id, status);

-- -------------------------------------------------------------------------------------
-- cuis - Codigo Unico de Inicio de Sistemas y su vigencia.
-- En el MVP el valor lo produce el adaptador SIN simulado; nunca el servicio real.
-- -------------------------------------------------------------------------------------
CREATE TABLE cuis (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    point_of_sale_id BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(30)     NOT NULL,
    valid_until      DATE            NOT NULL,
    requested_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status           ENUM('ACTIVE', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_cuis PRIMARY KEY (id),
    CONSTRAINT uq_cuis_point_of_sale_id_code UNIQUE (point_of_sale_id, code),
    CONSTRAINT fk_cuis_point_of_sale FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_cuis_point_of_sale_id_status_valid_until ON cuis (point_of_sale_id, status, valid_until);

-- -------------------------------------------------------------------------------------
-- cufd - Codigo Unico de Facturacion Diaria y su vigencia.
-- La factura lo referencia porque el diccionario lo exige (invoice.cufd_id NOT NULL).
-- -------------------------------------------------------------------------------------
CREATE TABLE cufd (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    point_of_sale_id BIGINT UNSIGNED NOT NULL,
    cuis_id          BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(200)    NOT NULL,
    control_code     VARCHAR(30)     NOT NULL,
    address          VARCHAR(250)    NOT NULL,
    valid_from       TIMESTAMP       NOT NULL,
    valid_until      TIMESTAMP       NOT NULL,
    requested_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cufd PRIMARY KEY (id),
    CONSTRAINT uq_cufd_code UNIQUE (code),
    CONSTRAINT fk_cufd_point_of_sale FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id),
    CONSTRAINT fk_cufd_cuis FOREIGN KEY (cuis_id) REFERENCES cuis (id),
    CONSTRAINT ck_cufd_validity_range CHECK (valid_until > valid_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_cufd_point_of_sale_id_valid_until ON cufd (point_of_sale_id, valid_until);
CREATE INDEX ix_cufd_cuis_id                      ON cufd (cuis_id);

-- -------------------------------------------------------------------------------------
-- document_sequence - correlativo_documento
-- Contador bloqueable por punto de venta y tipo de documento fiscal.
--
-- Es una tabla, no un AUTO_INCREMENT, justamente porque el correlativo fiscal debe
-- reiniciarse y auditarse por punto de venta. El servicio la lee con SELECT ... FOR
-- UPDATE dentro de la transaccion de emision: dos cajas simultaneas se serializan y
-- ninguna repite numero. La garantia final la impone uq_invoice_..._invoice_number.
-- -------------------------------------------------------------------------------------
CREATE TABLE document_sequence (
    point_of_sale_id  BIGINT UNSIGNED NOT NULL,
    tax_document_type VARCHAR(30)     NOT NULL,
    last_number       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_document_sequence PRIMARY KEY (point_of_sale_id, tax_document_type),
    CONSTRAINT fk_document_sequence_point_of_sale
        FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- invoice - factura
-- Cabecera fiscal y monetaria. Los datos del cliente son un snapshot congelado del DTO
-- fiscal minimo de ms-pacientes: la factura no cambia si el paciente se edita despues.
-- -------------------------------------------------------------------------------------
CREATE TABLE invoice (
    id                           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    public_id                    BINARY(16)       NOT NULL,
    organization_public_id       BINARY(16)       NOT NULL,
    branch_public_id             BINARY(16)       NOT NULL,
    patient_public_id            BINARY(16)       NOT NULL,
    point_of_sale_id             BIGINT UNSIGNED  NOT NULL,
    cufd_id                      BIGINT UNSIGNED  NOT NULL,
    tax_document_type            ENUM('PURCHASE_SALE_INVOICE', 'HEALTH_SECTOR_INVOICE', 'PROFESSIONAL_FEES_RECEIPT')
                                 NOT NULL DEFAULT 'PURCHASE_SALE_INVOICE',
    emission_type                ENUM('ONLINE', 'OFFLINE', 'BULK') NOT NULL DEFAULT 'ONLINE',
    invoice_number               BIGINT UNSIGNED  NOT NULL,
    cuf                          VARCHAR(80)      NOT NULL,
    cafc                         VARCHAR(30)      NULL,
    identity_document_type_code  TINYINT UNSIGNED NOT NULL DEFAULT 1,
    customer_document_number     VARCHAR(20)      NOT NULL,
    customer_document_complement VARCHAR(5)       NULL,
    customer_legal_name          VARCHAR(200)     NOT NULL,
    customer_email               VARCHAR(160)     NULL,
    exchange_rate                DECIMAL(12,5)    NOT NULL DEFAULT 1.00000,
    total_amount                 DECIMAL(14,2)    NOT NULL,
    vat_taxable_amount           DECIMAL(14,2)    NOT NULL,
    total_discount_amount        DECIMAL(14,2)    NOT NULL DEFAULT 0.00,
    gift_card_amount             DECIMAL(14,2)    NOT NULL DEFAULT 0.00,
    currency_total_amount        DECIMAL(14,2)    NOT NULL,
    payment_method_code          TINYINT UNSIGNED NOT NULL DEFAULT 1,
    card_number_suffix           VARCHAR(4)       NULL,
    issued_at                    TIMESTAMP        NOT NULL,
    tax_receipt_code             VARCHAR(50)      NULL,
    validated_at                 TIMESTAMP        NULL,
    -- Leyenda impresa. En el MVP siempre lleva NO VALIDA TRIBUTARIAMENTE.
    caption                      VARCHAR(500)     NULL,
    qr_url                       VARCHAR(500)     NULL,
    xml_hash                     CHAR(64)         NULL,
    xml_document_public_id       BINARY(16)       NULL,
    pdf_document_public_id       BINARY(16)       NULL,
    status                       ENUM('ISSUED', 'SEND_PENDING', 'DEMO_ACCEPTED', 'VALIDATED', 'REJECTED', 'VOIDED')
                                 NOT NULL DEFAULT 'ISSUED',
    cancellation_reason_code     TINYINT UNSIGNED NULL,
    voided_at                    TIMESTAMP        NULL,
    issued_by_user_public_id     BINARY(16)       NULL,
    created_at                   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_invoice PRIMARY KEY (id),
    CONSTRAINT uq_invoice_public_id UNIQUE (public_id),
    CONSTRAINT uq_invoice_cuf UNIQUE (cuf),
    CONSTRAINT uq_invoice_point_of_sale_id_tax_document_type_invoice_number
        UNIQUE (point_of_sale_id, tax_document_type, invoice_number),
    CONSTRAINT fk_invoice_point_of_sale FOREIGN KEY (point_of_sale_id) REFERENCES point_of_sale (id),
    CONSTRAINT fk_invoice_cufd         FOREIGN KEY (cufd_id)          REFERENCES cufd (id),
    CONSTRAINT ck_invoice_amounts
        CHECK (total_amount >= 0 AND vat_taxable_amount >= 0 AND total_discount_amount >= 0),
    CONSTRAINT ck_invoice_offline_requires_cafc
        CHECK (emission_type <> 'OFFLINE' OR cafc IS NOT NULL),
    CONSTRAINT ck_invoice_voided_requires_reason
        CHECK (status <> 'VOIDED' OR (voided_at IS NOT NULL AND cancellation_reason_code IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_invoice_organization_public_id_issued_at ON invoice (organization_public_id, issued_at);
CREATE INDEX ix_invoice_patient_public_id_issued_at      ON invoice (patient_public_id, issued_at);
CREATE INDEX ix_invoice_status_issued_at                 ON invoice (status, issued_at);
CREATE INDEX ix_invoice_cufd_id                          ON invoice (cufd_id);
CREATE INDEX ix_invoice_branch_public_id                 ON invoice (branch_public_id);
CREATE INDEX ix_invoice_xml_document_public_id           ON invoice (xml_document_public_id);
CREATE INDEX ix_invoice_pdf_document_public_id           ON invoice (pdf_document_public_id);
CREATE INDEX ix_invoice_issued_by_user_public_id         ON invoice (issued_by_user_public_id);

-- -------------------------------------------------------------------------------------
-- invoice_item - factura_detalle
-- Item facturado con descripcion y precio congelados en el momento de la emision.
-- El precio proviene de ms-tenencia, nunca del cliente HTTP.
-- -------------------------------------------------------------------------------------
CREATE TABLE invoice_item (
    id                           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    invoice_id                   BIGINT UNSIGNED   NOT NULL,
    item_number                  SMALLINT UNSIGNED NOT NULL,
    service_offering_public_id   BINARY(16)        NULL,
    appointment_public_id        BINARY(16)        NULL,
    clinical_encounter_public_id BINARY(16)        NULL,
    economic_activity_code       VARCHAR(20)       NOT NULL,
    tax_product_code             VARCHAR(30)       NOT NULL,
    internal_product_code        VARCHAR(30)       NULL,
    description                  VARCHAR(500)      NOT NULL,
    quantity                     DECIMAL(12,4)     NOT NULL DEFAULT 1,
    unit_of_measure              SMALLINT UNSIGNED NOT NULL DEFAULT 58,
    unit_price                   DECIMAL(14,2)     NOT NULL,
    discount_amount              DECIMAL(14,2)     NOT NULL DEFAULT 0.00,
    subtotal                     DECIMAL(14,2)     NOT NULL,
    CONSTRAINT pk_invoice_item PRIMARY KEY (id),
    CONSTRAINT uq_invoice_item_invoice_id_item_number UNIQUE (invoice_id, item_number),
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id) ON DELETE CASCADE,
    CONSTRAINT ck_invoice_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_item_amounts
        CHECK (unit_price >= 0 AND discount_amount >= 0 AND subtotal >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_invoice_item_service_offering_public_id   ON invoice_item (service_offering_public_id);
CREATE INDEX ix_invoice_item_appointment_public_id        ON invoice_item (appointment_public_id);
CREATE INDEX ix_invoice_item_clinical_encounter_public_id ON invoice_item (clinical_encounter_public_id);
