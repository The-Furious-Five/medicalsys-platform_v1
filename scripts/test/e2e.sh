#!/usr/bin/env bash
# Prueba de integracion de punta a punta sobre el stack levantado con docker compose.
#
# Todo pasa por el gateway desde la red interna medicalsys-interna (como en el VPS), dentro de un
# contenedor con curl y jq: no depende de puertos publicados ni de herramientas del host.
#
#   docker compose up -d --wait
#   bash scripts/test/e2e.sh
#   ADMIN_PASSWORD='...' bash scripts/test/e2e.sh      # si la cuenta demo tiene otra contrasena
#   SKIP_IAM=1 bash scripts/test/e2e.sh                # sin los recorridos largos de IAM-01..05
#
# Cubre: salud del stack, HARD-01 (solo gateway y Keycloak publican puerto), HARD-04 (cabeceras),
# suplantacion de X-User-Id/X-Roles, login y JWT, RBAC por modulo, catalogos (400 en parametros),
# LOG-01 (el login llega a la bitacora) e IAM-01..05 con los scripts de cada tarea.
# Termina con codigo distinto de 0 si algo falla.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
env_value() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2-; }
host_path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

failures=0
ok()   { printf '  OK    %s\n' "$1"; }
fail() { printf '  FALLA %s\n' "$1"; failures=$((failures + 1)); }

echo "== Stack"
unhealthy="$(docker compose ps --format '{{.Service}} {{.Status}}' | grep -vE 'healthy|^ms-notificaciones Up' || true)"
[ -z "$unhealthy" ] && ok "todos los servicios en ejecucion estan healthy" || fail "servicios no sanos: $unhealthy"

published="$(docker compose ps --format '{{.Service}} {{.Ports}}' | grep -- '->' | awk '{print $1}' | sort | tr '\n' ' ')"
[ "$published" = "keycloak ms-gateway " ] && ok "HARD-01: solo ms-gateway y keycloak publican puerto" \
  || fail "HARD-01: publican puerto: $published"

ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(env_value IDENTITY_DEMO_PASSWORD)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SanGabriel.Demo26!}"

MSYS_NO_PATHCONV=1 docker run --rm -i --network medicalsys-interna \
  -v "$(host_path "$ROOT"):/repo:ro" -w /repo \
  -e G=http://ms-gateway:8080 -e KC_URL=http://keycloak:8080 \
  -e ADMIN_PASSWORD="$ADMIN_PASSWORD" -e SKIP_IAM="${SKIP_IAM:-}" \
  -e KEYCLOAK_ADMIN_USER="$(env_value KEYCLOAK_ADMIN_USER)" \
  -e KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)" \
  -e KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET="$(env_value KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET)" \
  alpine:3.22 sh -c 'apk add -q --no-cache bash curl jq openssl >/dev/null && bash -s' <<'INNER'
set -uo pipefail
failures=0
ok()   { printf '  OK    %s\n' "$1"; }
fail() { printf '  FALLA %s\n' "$1"; failures=$((failures + 1)); }
expect() { [ "$3" = "$2" ] && ok "$1 -> $3" || fail "$1 -> $3 (esperado $2)"; }
code() { curl -s -m 20 -o /dev/null -w '%{http_code}' "$@"; }
J='Content-Type: application/json'

echo "== Gateway"
expect "sin token, /api/v1/patients/search" 401 "$(code "$G/api/v1/patients/search?q=a")"
expect "X-User-Id/X-Roles falsos sin token" 401 "$(code -H 'X-User-Id: 00000000-0000-4000-8000-000000000901' \
  -H 'X-Roles: ADMINISTRATOR' -H 'X-Permissions: patients.patient.read' "$G/api/v1/patients/search?q=a")"
headers="$(curl -s -m 20 -D - -o /dev/null "$G/api/v1/patients/search?q=a" | tr -d '\r')"
for h in 'X-Content-Type-Options: nosniff' 'X-Frame-Options: DENY' 'Strict-Transport-Security' 'Content-Security-Policy'; do
  grep -qi "^$h" <<<"$headers" && ok "HARD-04: cabecera $h" || fail "HARD-04: falta la cabecera $h"
done

echo "== Login (IAM-03) y bitacora (LOG-01)"
login="$(curl -s -m 30 "$G/api/v1/identity/auth/login" -H "$J" \
  -d "$(jq -n --arg p "$ADMIN_PASSWORD" '{email: "admin@medicalsys.demo", password: $p}')")"
TOKEN="$(jq -r '.accessToken // empty' <<<"$login")"
if [ -n "$TOKEN" ]; then ok "login del administrador demo"; else fail "login del administrador demo: $(head -c 200 <<<"$login")"; fi
expect "contrasena incorrecta" 401 "$(code "$G/api/v1/identity/auth/login" -H "$J" \
  -d '{"email":"nadie@medicalsys.demo","password":"no-existe-123"}')"
