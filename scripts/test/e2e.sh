#!/usr/bin/env bash
# Prueba de integracion de punta a punta sobre el stack levantado con docker compose.
#
# Todo pasa por el gateway desde la red interna medicalsys-interna (como en el VPS), dentro de un
# contenedor con curl y jq: no depende de puertos publicados ni de herramientas del host.
#
#   docker compose up -d --wait
#   bash scripts/test/e2e.sh
#   ADMIN_PASSWORD='...' bash scripts/test/e2e.sh      # si las cuentas demo tienen otra contrasena
#   SKIP_IAM=1 bash scripts/test/e2e.sh                # sin los recorridos largos de IAM-01..05
#
# Cada rol prueba lo suyo (ISO-01): el administrador sus modulos, el medico lo clinico y el OSI la
# bitacora, los roles y los niveles MAC. Necesita la cuenta OSI del .env (OSI_SEED_ENABLED,
# OSI_EMAIL, OSI_PASSWORD) para la bitacora y RBAC-02.
#
# Cubre: salud del stack, HARD-01 (solo gateway y Keycloak publican puerto), HARD-04 (cabeceras),
# suplantacion de cabeceras, login y JWT, RBAC por rol y segregacion de funciones (ISO-01), catalogos,
# LOG-01 (login e IP real en la bitacora), LOG-02 (ABM, roles, MAC y 403 de todos los servicios),
# RBAC-02 + MAC de punta a punta e IAM-01..05 con los scripts de cada tarea.
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
  -e OSI_EMAIL="$(env_value OSI_EMAIL)" -e OSI_PASSWORD="$(env_value OSI_PASSWORD)" \
  alpine:3.22 sh -c 'apk add -q --no-cache bash curl jq openssl >/dev/null && bash -s' <<'INNER'
set -uo pipefail
failures=0
ok()   { printf '  OK    %s\n' "$1"; }
fail() { printf '  FALLA %s\n' "$1"; failures=$((failures + 1)); }
expect() { [ "$3" = "$2" ] && ok "$1 -> $3" || fail "$1 -> $3 (esperado $2)"; }
code() { curl -s -m 20 -o /dev/null -w '%{http_code}' "$@"; }
J='Content-Type: application/json'
ORG=00000000-0000-4000-8000-0000000000a1
login_token() {
  curl -s -m 30 "$G/api/v1/identity/auth/login" -H "$J" "${@:3}" \
    -d "$(jq -n --arg e "$1" --arg p "$2" '{email: $e, password: $p}')" | jq -r '.accessToken // empty'
}
audit_count() {
  curl -s -m 20 -H "$OSI" "$G/api/v1/audit/events?$1&size=1" | jq -r '.totalElements // 0'
}

echo "== Gateway"
expect "sin token, /api/v1/patients/search" 401 "$(code "$G/api/v1/patients/search?q=a")"
expect "X-User-Id/X-Roles falsos sin token" 401 "$(code -H 'X-User-Id: 00000000-0000-4000-8000-000000000901' \
  -H 'X-Roles: ADMINISTRATOR' -H 'X-Permissions: patients.patient.read' "$G/api/v1/patients/search?q=a")"
headers="$(curl -s -m 20 -D - -o /dev/null "$G/api/v1/patients/search?q=a" | tr -d '\r')"
for h in 'X-Content-Type-Options: nosniff' 'X-Frame-Options: DENY' 'Strict-Transport-Security' 'Content-Security-Policy'; do
  grep -qi "^$h" <<<"$headers" && ok "HARD-04: cabecera $h" || fail "HARD-04: falta la cabecera $h"
done

echo "== Login de cada rol (IAM-03)"
ADMIN_TOKEN="$(login_token admin@medicalsys.demo "$ADMIN_PASSWORD")"
DOCTOR_TOKEN="$(login_token doctor@medicalsys.demo "$ADMIN_PASSWORD")"
OSI_TOKEN="$(login_token "${OSI_EMAIL:-}" "${OSI_PASSWORD:-}")"
[ -n "$ADMIN_TOKEN" ] && ok "administrador del centro" || fail "login del administrador demo"
[ -n "$DOCTOR_TOKEN" ] && ok "profesional de salud" || fail "login del medico demo"
[ -n "$OSI_TOKEN" ] && ok "OSI (security officer)" || fail "login del OSI: falta OSI_SEED_ENABLED/OSI_PASSWORD en .env"
ADMIN="Authorization: Bearer $ADMIN_TOKEN"; DOCTOR="Authorization: Bearer $DOCTOR_TOKEN"; OSI="Authorization: Bearer $OSI_TOKEN"

