#!/bin/sh
# Rol y base de Keycloak dentro del Postgres de ms-agenda. Se ejecuta en cada arranque desde
# el servicio postgres-init; todas las sentencias son idempotentes.
set -e

psql -v ON_ERROR_STOP=1 -v kc_password="$KEYCLOAK_DB_PASSWORD" <<'EOSQL'
SELECT format('CREATE ROLE keycloak LOGIN PASSWORD %L', :'kc_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'keycloak')\gexec

-- Mantiene la contrasena alineada con KEYCLOAK_DB_PASSWORD si se cambia en .env.
SELECT format('ALTER ROLE keycloak PASSWORD %L', :'kc_password')\gexec

SELECT 'CREATE DATABASE keycloak OWNER keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
EOSQL

echo "Base keycloak lista"