AUTH="Authorization: Bearer $TOKEN"
sleep 3
events="$(curl -s -m 20 -H "$AUTH" "$G/api/v1/audit/events?action=LOGIN_SUCCESS&serviceName=ms-identidad&size=5")"
n="$(jq -r '.totalElements // (.content | length) // 0' <<<"$events" 2>/dev/null || echo 0)"
[ "${n:-0}" -gt 0 ] && ok "LOG-01: el LOGIN_SUCCESS de ms-identidad esta en la bitacora ($n)" \
  || fail "LOG-01: no hay LOGIN_SUCCESS de ms-identidad en la bitacora: $(head -c 200 <<<"$events")"
failed_events="$(curl -s -m 20 -H "$AUTH" "$G/api/v1/audit/events?action=LOGIN_FAILED&size=5" | jq -r '.totalElements // 0')"
[ "${failed_events:-0}" -gt 0 ] && ok "LOG-01: los LOGIN_FAILED tambien llegan ($failed_events)" || fail "LOG-01: no hay LOGIN_FAILED"

echo "== Modulos con el JWT (RBAC)"
for p in /api/v1/identity/auth/me /api/v1/identity/users /api/v1/tenancy/organizacion "/api/v1/patients/search?q=a" \
         /api/v1/professionals /api/v1/scheduling/appointments /api/v1/expedientes /api/v1/encuentros /api/v1/facturas \
         /api/v1/campanas /api/v1/notifications/templates /api/v1/audit/events /api/v1/documents \
         "/api/v1/catalogs/tax?type=PAYMENT_METHOD_TYPE"; do
  expect "GET $p" 200 "$(code -H "$AUTH" "$G$p")"
done
expect "catalogos: falta ?type" 400 "$(code -H "$AUTH" "$G/api/v1/catalogs/tax")"
expect "catalogos: ?type invalido" 400 "$(code -H "$AUTH" "$G/api/v1/catalogs/tax?type=NO_EXISTE")"
expect "CRY-02: integridad de un documento inexistente" 404 \
  "$(code -H "$AUTH" "$G/api/v1/documents/00000000-0000-4000-8000-00000000dead/integrity")"

if [ -z "${SKIP_IAM:-}" ]; then
  for step in "IAM-01|infra/keycloak/smoke-token.sh" "IAM-02|infra/keycloak/policy-check.sh" \
              "IAM-03|services/ms-identidad/scripts/login-keycloak.sh" \
              "IAM-04|services/ms-identidad/scripts/abm-keycloak.sh" \
              "IAM-05|services/ms-identidad/scripts/desbloqueo-reinicio.sh"; do
    name="${step%%|*}"; script="${step#*|}"
    echo "== $name ($script)"
    out="$(bash "$script" "$G" 2>&1)"; rc=$?
    if [ $rc -eq 0 ] && ! grep -q 'FALLA' <<<"$out"; then ok "$name"; else fail "$name"; tail -15 <<<"$out" | sed 's/^/        /'; fi
  done

  # LOG-02: los recorridos de IAM-03..05 hicieron ABM, bloqueos, desbloqueos y reinicios reales.
  echo "== LOG-02: eventos de ABM y contrasenas en la bitacora"
  sleep 3
  for action in USER_CREATED USER_UPDATED USER_DISABLED ACCOUNT_LOCKED ACCOUNT_UNLOCKED PASSWORD_RESET PASSWORD_CHANGED; do
    n="$(curl -s -m 20 -H "$AUTH" "$G/api/v1/audit/events?action=$action&serviceName=ms-identidad&size=1" | jq -r '.totalElements // 0')"
    [ "${n:-0}" -gt 0 ] && ok "LOG-02: $action ($n)" || fail "LOG-02: no hay eventos $action"
  done
  leaked="$(curl -s -m 20 -H "$AUTH" "$G/api/v1/audit/events?serviceName=ms-identidad&size=200" | grep -c -E 'Clave|clave [a-f0-9]{6} ok|Prueba valida' || true)"
  [ "${leaked:-0}" -eq 0 ] && ok "LOG-02: ninguna contrasena de las pruebas aparece en la bitacora"     || fail "LOG-02: la bitacora contiene contrasenas"
fi

echo "== Resultado"
[ $failures -eq 0 ] && echo "  Todo en verde." || echo "  $failures verificaciones fallaron."
exit $failures
INNER
inner=$?
total=$((failures + inner))
[ $total -eq 0 ] && echo "E2E OK" || echo "E2E con $total fallas"
exit $total