echo "== Modulos por rol (RBAC) y segregacion de funciones (ISO-01)"
for p in /api/v1/identity/auth/me /api/v1/identity/users /api/v1/tenancy/organizacion "/api/v1/patients/search?q=a" \
         /api/v1/professionals /api/v1/scheduling/appointments /api/v1/facturas /api/v1/notifications/templates \
         "/api/v1/catalogs/tax?type=PAYMENT_METHOD_TYPE"; do
  expect "administrador: GET $p" 200 "$(code -H "$ADMIN" "$G$p")"
done
for p in /api/v1/expedientes /api/v1/encuentros /api/v1/documents; do
  expect "medico: GET $p" 200 "$(code -H "$DOCTOR" "$G$p")"
done
expect "OSI: GET /api/v1/audit/events" 200 "$(code -H "$OSI" "$G/api/v1/audit/events")"
expect "ISO-01: el administrador ya no lee la bitacora" 403 "$(code -H "$ADMIN" "$G/api/v1/audit/events")"
expect "ISO-01: el administrador ya no lee la historia clinica" 403 "$(code -H "$ADMIN" "$G/api/v1/expedientes")"
expect "ISO-01: el administrador ya no gestiona roles" 403 "$(code -X POST -H "$ADMIN" -H "$J" "$G/api/v1/identity/roles" \
  -d '{"code":"NO_DEBE_EXISTIR","name":"x","scope":"ORGANIZATION"}')"
expect "catalogos: falta ?type" 400 "$(code -H "$ADMIN" "$G/api/v1/catalogs/tax")"
expect "catalogos: ?type invalido" 400 "$(code -H "$ADMIN" "$G/api/v1/catalogs/tax?type=NO_EXISTE")"
expect "CRY-02: integridad de un documento inexistente" 404 \
  "$(code -H "$DOCTOR" "$G/api/v1/documents/00000000-0000-4000-8000-00000000dead/integrity")"

echo "== LOG-01: autenticacion e IP real en la bitacora"
before="$(audit_count 'action=LOGIN_FAILED')"
expect "correo inexistente" 401 "$(code "$G/api/v1/identity/auth/login" -H "$J" \
  -d '{"email":"nadie@medicalsys.demo","password":"no-existe-123"}')"
login_token admin@medicalsys.demo "$ADMIN_PASSWORD" -H 'X-Forwarded-For: 203.0.113.50' >/dev/null
sleep 3
after="$(audit_count 'action=LOGIN_FAILED')"
[ "${after:-0}" -gt "${before:-0}" ] && ok "el intento con un correo inexistente quedo como LOGIN_FAILED" \
  || fail "el intento con un correo inexistente no llego a la bitacora ($before -> $after)"
ip="$(curl -s -m 20 -H "$OSI" "$G/api/v1/audit/events?action=LOGIN_SUCCESS&size=1" | jq -r '.content[0].actorIp // empty')"
[ "$ip" = "203.0.113.50" ] && ok "detras de un proxy interno se guarda la IP del cliente ($ip)" \
  || fail "se guardo '$ip' en vez de 203.0.113.50 (el gateway es $(getent hosts ms-gateway | awk '{print $1}'))"

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
    n="$(audit_count "action=$action&serviceName=ms-identidad")"
    [ "${n:-0}" -gt 0 ] && ok "$action ($n)" || fail "no hay eventos $action"
  done
  leaked="$(curl -s -m 20 -H "$OSI" "$G/api/v1/audit/events?serviceName=ms-identidad&size=200" | grep -c -E 'Clave|clave [a-f0-9]{6} ok|Prueba valida' || true)"
  [ "${leaked:-0}" -eq 0 ] && ok "ninguna contrasena de las pruebas aparece en la bitacora" \
    || fail "la bitacora contiene contrasenas"
