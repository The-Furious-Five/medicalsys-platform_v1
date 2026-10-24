-- IAM-03: las credenciales pasan a Keycloak.
--
-- keycloak_user_id enlaza cada cuenta con su usuario del realm medicalsys. password_hash deja de
-- ser obligatorio: KeycloakAccountLinker importa el hash Argon2id existente a Keycloak y lo borra
-- de esta tabla, así que después de enlazar no queda una segunda copia de la credencial.
ALTER TABLE user_account
    ADD COLUMN keycloak_user_id CHAR(36) NULL AFTER public_id,
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    ADD CONSTRAINT uq_user_account_keycloak_user_id UNIQUE (keycloak_user_id);
