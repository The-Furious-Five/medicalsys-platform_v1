-- Las contrasenas pasan a Keycloak (realm medicalsys). Una cuenta migrada ya no guarda hash
-- local: password_hash queda en NULL y hash_algorithm = 'keycloak'.
ALTER TABLE user_account MODIFY password_hash VARCHAR(255) NULL;
