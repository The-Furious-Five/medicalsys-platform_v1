-- ms-auditoria · audit_db · MySQL 8
-- Bitacora append-only de la plataforma MedicalSys.
--
-- Reglas del modelo, en el mismo estilo que el resto de bases del diccionario:
--   * Las referencias a otros microservicios se guardan como UUID publicos en
--     BINARY(16) y NO llevan claves foraneas fisicas.
--   * Los ENUM del diccionario se implementan como VARCHAR + CHECK.
--   * Se usa DATETIME(6) y no TIMESTAMP porque la retencion legal de una
--     bitacora clinica supera el limite de 2038 de TIMESTAMP en MySQL.
--
-- Esta base no tiene UPDATE ni DELETE en su contrato: el usuario de aplicacion
-- solo inserta y lee. Cualquier correccion se asienta como un evento nuevo.

-- ------------------------------------------------------------- audit_event
CREATE TABLE audit_event (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id          BINARY(16)      NOT NULL,
    organization_id    BINARY(16)      NOT NULL,

    -- Posicion del evento dentro de la cadena de su organizacion. Junto con
    -- record_hash permite detectar que a alguien le falta una entrada.
    sequence_number    BIGINT UNSIGNED NOT NULL,

    -- occurred_at lo declara el productor (cuando paso el hecho);
    -- recorded_at lo pone esta base (cuando quedo asentado). Separarlos deja
    -- visible el retraso de ingesta y detecta relojes desviados.
    occurred_at        DATETIME(6)     NOT NULL,
    recorded_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    service_name       VARCHAR(40)     NOT NULL,
    action             VARCHAR(60)     NOT NULL,
    category           VARCHAR(30)     NOT NULL,
    severity           VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    outcome            VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',

    actor_user_id      BINARY(16)      NULL,
    actor_display_name VARCHAR(160)    NULL,
    actor_roles        VARCHAR(200)    NULL,
    actor_ip           VARCHAR(45)     NULL,

    resource_type      VARCHAR(60)     NOT NULL,
    resource_public_id BINARY(16)      NULL,
    resource_label     VARCHAR(160)    NULL,

    -- Denormalizado a proposito: "quien vio la historia de este paciente" es la
    -- consulta que mas veces hace un auditor y no puede depender de un JOIN
    -- contra otra base.
    patient_public_id  BINARY(16)      NULL,

    correlation_id     VARCHAR(64)     NOT NULL,
    request_method     VARCHAR(10)     NULL,
    request_path       VARCHAR(255)    NULL,
    response_status    INT UNSIGNED    NULL,
    reason             VARCHAR(255)    NULL,

    -- Contexto adicional del productor como JSON serializado. Nunca contenido
    -- clinico ni datos personales: solo identificadores y nombres de campo.
    metadata_json      VARCHAR(2000)   NULL,

    -- Encadenamiento a prueba de manipulacion. record_hash = SHA-256 sobre la
    -- entrada canonica concatenada con previous_hash; el primer evento de una
    -- organizacion tiene previous_hash NULL.
    previous_hash      VARCHAR(64)     NULL,
    record_hash        VARCHAR(64)     NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_audit_event_public_id
        UNIQUE (public_id),
    CONSTRAINT uq_audit_event_organization_id_sequence_number
        UNIQUE (organization_id, sequence_number),
    CONSTRAINT uq_audit_event_record_hash
        UNIQUE (record_hash),
    CONSTRAINT ck_audit_event_category CHECK (
        category IN (
            'AUTHENTICATION','AUTHORIZATION','PATIENT_DATA','CLINICAL_RECORD',
            'DOCUMENT','SCHEDULING','BILLING','CONFIGURATION','SECURITY','SYSTEM'
        )
    ),
    CONSTRAINT ck_audit_event_severity CHECK (
        severity IN ('INFO','NOTICE','WARNING','CRITICAL')
    ),
    CONSTRAINT ck_audit_event_outcome CHECK (
        outcome IN ('SUCCESS','DENIED','FAILURE')
    ),
    CONSTRAINT ck_audit_event_sequence_number CHECK (sequence_number >= 1),
    CONSTRAINT ck_audit_event_response_status CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    )
) ENGINE = InnoDB;

-- Recorrido por defecto de la pantalla: los eventos del tenant, del mas
-- reciente al mas antiguo.
CREATE INDEX ix_audit_event_organization_id_occurred_at
    ON audit_event (organization_id, occurred_at DESC);

-- "Que hizo esta persona" y "quien toco a este paciente": las dos preguntas
-- que un auditor formula primero.
CREATE INDEX ix_audit_event_organization_id_actor_user_id_occurred_at
    ON audit_event (organization_id, actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_event_organization_id_patient_public_id_occurred_at
    ON audit_event (organization_id, patient_public_id, occurred_at DESC);

CREATE INDEX ix_audit_event_organization_id_category_occurred_at
    ON audit_event (organization_id, category, occurred_at DESC);
CREATE INDEX ix_audit_event_organization_id_resource_public_id
    ON audit_event (organization_id, resource_public_id);

-- Reconstruccion de un salto completo entre microservicios.
CREATE INDEX ix_audit_event_correlation_id
    ON audit_event (correlation_id);

-- -------------------------------------------------------- audit_chain_head
-- Punta de la cadena por organizacion. Existe para que el asiento de un evento
-- pueda tomar un bloqueo de fila corto (SELECT ... FOR UPDATE) y serializar el
-- calculo de sequence_number y previous_hash sin bloquear toda la tabla de
-- eventos ni depender de leer su ultimo registro.
CREATE TABLE audit_chain_head (
    organization_id      BINARY(16)      NOT NULL,
    last_sequence_number BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_record_hash     VARCHAR(64)     NULL,
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (organization_id)
) ENGINE = InnoDB;
