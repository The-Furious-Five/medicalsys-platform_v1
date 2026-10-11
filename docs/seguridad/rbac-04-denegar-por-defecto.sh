#!/usr/bin/env bash
# Evidencia de RBAC-04 (SINS-42): los servicios que no tenian control ahora exigen identidad y permiso,
# y lo que no se declara se deniega. Todo pasa por el gateway, como una solicitud real.
#
# Para cada endpoint comprueba: 401 sin token, 403 con un rol sin el permiso y 200 con el permiso.
# Ningun rol de personal carece de los permisos de catalogos, asi que para ese caso PLATFORM_ADMIN le
# quita catalogs.icd10.read a BILLING_CLERK por la API y se lo devuelve al terminar, aunque el script
# falle a mitad de camino. No lo corras en el entorno compartido mientras alguien usa caja.
#
# Uso: BASE=http://localhost:8080 PA_PASSWORD=... DEMO_PASSWORD=... bash docs/seguridad/rbac-04-denegar-por-defecto.sh
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
PA_EMAIL=${PA_EMAIL:-platform.admin@medicalsys.demo}
: "${PA_PASSWORD:?Falta PA_PASSWORD}" "${DEMO_PASSWORD:?Falta DEMO_PASSWORD}"
PY=$(command -v python3 || command -v python)

body() { "$PY" -c 'import json,sys;print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' "$1" "$2"; }
login() {
  curl -sS -X POST "$BASE/api/v1/identity/auth/login" -H 'Content-Type: application/json' -d "$(body "$1" "$2")" \
    | "$PY" -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])'
}
status() { # status <token|-> <ruta>
  if [ "$1" = "-" ]; then curl -s -o /dev/null -w '%{http_code}' "$BASE$2"
  else curl -s -o /dev/null -w '%{http_code}' "$BASE$2" -H "Authorization: Bearer $1"; fi
}
set_permissions() {
  curl -sS -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/v1/identity/roles/$1/permissions" \
    -H "Authorization: Bearer $PA" -H 'Content-Type: application/json' -d "{\"permissions\": $2}"
}

PA=$(login "$PA_EMAIL" "$PA_PASSWORD")
ORIGINAL=$(curl -sS "$BASE/api/v1/identity/roles/BILLING_CLERK" -H "Authorization: Bearer $PA" \
  | "$PY" -c 'import json,sys;print(json.dumps([p["code"] for p in json.load(sys.stdin)["permissions"]]))')
WITHOUT=$(echo "$ORIGINAL" | "$PY" -c \
  'import json,sys;print(json.dumps([p for p in json.load(sys.stdin) if p != "catalogs.icd10.read"]))')
trap 'set_permissions BILLING_CLERK "$ORIGINAL" > /dev/null' EXIT

RECEPCION=$(login recepcion@medicalsys.demo "$DEMO_PASSWORD")
ADMIN=$(login admin@medicalsys.demo "$DEMO_PASSWORD")
CAJA=$(login caja@medicalsys.demo "$DEMO_PASSWORD")

failures=0
row() { # row <servicio> <ruta> <token sin permiso> <rol sin permiso> <token con permiso> <rol con permiso>
  local none denied granted
  none=$(status - "$2"); denied=$(status "$3" "$2"); granted=$(status "$5" "$2")
  printf '%-15s %-45s %-10s %-20s %s\n' "$1" "GET $2" "$none" "$denied ($4)" "$granted ($6)"
  [ "$none" = 401 ] && [ "$denied" = 403 ] && [ "$granted" = 200 ] || failures=$((failures + 1))
}

printf '%-15s %-45s %-10s %-20s %s\n' SERVICIO ENDPOINT "SIN TOKEN" "SIN PERMISO" "CON PERMISO"
row agenda /api/v1/scheduling/resources "$CAJA" BILLING_CLERK "$RECEPCION" RECEPTIONIST
row profesionales /api/v1/professionals "$CAJA" BILLING_CLERK "$RECEPCION" RECEPTIONIST
row notificaciones /api/v1/notifications/rules "$RECEPCION" RECEPTIONIST "$ADMIN" ADMINISTRATOR
row marketing /api/v1/marketing/criterios-permitidos "$CAJA" BILLING_CLERK "$RECEPCION" RECEPTIONIST

# Catalogos: BILLING_CLERK pierde catalogs.icd10.read; su sesion se cierra y la nueva ya no lo trae.
set_permissions BILLING_CLERK "$WITHOUT" > /dev/null
CAJA_SIN=$(login caja@medicalsys.demo "$DEMO_PASSWORD")
row catalogos "/api/v1/catalogs/cie10?q=J00" "$CAJA_SIN" "BILLING_CLERK*" "$RECEPCION" RECEPTIONIST
set_permissions BILLING_CLERK "$ORIGINAL" > /dev/null

# ms-identidad deniega por defecto: una ruta que nadie declaro no se atiende ni con sesion.
none=$(status - /api/v1/identity/no-declarada); admin=$(status "$ADMIN" /api/v1/identity/no-declarada)
printf '%-15s %-45s %-10s %-20s %s\n' identidad "GET /api/v1/identity/no-declarada" "$none" "$admin (ADMINISTRATOR)" "-"
[ "$none" = 401 ] && [ "$admin" = 403 ] || failures=$((failures + 1))

# Lo publico sigue publico: la comprobacion de estado no pide token.
ping=$(status - /api/v1/scheduling/ping)
printf '%-15s %-45s %s\n' publico "GET /api/v1/scheduling/ping" "$ping (@PublicEndpoint)"
[ "$ping" = 200 ] || failures=$((failures + 1))

echo "* BILLING_CLERK sin catalogs.icd10.read durante la prueba; el permiso ya fue restaurado."
[ "$failures" = 0 ] && echo "OK: 401 sin token, 403 sin el permiso y 200 con el permiso en cada servicio." \
  || { echo "FALLA: $failures comprobaciones no dieron lo esperado"; exit 1; }