fi

echo "== RBAC-02 + MAC (ISO-01): el OSI crea un rol, una cuenta y le fija el nivel"
role="E2E_$(date +%s)"; email="e2e.$(date +%s)@medicalsys.demo"; pass="E2e prueba $(openssl rand -hex 4) Ok9!"
expect "OSI crea el rol $role" 201 "$(code -X POST "$G/api/v1/identity/roles" -H "$OSI" -H "$J" \
  -d "{\"code\":\"$role\",\"name\":\"Rol e2e\",\"scope\":\"ORGANIZATION\"}")"
expect "OSI le otorga solo audit.log.read (CONFIDENTIAL)" 200 "$(code -X PUT "$G/api/v1/identity/roles/$role/permissions" \
  -H "$OSI" -H "$J" -d '{"permissions":["audit.log.read"]}')"
created="$(curl -s -m 30 -X POST "$G/api/v1/identity/users" -H "$OSI" -H "$J" \
  -d "$(jq -n --arg e "$email" --arg r "$role" --arg o "$ORG" '{email: $e, fullName: "Prueba E2E Rol", activateImmediately: true, organizationPublicId: $o, roles: [{role: $r}]}')")"
temp="$(jq -r '.temporaryPassword // empty' <<<"$created")"; account="$(jq -r '.publicId // empty' <<<"$created")"
[ -n "$temp" ] && ok "alta de una cuenta con el rol nuevo" || fail "alta con el rol nuevo: $(head -c 200 <<<"$created")"
expect "cambio de la contrasena temporal" 200 "$(code -X POST "$G/api/v1/identity/auth/change-password" -H "$J" \
  -d "$(jq -n --arg e "$email" --arg c "$temp" --arg n "$pass" '{email: $e, currentPassword: $c, newPassword: $n}')")"
U="Authorization: Bearer $(login_token "$email" "$pass")"
expect "MAC: con nivel INTERNAL el permiso CONFIDENTIAL no entra al token" 403 "$(code -H "$U" "$G/api/v1/audit/events")"
expect "OSI sube el nivel a CONFIDENTIAL" 200 "$(code -X PUT "$G/api/v1/identity/users/$account/clearance" -H "$OSI" -H "$J" \
  -d '{"clearance":"CONFIDENTIAL","reason":"Prueba e2e de MAC"}')"
U="Authorization: Bearer $(login_token "$email" "$pass")"
expect "con nivel CONFIDENTIAL: bitacora" 200 "$(code -H "$U" "$G/api/v1/audit/events")"
expect "con el rol nuevo: pacientes (sin permiso)" 403 "$(code -H "$U" "$G/api/v1/patients/search?q=a")"
sleep 3
echo "== LOG-02: roles, nivel MAC y 403 de los demas servicios"
for action in ROLE_CREATED ROLE_PERMISSIONS_CHANGED CLEARANCE_CHANGED; do
  n="$(audit_count "action=$action")"
  [ "${n:-0}" -gt 0 ] && ok "$action en la bitacora" || fail "falta $action"
done
denied="$(curl -s -m 20 -H "$OSI" "$G/api/v1/audit/events?action=ACCESS_DENIED&serviceName=ms-gateway&size=1")"
[ "$(jq -r '.content[0].requestPath // empty' <<<"$denied")" = "/api/v1/patients/search" ] \
  && ok "el 403 de ms-pacientes quedo como ACCESS_DENIED (lo registra el gateway)" \
  || fail "no hay ACCESS_DENIED del 403 de pacientes: $(head -c 200 <<<"$denied")"
[ -n "$account" ] && code -X PATCH "$G/api/v1/identity/users/$account/status" -H "$OSI" -H "$J" \
  -d '{"status":"INACTIVE","reason":"Fin de la prueba e2e"}' >/dev/null
code -X DELETE "$G/api/v1/identity/roles/$role" -H "$OSI" >/dev/null

echo "== Resultado"
[ $failures -eq 0 ] && echo "  Todo en verde." || echo "  $failures verificaciones fallaron."
exit $failures
INNER
inner=$?
total=$((failures + inner))
[ $total -eq 0 ] && echo "E2E OK" || echo "E2E con $total fallas"
exit $total
