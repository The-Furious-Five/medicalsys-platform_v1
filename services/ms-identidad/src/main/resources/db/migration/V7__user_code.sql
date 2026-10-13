-- IAM-04: User ID estándar MS-AA-NNNNN (hoja «Decisiones» de C-01).
--
-- MS = MedicalSys, AA = dos últimos dígitos del año de alta, NNNNN = correlativo de cinco dígitos.
-- No incluye rol ni datos personales, es único y no cambia nunca (aunque cambien rol, nombre o correo).
-- El correlativo sale de user_code_sequence: AUTO_INCREMENT de InnoDB no reutiliza números, así que
-- un código tampoco se reutiliza aunque la cuenta se dé de baja.

CREATE TABLE user_code_sequence (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT UNSIGNED NULL,
    issued_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE user_account ADD COLUMN user_code VARCHAR(16) NULL AFTER public_id;

-- Cuentas existentes: código por orden de alta, con el año de su created_at.
INSERT INTO user_code_sequence (user_account_id, issued_at)
SELECT id, created_at FROM user_account ORDER BY created_at, id;

UPDATE user_account u
JOIN user_code_sequence s ON s.user_account_id = u.id
SET u.user_code = CONCAT('MS-', DATE_FORMAT(u.created_at, '%y'), '-', LPAD(s.id, 5, '0'));

-- INSERT ... SELECT reserva autoincrementales en bloques y deja huecos; el próximo código sigue al último.
ALTER TABLE user_code_sequence AUTO_INCREMENT = 1;

ALTER TABLE user_account
    MODIFY COLUMN user_code VARCHAR(16) NOT NULL,
    ADD CONSTRAINT uq_user_account_user_code UNIQUE (user_code),
    ADD CONSTRAINT ck_user_account_user_code CHECK (user_code REGEXP '^MS-[0-9]{2}-[0-9]{5,}$');

-- Inmutable: la entidad mapea user_code con updatable = false (Hibernate nunca lo incluye en un UPDATE)
-- y ninguna operación de la API lo modifica. Un trigger como el de ms-historial lo reforzaría en la base,
-- pero CREATE TRIGGER exige --log-bin-trust-function-creators=ON en el servidor (error 1419); se agrega
-- cuando esa bandera esté confirmada en Aiven, para no arriesgar el arranque del servicio de identidad.
