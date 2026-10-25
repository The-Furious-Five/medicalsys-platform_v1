-- IAM-07: nombre de usuario estándar para iniciar sesión y segundo factor TOTP obligatorio.
-- Reglas y fundamentos en docs/seguridad/IAM-07-usuario-y-segundo-factor.md.
--
-- username: nombre.apellido[.inicial del segundo apellido][n], en minúsculas y sin tildes. Lo genera
-- ms-identidad al crear la cuenta a partir de sus nombres y no cambia nunca, aunque cambien el nombre
-- o el correo. Las cuentas existentes lo reciben al arrancar (UsernameBackfill), porque quitar tildes y
-- partículas de un nombre no se puede hacer bien en SQL. Por eso la columna nace nula.
ALTER TABLE user_account
    ADD COLUMN username VARCHAR(40) NULL AFTER user_code,
    ADD CONSTRAINT uq_user_account_username UNIQUE (username),
    ADD CONSTRAINT ck_user_account_username CHECK (username REGEXP '^[a-z][a-z0-9.]{2,39}$');

-- TOTP (RFC 6238): mfa_enabled y encrypted_mfa_secret ya existían desde V1, sin uso. El último paso
-- de 30 s aceptado impide reutilizar un código mientras sigue vigente (OWASP ASVS 2.8.4).
ALTER TABLE user_account
    ADD COLUMN mfa_last_used_step BIGINT NULL AFTER encrypted_mfa_secret,
    ADD COLUMN mfa_enrolled_at    TIMESTAMP NULL AFTER mfa_last_used_step;
