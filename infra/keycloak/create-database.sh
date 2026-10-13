#!/bin/sh
# IAM-01: crea el rol y la base «keycloak» en el Postgres de la plataforma si no existen,
# y deja la contraseña del rol igual a KEYCLOAK_DB_PASSWORD. Idempotente.
# La contraseña viaja como variable de psql (%L la cita), nunca concatenada en el SQL.
set -eu

psql -v ON_ERROR_STOP=1 -v kc_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
SELECT 'CREATE ROLE keycloak LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak')\gexec

SELECT format('ALTER ROLE keycloak WITH LOGIN PASSWORD %L', :'kc_password')\gexec

SELECT 'CREATE DATABASE keycloak OWNER keycloak'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'keycloak')\gexec
SQL

echo "Base keycloak lista."
