-- ms-pacientes · patients_db · acceso cruzado entre tenants, autorizado por el paciente.
--
-- El paciente es global (una sola fila para toda la plataforma), asi que hoy cualquier tenant ve su
-- ficha completa sin ninguna restriccion. registered_by_organization_public_id marca que
-- organizacion lo registro primero (esa siempre ve todo); cualquier otra necesita un
-- patient_access_grant en AUTHORIZED, que solo se logra con el codigo OTP que le llega al propio
-- paciente por WhatsApp -- no lo activa la clinica, como sigue pasando con
-- medical_record.shares_with_network en ms-historial (ese switch queda intacto, es otra cosa).

ALTER TABLE patient
    ADD COLUMN registered_by_organization_public_id BINARY(16) NULL AFTER user_account_public_id;

CREATE TABLE patient_access_grant (
    id                                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                           BINARY(16)      NOT NULL,
    patient_id                          BIGINT UNSIGNED NOT NULL,
    requesting_organization_public_id   BINARY(16)      NOT NULL,
    status                              VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED',
    requested_by_user_public_id         BINARY(16)      NULL,
    requested_at                        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    otp_code_hash                       CHAR(64)        NOT NULL,
    otp_expires_at                      TIMESTAMP(6)    NOT NULL,
    otp_attempts                        SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    authorized_at                       TIMESTAMP(6)    NULL,
    revoked_at                          TIMESTAMP(6)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_patient_access_grant_public_id (public_id),
    CONSTRAINT fk_patient_access_grant_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT ck_patient_access_grant_status
        CHECK (status IN ('REQUESTED', 'AUTHORIZED', 'REVOKED', 'EXPIRED')),
    KEY ix_patient_access_grant_patient_status (patient_id, status),
    KEY ix_patient_access_grant_patient_org (patient_id, requesting_organization_public_id)
) ENGINE=InnoDB;
