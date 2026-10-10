-- RBAC-02 (SINS-25) - Gestion de roles desde la API: alta, baja logica, edicion de permisos y
-- pares de roles incompatibles (segregacion de funciones).

-- Los roles dejan de ser una lista cerrada: el codigo solo exige el formato. La 'c' de
-- REGEXP_LIKE fuerza la comparacion sensible a mayusculas, porque la intercalacion de la tabla
-- no lo es y aceptaria 'auditor'.
ALTER TABLE `role` DROP CHECK ck_role_code;
ALTER TABLE `role` ADD CONSTRAINT ck_role_code CHECK (REGEXP_LIKE(code, '^[A-Z][A-Z0-9_]{2,29}$', 'c'));

-- Baja logica: un rol desactivado no se puede asignar ni aporta roles o permisos al token, pero
-- sus asignaciones se conservan para la trazabilidad y para poder reactivarlo.
ALTER TABLE `role` ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER is_system;

-- Pares de roles que una misma cuenta no puede tener a la vez. Cada par se guarda una sola vez,
-- con el id menor primero, para que (A, B) y (B, A) no puedan coexistir.
CREATE TABLE role_incompatibility (
    id                   BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    role_id              SMALLINT UNSIGNED NOT NULL,
    incompatible_role_id SMALLINT UNSIGNED NOT NULL,
    reason               VARCHAR(300)      NOT NULL,
    created_by_user_id   BIGINT UNSIGNED   NULL,
    created_at           TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_role_incompatibility_pair UNIQUE (role_id, incompatible_role_id),
    CONSTRAINT fk_role_incompatibility_role FOREIGN KEY (role_id) REFERENCES `role` (id),
    CONSTRAINT fk_role_incompatibility_incompatible_role FOREIGN KEY (incompatible_role_id) REFERENCES `role` (id),
    CONSTRAINT fk_role_incompatibility_created_by FOREIGN KEY (created_by_user_id) REFERENCES user_account (id),
    CONSTRAINT ck_role_incompatibility_order CHECK (role_id < incompatible_role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_role_incompatibility_incompatible_role_id ON role_incompatibility (incompatible_role_id);
CREATE INDEX ix_role_incompatibility_created_by_user_id ON role_incompatibility (created_by_user_id);

-- Delegacion administrativa: que roles puede asignar o revocar quien tiene cada rol. Un rol de
-- plataforma no la necesita, porque gestiona cualquier rol; uno de organizacion solo asigna los
-- roles que figuran aqui, y la lista se edita con PUT /roles/{code}/assignable-roles.
CREATE TABLE role_delegation (
    role_id            SMALLINT UNSIGNED NOT NULL,
    assignable_role_id SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, assignable_role_id),
    CONSTRAINT fk_role_delegation_role FOREIGN KEY (role_id) REFERENCES `role` (id),
    CONSTRAINT fk_role_delegation_assignable_role FOREIGN KEY (assignable_role_id) REFERENCES `role` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_role_delegation_assignable_role_id ON role_delegation (assignable_role_id);

-- El administrador de un centro da de alta a su personal: los roles de organizacion del seed.
INSERT INTO role_delegation (role_id, assignable_role_id)
SELECT grantor.id, assignable.id FROM `role` grantor JOIN `role` assignable
WHERE grantor.code = 'ADMINISTRATOR'
  AND assignable.code IN ('ADMINISTRATOR', 'PHYSICIAN', 'RECEPTIONIST', 'BILLING_CLERK', 'PATIENT');
