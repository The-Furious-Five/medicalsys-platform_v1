-- =====================================================================================
-- MedicalSys - ms-marketing - marketing_db
-- Fuente normativa: MedicalSys_Microservicios.pdf, seccion 5.12, y el esquema de
-- referencia de servicios/12-ms-marketing/sql/V1__esquema.sql.
--
-- Reglas de la plataforma aplicadas (diccionario, capitulo 13; iguales a billing_db):
--   * Identificadores fisicos en ingles y snake_case.
--   * Solo FK locales. Lo que pertenece a otro servicio se guarda como UUID publico en
--     BINARY(16), sin FK fisica y con indice propio.
--   * El id numerico es clave tecnica local; public_id es el identificador del contrato.
--   * Instantes en UTC; el dinero es DECIMAL, nunca coma flotante.
--
-- El esquema de referencia usa nombres en espanol y claves BIGINT compartidas entre
-- servicios. Aqui se traduce a la convencion vigente del repositorio; la equivalencia
-- tabla por tabla esta en docs/diccionario-datos.md.
--
-- ------------------------------------------------------------------------------------
-- LA FRONTERA DE PRIVACIDAD (criterio C3), hecha esquema
-- ------------------------------------------------------------------------------------
-- Notese lo que NO hay en esta base: ni ref_diagnosis, ni ref_medical_record, ni
-- ref_clinical_encounter, ni una sola columna cie10. La ausencia es la politica.
--
-- La promesa se apoya en tres hechos verificables, no en disciplina:
--   1. marketing_db no contiene ninguna tabla clinica.
--   2. ref_patient no tiene ninguna columna cuyo nombre contenga cie10 ni diagnosis.
--   3. ms-marketing no tiene credenciales de medical_records_db ni cliente HTTP alguno
--      hacia ms-historial.
-- El CHECK (uses_clinical_data = 0) se conserva como segunda barrera declarativa, no
-- como unica defensa. Lo comprueba SchemaConformanceContainerTest.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. REPLICAS DE REFERENCIA (ref_*)
-- Copias locales de solo lectura de datos que pertenecen a otro servicio. Ningun caso de
-- uso de marketing las escribe: se alimentan de eventos entrantes y de una hidratacion
-- puntual contra el propietario cuando llega un identificador desconocido.
-- -------------------------------------------------------------------------------------

