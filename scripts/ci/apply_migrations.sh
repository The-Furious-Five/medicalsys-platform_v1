#!/usr/bin/env bash
# Aplica desde una base vacia las migraciones de cada servicio indicado, en el mismo motor y con las
# mismas carpetas que el entorno compartido (db/migration y, si existe, db/demo). Una migracion con un
# error de sintaxis o que choca con una anterior falla aqui y no al desplegar.
#
# Uso: scripts/ci/apply_migrations.sh ms-identidad ms-agenda ...
# Necesita MySQL 8.4 y Postgres 17 accesibles; por defecto en 127.0.0.1 (servicios del job de CI).
#   MYSQL_HOST, MYSQL_PORT, MYSQL_PASSWORD   (usuario root)
#   PG_HOST, PG_PORT, PG_PASSWORD            (usuario postgres)
#   DOCKER_NETWORK                           red para el contenedor de Flyway (host en CI)
set -euo pipefail

FLYWAY_IMAGE=flyway/flyway:12.4.0
# El CLI de Flyway trae el driver de MariaDB, que conecta con otra collation que los servicios y da
# errores que en el despliegue no existen. Se usa el mismo Connector/J que fija Spring Boot.
MYSQL_DRIVER_VERSION=9.7.0
MYSQL_DRIVER_SHA256=0353648eaa1c91e0f4020c959abf756bc866ffd583df22ae6b6f6e0cbd43eb44
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-ci}
PG_HOST=${PG_HOST:-127.0.0.1}
PG_PORT=${PG_PORT:-5432}
PG_PASSWORD=${PG_PASSWORD:-ci}
DOCKER_NETWORK=${DOCKER_NETWORK:-host}

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

driver="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/mysql-connector-j-$MYSQL_DRIVER_VERSION.jar"
if [ ! -f "$driver" ] || [ "$(sha256 "$driver")" != "$MYSQL_DRIVER_SHA256" ]; then
  curl -sSfL -o "$driver" \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/$MYSQL_DRIVER_VERSION/mysql-connector-j-$MYSQL_DRIVER_VERSION.jar"
  [ "$(sha256 "$driver")" = "$MYSQL_DRIVER_SHA256" ] || { echo "El driver descargado no coincide con su SHA-256"; exit 1; }
fi

failed=()
for service in "$@"; do
  resources="$ROOT/services/$service/src/main/resources/db"
  [ -d "$resources/migration" ] || { echo "== $service: sin migraciones"; continue; }

  locations=filesystem:/flyway/sql/migration
  [ -d "$resources/demo" ] && locations="$locations,filesystem:/flyway/sql/demo"

  database="ci_${service//-/_}"
  if grep -q "flyway-database-postgresql" "$ROOT/services/$service/pom.xml"; then
    # Como en el entorno compartido, el esquema public de una base propia: en CI, la del contenedor.
    url="jdbc:postgresql://$PG_HOST:$PG_PORT/postgres"
    credentials=(-user=postgres "-password=$PG_PASSWORD")
  else
    url="jdbc:mysql://$MYSQL_HOST:$MYSQL_PORT/$database?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
    credentials=(-user=root "-password=$MYSQL_PASSWORD")
  fi

  echo "== $service ($locations)"
  if docker run --rm --network "$DOCKER_NETWORK" -v "$resources:/flyway/sql:ro" \
      -v "$driver:/flyway/drivers/mysql-connector-j.jar:ro" "$FLYWAY_IMAGE" \
      "-url=$url" "${credentials[@]}" "-locations=$locations" -validateMigrationNaming=true \
      -cleanDisabled=true migrate; then
    echo "   ok"
  else
    failed+=("$service")
  fi
done

if [ ${#failed[@]} -gt 0 ]; then
  echo "Migraciones que no se aplican desde cero: ${failed[*]}"
  exit 1
fi
