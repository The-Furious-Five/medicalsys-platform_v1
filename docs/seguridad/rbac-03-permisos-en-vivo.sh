#!/usr/bin/env bash
# Evidencia de RBAC-03 (SINS-26): cambiar los permisos de un rol desde la API de ms-identidad cambia
# el acceso real en historial, pacientes y facturacion, sin redesplegar ningun servicio.
#
# Quita a RECEPTIONIST los permisos de lectura de los tres servicios, comprueba el 403 con una sesion
# nueva y los devuelve. Los restaura aunque el script falle a mitad de camino; aun asi, no lo corras
# en el entorno compartido mientras alguien usa recepcion: pierde ese acceso unos segundos.
#
# Uso: BASE=http://localhost:8080 PA_PASSWORD=... DEMO_PASSWORD=... bash docs/seguridad/rbac-03-permisos-en-vivo.sh
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
PA_EMAIL=${PA_EMAIL:-platform.admin@medicalsys.demo}
RE_EMAIL=${RE_EMAIL:-recepcion@medicalsys.demo}
: "${PA_PASSWORD:?Falta PA_PASSWORD}" "${DEMO_PASSWORD:?Falta DEMO_PASSWORD}"
PY=$(command -v python3 || command -v python)
ROLE=RECEPTIONIST
PERMISSIONS=(patients.patient.read clinical.record.read billing.invoice.read)
ENDPOINTS=("/api/v1/patients/search?q=a" "/api/v1/expedientes" "/api/v1/facturas")

body() { "$PY" -c 'import json,sys;print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' "$1" "$2"; }
login() {
  curl -sS -X POST "$BASE/api/v1/identity/auth/login" -H 'Content-Type: application/json' -d "$(body "$1" "$2")" \
    | "$PY" -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])'
}
codes() {
  local token; token=$(login "$RE_EMAIL" "$DEMO_PASSWORD")
  for endpoint in "${ENDPOINTS[@]}"; do
    printf '%s ' "$(curl -s -o /dev/null -w '%{http_code}' "$BASE$endpoint" -H "Authorization: Bearer $token")"
  done
}
set_permissions() {
  curl -sS -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/v1/identity/roles/$ROLE/permissions" \
    -H "Authorization: Bearer $PA" -H 'Content-Type: application/json' -d "{\"permissions\": $1}"
}

PA=$(login "$PA_EMAIL" "$PA_PASSWORD")
ORIGINAL=$(curl -sS "$BASE/api/v1/identity/roles/$ROLE" -H "Authorization: Bearer $PA" \
  | "$PY" -c 'import json,sys;print(json.dumps([p["code"] for p in json.load(sys.stdin)["permissions"]]))')
WITHOUT=$(echo "$ORIGINAL" | "$PY" -c \
  'import json,sys;drop=set(sys.argv[1:]);print(json.dumps([p for p in json.load(sys.stdin) if p not in drop]))' \
  "${PERMISSIONS[@]}")
trap 'set_permissions "$ORIGINAL" > /dev/null' EXIT

echo "Rol $ROLE · permisos ${PERMISSIONS[*]}"
echo "Endpoints: ${ENDPOINTS[*]}"
before=$(codes);                          echo "1. ANTES, con los permisos:              $before"
echo "2. PLATFORM_ADMIN los quita por la API:   HTTP $(set_permissions "$WITHOUT")"
after=$(codes);                           echo "3. DESPUES, nuevo login, mismo despliegue: $after"
echo "4. PLATFORM_ADMIN los devuelve:            HTTP $(set_permissions "$ORIGINAL")"
again=$(codes);                           echo "5. OTRA VEZ, nuevo login:                 $again"

[ "$before" = "200 200 200 " ] && [ "$after" = "403 403 403 " ] && [ "$again" = "200 200 200 " ] \
  && echo "OK: el acceso sigue a los permisos del rol, sin redesplegar." \
  || { echo "FALLA: se esperaba 200 -> 403 -> 200 en los tres servicios"; exit 1; }