-- ref_organization - replica de tenancy_db.organization
CREATE TABLE ref_organization (
    public_id       BINARY(16)      NOT NULL,
    commercial_name VARCHAR(180)    NOT NULL,
    includes_marketing TINYINT(1)   NOT NULL DEFAULT 1
        COMMENT 'Proyeccion de plan_subscription.includes_marketing: 0 desactiva el modulo para el tenant',
    source_version  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    synced_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_ref_organization PRIMARY KEY (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Replica de tenencia.organizacion';

-- ref_patient - replica de patients_db.patient: SOLO demografia.
-- Los ejes de esta tabla son los unicos permitidos para segmentar, junto con la
-- actividad comercial de patient_activity.
CREATE TABLE ref_patient (
    public_id         BINARY(16)      NOT NULL,
    full_name         VARCHAR(290)    NOT NULL,
    birth_date        DATE            NULL,
    sex               ENUM('M', 'F', 'INTERSEX', 'UNDECLARED') NOT NULL DEFAULT 'UNDECLARED',
    city              VARCHAR(80)     NULL,
    phone_e164        VARCHAR(20)     NULL,
    status            ENUM('ACTIVE', 'INACTIVE', 'DECEASED') NOT NULL DEFAULT 'ACTIVE',
    -- Opt-out comercial. Vive aqui y no en ms-notificaciones porque la exclusion debe
    -- aplicarse al materializar la audiencia, antes de que exista mensaje alguno.
    marketing_opt_out TINYINT(1)      NOT NULL DEFAULT 0,
    opted_out_at      TIMESTAMP       NULL,
    source_version    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    synced_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_ref_patient PRIMARY KEY (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Replica de pacientes.paciente: solo demografia, cero dato clinico';

CREATE INDEX ix_ref_patient_sex_birth_date_city ON ref_patient (sex, birth_date, city);
CREATE INDEX ix_ref_patient_marketing_opt_out ON ref_patient (marketing_opt_out);

-- ref_service_offering - replica de tenancy_db.service_offering
CREATE TABLE ref_service_offering (
    public_id              BINARY(16)      NOT NULL,
    organization_public_id BINARY(16)      NOT NULL,
    name                   VARCHAR(160)    NOT NULL,
    base_price_bob         DECIMAL(12, 2)  NOT NULL DEFAULT 0.00,
    active                 TINYINT(1)      NOT NULL DEFAULT 1,
    source_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    synced_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_ref_service_offering PRIMARY KEY (public_id),
    CONSTRAINT fk_ref_service_offering_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Replica de tenencia.servicio';

CREATE INDEX ix_ref_service_offering_organization_public_id
    ON ref_service_offering (organization_public_id);

-- -------------------------------------------------------------------------------------
-- patient_activity - tabla nueva del sprint (actividad_paciente).
-- En el monolito, segmentar exigia JOIN contra cita y factura. Al no existir esas tablas
-- aqui, se mantiene una proyeccion comercial alimentada por eventos. Es la unica
-- "historia" que marketing conoce: cuando vino y cuanto gasto, nunca por que vino.
--
-- suggested_next_visit llega en el evento EncuentroFirmado y transporta la FECHA de
-- control, jamas el motivo clinico. Marketing sabe cuando volver a llamar, no de que
-- padece el paciente.
-- -------------------------------------------------------------------------------------
CREATE TABLE patient_activity (
    patient_public_id               BINARY(16)      NOT NULL,
    organization_public_id          BINARY(16)      NOT NULL,
    first_visit_on                  DATE            NULL,
    last_visit_on                   DATE            NULL,
    total_appointments              INT UNSIGNED    NOT NULL DEFAULT 0,
    total_no_shows                  INT UNSIGNED    NOT NULL DEFAULT 0,
    total_billed_bob                DECIMAL(14, 2)  NOT NULL DEFAULT 0.00,
    last_service_offering_public_id BINARY(16)      NULL,
    suggested_next_visit_on         DATE            NULL
        COMMENT 'Fecha de control recibida en el evento, SIN el motivo clinico',
    updated_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_patient_activity PRIMARY KEY (patient_public_id, organization_public_id),
    CONSTRAINT fk_patient_activity_patient
        FOREIGN KEY (patient_public_id) REFERENCES ref_patient (public_id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_activity_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT fk_patient_activity_service_offering
        FOREIGN KEY (last_service_offering_public_id) REFERENCES ref_service_offering (public_id),
    CONSTRAINT ck_patient_activity_billed CHECK (total_billed_bob >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Proyeccion comercial por paciente: sustituye a los JOIN contra citas y facturas del monolito';

CREATE INDEX ix_patient_activity_reactivation
    ON patient_activity (organization_public_id, last_visit_on);
CREATE INDEX ix_patient_activity_control
    ON patient_activity (organization_public_id, suggested_next_visit_on);
CREATE INDEX ix_patient_activity_service_offering
    ON patient_activity (last_service_offering_public_id);

-- -------------------------------------------------------------------------------------
-- 2. SEGMENTACION Y CAMPANAS
-- -------------------------------------------------------------------------------------

-- audience_segment - segmento_audiencia
CREATE TABLE audience_segment (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                BINARY(16)      NOT NULL,
    organization_public_id   BINARY(16)      NOT NULL,
    name                     VARCHAR(120)    NOT NULL,
    description              VARCHAR(300)    NULL,
    criteria                 JSON            NOT NULL
        COMMENT 'DSL declarativo: edad, sexo, ciudad, ultima visita, servicio y gasto. Nada mas',
    uses_clinical_data       TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Debe permanecer en 0. Segunda barrera tras el aislamiento de base',
    estimated_size           INT UNSIGNED    NULL,
    last_evaluated_at        TIMESTAMP       NULL,
    active                   TINYINT(1)      NOT NULL DEFAULT 1,
    created_by_user_public_id BINARY(16)     NULL,
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_audience_segment PRIMARY KEY (id),
    CONSTRAINT uq_audience_segment_public_id UNIQUE (public_id),
    CONSTRAINT uq_audience_segment_organization_public_id_name
        UNIQUE (organization_public_id, name),
    CONSTRAINT fk_audience_segment_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT ck_audience_segment_privacy CHECK (uses_clinical_data = 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Audiencias sobre datos administrativos y demograficos, nunca clinicos';

CREATE INDEX ix_audience_segment_organization_public_id_active
    ON audience_segment (organization_public_id, active);

-- promotion - promocion. ms-facturacion la valida por API antes de aplicarla.
CREATE TABLE promotion (
    id                          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id                   BINARY(16)        NOT NULL,
    organization_public_id      BINARY(16)        NOT NULL,
    coupon_code                 VARCHAR(30)       NOT NULL,
    name                        VARCHAR(160)      NOT NULL,
    description                 VARCHAR(500)      NULL,
    discount_type               ENUM('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SERVICE') NOT NULL,
    value                       DECIMAL(12, 2)    NOT NULL,
    service_offering_public_id  BINARY(16)        NULL
        COMMENT 'NULL = aplica a cualquier prestacion',
    max_redemptions             INT UNSIGNED      NULL,
    redemptions_per_patient     TINYINT UNSIGNED  NOT NULL DEFAULT 1,
    current_redemptions         INT UNSIGNED      NOT NULL DEFAULT 0,
    valid_from                  DATE              NOT NULL,
    valid_until                 DATE              NOT NULL,
    active                      TINYINT(1)        NOT NULL DEFAULT 1,
    created_by_user_public_id   BINARY(16)        NULL,
    created_at                  TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_promotion PRIMARY KEY (id),
    CONSTRAINT uq_promotion_public_id UNIQUE (public_id),
    CONSTRAINT uq_promotion_organization_public_id_coupon_code
        UNIQUE (organization_public_id, coupon_code),
    CONSTRAINT fk_promotion_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT fk_promotion_service_offering
        FOREIGN KEY (service_offering_public_id) REFERENCES ref_service_offering (public_id),
    CONSTRAINT ck_promotion_value CHECK (value >= 0),
    CONSTRAINT ck_promotion_dates CHECK (valid_until >= valid_from),
    CONSTRAINT ck_promotion_redemptions
        CHECK (max_redemptions IS NULL OR current_redemptions <= max_redemptions)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Cupones y descuentos; ms-facturacion los valida por API antes de aplicarlos';

CREATE INDEX ix_promotion_organization_public_id_active_valid_until
    ON promotion (organization_public_id, active, valid_until);
CREATE INDEX ix_promotion_service_offering_public_id
    ON promotion (service_offering_public_id);

-- campaign - campana
CREATE TABLE campaign (
    id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                    BINARY(16)      NOT NULL,
    organization_public_id       BINARY(16)      NOT NULL,
    name                         VARCHAR(160)    NOT NULL,
    type                         ENUM('PROMOTION', 'PREVENTIVE', 'CONTROL_REMINDER', 'BIRTHDAY',
                                      'REACTIVATION', 'SURVEY') NOT NULL,
    objective                    VARCHAR(300)    NULL,
    channel                      ENUM('WHATSAPP', 'SMS', 'EMAIL', 'PORTAL') NOT NULL DEFAULT 'WHATSAPP',
    message_template_public_id   BINARY(16)      NULL
        COMMENT 'Referencia debil a notifications_db.message_template',
    audience_segment_id          BIGINT UNSIGNED NULL,
    promotion_id                 BIGINT UNSIGNED NULL,
    starts_at                    TIMESTAMP       NOT NULL,
    ends_at                      TIMESTAMP       NULL,
    budget_bob                   DECIMAL(12, 2)  NULL,
    status                       ENUM('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED',
                                      'FINISHED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    total_recipients             INT UNSIGNED    NOT NULL DEFAULT 0,
    total_sent                   INT UNSIGNED    NOT NULL DEFAULT 0,
    total_read                   INT UNSIGNED    NOT NULL DEFAULT 0,
    total_converted              INT UNSIGNED    NOT NULL DEFAULT 0
        COMMENT 'Citas atribuidas a la campana',
    created_by_user_public_id    BINARY(16)      NULL,
    created_at                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_campaign PRIMARY KEY (id),
    CONSTRAINT uq_campaign_public_id UNIQUE (public_id),
    CONSTRAINT fk_campaign_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT fk_campaign_audience_segment
        FOREIGN KEY (audience_segment_id) REFERENCES audience_segment (id),
    CONSTRAINT fk_campaign_promotion
        FOREIGN KEY (promotion_id) REFERENCES promotion (id),
    CONSTRAINT ck_campaign_dates CHECK (ends_at IS NULL OR ends_at >= starts_at),
    CONSTRAINT ck_campaign_budget CHECK (budget_bob IS NULL OR budget_bob >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Campana de marketing o de salud preventiva';

CREATE INDEX ix_campaign_organization_public_id_status_starts_at
    ON campaign (organization_public_id, status, starts_at);
CREATE INDEX ix_campaign_audience_segment_id ON campaign (audience_segment_id);
CREATE INDEX ix_campaign_promotion_id ON campaign (promotion_id);

-- campaign_recipient - campana_destinatario: audiencia materializada con su resultado.
CREATE TABLE campaign_recipient (
    id                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    campaign_id                    BIGINT UNSIGNED NOT NULL,
    patient_public_id              BINARY(16)      NOT NULL,
    notification_public_id         BINARY(16)      NULL
        COMMENT 'Referencia debil a notifications_db.notification',
    status                         ENUM('SELECTED', 'EXCLUDED_OPT_OUT', 'SENT', 'READ',
                                        'CONVERTED', 'FAILED') NOT NULL DEFAULT 'SELECTED',
    exclusion_reason               VARCHAR(120)    NULL,
    generated_appointment_public_id BINARY(16)     NULL
        COMMENT 'Atribucion de conversion; referencia debil a scheduling_db.appointment',
    evaluated_at                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_campaign_recipient PRIMARY KEY (id),
    CONSTRAINT uq_campaign_recipient_campaign_id_patient_public_id
        UNIQUE (campaign_id, patient_public_id),
    CONSTRAINT fk_campaign_recipient_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_recipient_patient
        FOREIGN KEY (patient_public_id) REFERENCES ref_patient (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Audiencia materializada con su resultado individual';

CREATE INDEX ix_campaign_recipient_patient_public_id ON campaign_recipient (patient_public_id);
CREATE INDEX ix_campaign_recipient_campaign_id_status ON campaign_recipient (campaign_id, status);
CREATE INDEX ix_campaign_recipient_notification_public_id ON campaign_recipient (notification_public_id);

-- promotion_redemption - promocion_uso: controla max_redemptions y redemptions_per_patient.
CREATE TABLE promotion_redemption (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                 BINARY(16)      NOT NULL,
    promotion_id              BIGINT UNSIGNED NOT NULL,
    patient_public_id         BINARY(16)      NOT NULL,
    invoice_public_id         BINARY(16)      NULL
        COMMENT 'Referencia debil a billing_db.invoice; sirve tambien de clave de idempotencia',
    appointment_public_id     BINARY(16)      NULL,
    discounted_amount_bob     DECIMAL(12, 2)  NOT NULL DEFAULT 0.00,
    redeemed_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_promotion_redemption PRIMARY KEY (id),
    CONSTRAINT uq_promotion_redemption_public_id UNIQUE (public_id),
    -- Idempotencia del canje: reintentar la misma factura no descuenta dos veces.
    CONSTRAINT uq_promotion_redemption_promotion_id_invoice_public_id
        UNIQUE (promotion_id, invoice_public_id),
    CONSTRAINT fk_promotion_redemption_promotion
        FOREIGN KEY (promotion_id) REFERENCES promotion (id),
    CONSTRAINT fk_promotion_redemption_patient
        FOREIGN KEY (patient_public_id) REFERENCES ref_patient (public_id),
    CONSTRAINT ck_promotion_redemption_amount CHECK (discounted_amount_bob >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Consumo de cupones';

CREATE INDEX ix_promotion_redemption_promotion_id_patient_public_id
    ON promotion_redemption (promotion_id, patient_public_id);
CREATE INDEX ix_promotion_redemption_invoice_public_id
    ON promotion_redemption (invoice_public_id);

-- advertisement - anuncio: zona de anuncios del portal del paciente.
CREATE TABLE advertisement (
    id                         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    public_id                  BINARY(16)       NOT NULL,
    organization_public_id     BINARY(16)       NULL
        COMMENT 'NULL = anuncio institucional de la plataforma',
    title                      VARCHAR(180)     NOT NULL,
    content                    TEXT             NULL,
    image_document_public_id   BINARY(16)       NULL
        COMMENT 'Referencia debil a documents_db.document',
    target_url                 VARCHAR(500)     NULL,
    service_offering_public_id BINARY(16)       NULL
        COMMENT 'Permite agendar directo desde el anuncio',
    placement                  ENUM('PATIENT_PORTAL', 'APP_HOME', 'WAITING_ROOM', 'EMAIL_FOOTER')
                               NOT NULL DEFAULT 'PATIENT_PORTAL',
    priority                   TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '1 = mayor prioridad',
    target_audience            JSON             NULL COMMENT 'Filtros ligeros: edad, sexo, ciudad',
    valid_from                 TIMESTAMP        NOT NULL,
    valid_until                TIMESTAMP        NOT NULL,
    status                     ENUM('DRAFT', 'PUBLISHED', 'PAUSED', 'FINISHED') NOT NULL DEFAULT 'DRAFT',
    impressions                INT UNSIGNED     NOT NULL DEFAULT 0,
    clicks                     INT UNSIGNED     NOT NULL DEFAULT 0,
    created_by_user_public_id  BINARY(16)       NULL,
    created_at                 TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_advertisement PRIMARY KEY (id),
    CONSTRAINT uq_advertisement_public_id UNIQUE (public_id),
    CONSTRAINT fk_advertisement_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT fk_advertisement_service_offering
        FOREIGN KEY (service_offering_public_id) REFERENCES ref_service_offering (public_id),
    CONSTRAINT ck_advertisement_validity CHECK (valid_until > valid_from),
    CONSTRAINT ck_advertisement_priority CHECK (priority BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Zona de anuncios y promociones de salud del portal del paciente';

CREATE INDEX ix_advertisement_status_placement_valid_from_valid_until
    ON advertisement (status, placement, valid_from, valid_until);
CREATE INDEX ix_advertisement_organization_public_id ON advertisement (organization_public_id);
CREATE INDEX ix_advertisement_service_offering_public_id ON advertisement (service_offering_public_id);

-- -------------------------------------------------------------------------------------
-- 3. FIDELIZACION
-- -------------------------------------------------------------------------------------

-- loyalty_program - programa_fidelidad
CREATE TABLE loyalty_program (
    id                     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id              BINARY(16)        NOT NULL,
    organization_public_id BINARY(16)        NOT NULL,
    name                   VARCHAR(120)      NOT NULL,
    points_per_bob         DECIMAL(8, 4)     NOT NULL DEFAULT 1.0000,
    bob_per_point          DECIMAL(8, 4)     NOT NULL DEFAULT 0.0100,
    points_validity_months SMALLINT UNSIGNED NULL,
    active                 TINYINT(1)        NOT NULL DEFAULT 1,
    created_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_loyalty_program PRIMARY KEY (id),
    CONSTRAINT uq_loyalty_program_public_id UNIQUE (public_id),
    CONSTRAINT uq_loyalty_program_organization_public_id_name
        UNIQUE (organization_public_id, name),
    CONSTRAINT fk_loyalty_program_organization
        FOREIGN KEY (organization_public_id) REFERENCES ref_organization (public_id),
    CONSTRAINT ck_loyalty_program_rates CHECK (points_per_bob >= 0 AND bob_per_point >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Reglas del programa de fidelizacion del tenant';

-- loyalty_points_movement - movimiento_puntos: libro mayor append-only.
-- El saldo se deriva sumando los movimientos; nunca se sobrescribe una fila.
CREATE TABLE loyalty_points_movement (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                 BINARY(16)      NOT NULL,
    loyalty_program_id        BIGINT UNSIGNED NOT NULL,
    patient_public_id         BINARY(16)      NOT NULL,
    type                      ENUM('ACCRUAL', 'REDEMPTION', 'EXPIRATION', 'MANUAL_ADJUSTMENT',
                                   'REFERRAL_BONUS') NOT NULL,
    points                    INT             NOT NULL COMMENT 'Positivo acumula, negativo descuenta',
    resulting_balance         INT             NOT NULL,
    invoice_public_id         BINARY(16)      NULL,
    appointment_public_id     BINARY(16)      NULL,
    source_event_public_id    BINARY(16)      NULL
        COMMENT 'Idempotencia: una misma FacturaEmitida no acumula dos veces',
    reason                    VARCHAR(250)    NULL,
    registered_by_user_public_id BINARY(16)   NULL,
    occurred_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_loyalty_points_movement PRIMARY KEY (id),
    CONSTRAINT uq_loyalty_points_movement_public_id UNIQUE (public_id),
    CONSTRAINT uq_loyalty_points_movement_source_event_public_id
        UNIQUE (source_event_public_id),
    CONSTRAINT fk_loyalty_points_movement_program
        FOREIGN KEY (loyalty_program_id) REFERENCES loyalty_program (id),
    CONSTRAINT fk_loyalty_points_movement_patient
        FOREIGN KEY (patient_public_id) REFERENCES ref_patient (public_id),
    CONSTRAINT ck_loyalty_points_movement_balance CHECK (resulting_balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Libro mayor de puntos (append-only): el saldo se deriva, no se sobrescribe';

CREATE INDEX ix_loyalty_points_movement_program_patient_occurred_at
    ON loyalty_points_movement (loyalty_program_id, patient_public_id, occurred_at);
CREATE INDEX ix_loyalty_points_movement_invoice_public_id
    ON loyalty_points_movement (invoice_public_id);

-- -------------------------------------------------------------------------------------
-- 4. OUTBOX / INBOX (patron uniforme de los servicios MySQL de la plataforma)
-- -------------------------------------------------------------------------------------

-- outbox_event: el evento se guarda con el cambio, en la misma transaccion. Hoy el MVP no
-- tiene broker; el relay lo drena segun MARKETING_OUTBOX_MODE y la tabla queda lista para
-- conectarlo sin tocar ningun caso de uso.
CREATE TABLE outbox_event (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_public_id       BINARY(16)      NOT NULL,
    aggregate_type        VARCHAR(60)     NOT NULL,
    aggregate_public_id   BINARY(16)      NOT NULL,
    partition_key         VARCHAR(80)     NOT NULL,
    event_type            VARCHAR(80)     NOT NULL,
    payload               JSON            NOT NULL,
    headers               JSON            NULL,
    status                ENUM('PENDING', 'PUBLISHED', 'ERROR') NOT NULL DEFAULT 'PENDING',
    attempts              SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    last_error            VARCHAR(500)    NULL,
    created_at            TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at          TIMESTAMP(3)    NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_event_public_id UNIQUE (event_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Transactional Outbox: el evento se guarda con el cambio, en la misma transaccion';

CREATE INDEX ix_outbox_event_status_id ON outbox_event (status, id);

-- processed_event: inbox de idempotencia. Convierte at-least-once en effectively-once.
CREATE TABLE processed_event (
    event_public_id BINARY(16)   NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    processed_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_processed_event PRIMARY KEY (event_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Inbox de idempotencia: convierte at-least-once en effectively-once';

CREATE INDEX ix_processed_event_processed_at ON processed_event (processed_at);

-- -------------------------------------------------------------------------------------
-- 5. VISTAS
-- Son las dos consultas de lectura que el servicio usa de verdad: el saldo de puntos y el
-- rendimiento de campana. Estan en la base y no en Java porque agregan sobre el libro
-- mayor y sobre los contadores, y asi la formula queda en un solo lugar.
-- -------------------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_loyalty_balance AS
SELECT m.loyalty_program_id,
       m.patient_public_id,
       p.full_name,
       SUM(m.points)       AS balance_points,
       MAX(m.occurred_at)  AS last_movement_at
FROM loyalty_points_movement m
JOIN ref_patient p ON p.public_id = m.patient_public_id
GROUP BY m.loyalty_program_id, m.patient_public_id, p.full_name;

CREATE OR REPLACE VIEW v_campaign_performance AS
SELECT c.id AS campaign_id,
       c.public_id,
       c.organization_public_id,
       c.name,
       c.type,
       c.status,
       c.total_recipients,
       c.total_sent,
       c.total_read,
       c.total_converted,
       ROUND(100 * c.total_sent      / NULLIF(c.total_recipients, 0), 2) AS send_rate_pct,
       ROUND(100 * c.total_read      / NULLIF(c.total_sent, 0), 2)       AS read_rate_pct,
       ROUND(100 * c.total_converted / NULLIF(c.total_sent, 0), 2)       AS conversion_rate_pct,
       (SELECT COUNT(*) FROM campaign_recipient r
         WHERE r.campaign_id = c.id AND r.status = 'EXCLUDED_OPT_OUT')   AS excluded_opt_out
FROM campaign c;
