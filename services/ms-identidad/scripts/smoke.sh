#!/usr/bin/env bash
# Pruebas de humo de ms-identidad contra una instancia en ejecucion.
B=${1:-http://localhost:8081}
pass=0; fail=0
check() { # check <descripcion> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then echo "  OK   $1 ($3)"; pass=$((pass+1));
  else echo "  FALLA $1: esperado $2, obtenido $3"; fail=$((fail+1)); fi
}
code() { curl -s -o /tmp/body.json -w '%{http_code}' "$@"; }
# macOS solo trae python3; algunas distribuciones solo python.
PY=$(command -v python3 || command -v python)
field() { "$PY" -c 'import json,sys;print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
JSON='Content-Type: application/json'

echo "== ms-identidad =="
check "ping" 200 "$(code $B/api/v1/identity/ping)"
check "health" 200 "$(code $B/actuator/health)"

TOKEN=$(curl -s -X POST $B/api/v1/identity/auth/login -H "$JSON" \
  -d '{"email":"admin@medicalsys.demo","password":"Demo1234!"}' | field accessToken)
AUTH="Authorization: Bearer $TOKEN"

check "login con credenciales malas" 401 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d '{"email":"admin@medicalsys.demo","password":"NoEsLaClave1"}')"
check "login con email inexistente" 401 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d '{"email":"nadie@medicalsys.demo","password":"Demo1234!"}')"
check "login con cuerpo invalido" 400 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d '{"email":"no-es-email","password":"x"}')"

check "auth/me con token" 200 "$(code $B/api/v1/identity/auth/me -H "$AUTH")"
check "auth/me sin token" 401 "$(code $B/api/v1/identity/auth/me)"
check "auth/me con token falso" 401 "$(code $B/api/v1/identity/auth/me -H 'Authorization: Bearer no.es.un.jwt')"

check "listar roles" 200 "$(code $B/api/v1/identity/roles -H "$AUTH")"
check "listar permisos por modulo" 200 "$(code "$B/api/v1/identity/permissions?module=SCHEDULING" -H "$AUTH")"
check "listar usuarios" 200 "$(code $B/api/v1/identity/users -H "$AUTH")"

# Desde RBAC-02 un administrador de centro solo ve, crea y gestiona cuentas de su organizacion.
ORG=$(curl -s $B/api/v1/identity/auth/me -H "$AUTH" | field organizationPublicId)
ADMIN_ID=$(curl -s $B/api/v1/identity/auth/me -H "$AUTH" | field publicId)
OTHER_ORG=00000000-0000-4000-8000-0000000000b2
EMAIL="prueba-$RANDOM@medicalsys.demo"

# Los cuerpos que llevan variables se arman aqui y no dentro de "$(...)": el bash 3.2 de macOS deja
# alli las llaves sin comillas, las expande por las comas y el servicio recibe un JSON roto.
# IAM-04: sin "password" el alta genera una contrasena temporal; con ella, Keycloak aplica la politica.
NEW_BODY="{\"email\":\"$EMAIL\",\"fullName\":\"Usuario De Prueba\",\"phoneE164\":\"+59171234567\",\"organizationPublicId\":\"$ORG\",\"activateImmediately\":true,\"roles\":[{\"role\":\"RECEPTIONIST\"}]}"
DUP_BODY="{\"email\":\"$EMAIL\",\"fullName\":\"Duplicado\",\"password\":\"Prueba segura 2026!\",\"activateImmediately\":true}"
OTHER_ORG_BODY="{\"email\":\"otra-$EMAIL\",\"fullName\":\"Otra Organizacion\",\"password\":\"Prueba segura 2026!\",\"organizationPublicId\":\"$OTHER_ORG\",\"activateImmediately\":true}"
NEW_PASSWORD="Recepcion prueba $RANDOM ok!"
LOGIN_BODY="{\"email\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}"
BAD_LOGIN_BODY="{\"email\":\"$EMAIL\",\"password\":\"ClaveIncorrecta1\"}"

curl -s -X POST $B/api/v1/identity/users -H "$AUTH" -H "$JSON" -d "$NEW_BODY" > /tmp/created.json
PID=$(field publicId < /tmp/created.json)
TEMP=$(field temporaryPassword < /tmp/created.json)
echo "  usuario creado: $PID ($(field userCode < /tmp/created.json))"
SHORT_BODY="{\"email\":\"corta-$EMAIL\",\"fullName\":\"Corta\",\"password\":\"Corta9!a\"}"
check "alta con contrasena que no cumple la politica" 400 "$(code -X POST $B/api/v1/identity/users -H "$AUTH" -H "$JSON" -d "$SHORT_BODY")"
check "  ... y el motivo es la politica" PASSWORD_POLICY_VIOLATION "$(field code < /tmp/body.json)"

# IAM-05: primer ingreso. La temporal no da sesion (403) y se cambia sin sesion.
TEMP_LOGIN="{\"email\":\"$EMAIL\",\"password\":\"$TEMP\"}"
FIRST_CHANGE="{\"email\":\"$EMAIL\",\"currentPassword\":\"$TEMP\",\"newPassword\":\"$NEW_PASSWORD\"}"
check "login con la contrasena temporal" 403 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$TEMP_LOGIN")"
check "cambio sin sesion en el primer ingreso" 200 "$(code -X POST $B/api/v1/identity/auth/change-password -H "$JSON" -d "$FIRST_CHANGE")"
check "login con la contrasena nueva" 200 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"
check "crear usuario duplicado" 409 "$(code -X POST $B/api/v1/identity/users -H "$AUTH" -H "$JSON" -d "$DUP_BODY")"
check "crear cuenta en otra organizacion" 403 "$(code -X POST $B/api/v1/identity/users -H "$AUTH" -H "$JSON" -d "$OTHER_ORG_BODY")"
check "listar cuentas de otra organizacion" 403 "$(code "$B/api/v1/identity/users?organizationPublicId=$OTHER_ORG" -H "$AUTH")"
check "obtener usuario" 200 "$(code $B/api/v1/identity/users/$PID -H "$AUTH")"
check "obtener usuario inexistente" 404 "$(code $B/api/v1/identity/users/00000000-0000-0000-0000-000000000000 -H "$AUTH")"
check "actualizar usuario" 200 "$(code -X PATCH $B/api/v1/identity/users/$PID -H "$AUTH" -H "$JSON" -d '{"fullName":"Usuario Renombrado"}')"
check "telefono invalido rechazado" 400 "$(code -X PATCH $B/api/v1/identity/users/$PID -H "$AUTH" -H "$JSON" -d '{"phoneE164":"71234567"}')"
check "asignar rol" 200 "$(code -X POST $B/api/v1/identity/users/$PID/roles -H "$AUTH" -H "$JSON" -d '{"role":"PHYSICIAN"}')"
check "asignar rol repetido" 409 "$(code -X POST $B/api/v1/identity/users/$PID/roles -H "$AUTH" -H "$JSON" -d '{"role":"PHYSICIAN"}')"
check "revocar rol" 200 "$(code -X DELETE $B/api/v1/identity/users/$PID/roles/PHYSICIAN -H "$AUTH")"
check "asignarse un rol a si mismo" 403 "$(code -X POST $B/api/v1/identity/users/$ADMIN_ID/roles -H "$AUTH" -H "$JSON" -d '{"role":"PHYSICIAN"}')"
check "asignar un rol de plataforma sin serlo" 403 "$(code -X POST $B/api/v1/identity/users/$PID/roles -H "$AUTH" -H "$JSON" -d '{"role":"PLATFORM_ADMIN"}')"
check "revocar rol no asignado" 404 "$(code -X DELETE $B/api/v1/identity/users/$PID/roles/BILLING_CLERK -H "$AUTH")"
check "contrato interno" 200 "$(code $B/internal/v1/users/$PID)"

# El usuario nuevo tiene rol RECEPTIONIST: puede leer agenda pero no administrar cuentas.
NTOKEN=$(curl -s -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY" | field accessToken)
NAUTH="Authorization: Bearer $NTOKEN"
check "recepcionista no puede listar usuarios" 403 "$(code $B/api/v1/identity/users -H "$NAUTH")"
check "recepcionista si puede ver su perfil" 200 "$(code $B/api/v1/identity/auth/me -H "$NAUTH")"

check "cerrar sesion" 200 "$(code -X POST $B/api/v1/identity/auth/logout -H "$NAUTH")"
check "token revocado ya no sirve" 401 "$(code $B/api/v1/identity/auth/me -H "$NAUTH")"

# Bloqueo por intentos fallidos: lo aplica Keycloak al 3er intento (IAM-02/IAM-03)
for i in 1 2 3; do
  code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$BAD_LOGIN_BODY" > /dev/null
  sleep 1.2  # con menos de 1 s entre fallos Keycloak aplica otra regla (bloqueo temporal por rafaga)
done
check "cuenta bloqueada tras 3 fallos" 423 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"
check "desbloquear (IAM-05)" 200 "$(code -X POST $B/api/v1/identity/users/$PID/unlock -H "$AUTH")"
check "login tras desbloqueo" 200 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"
check "reiniciar contrasena (IAM-05)" 200 "$(code -X POST $B/api/v1/identity/users/$PID/reset-password -H "$AUTH")"
RESET_TEMP=$(field temporaryPassword < /tmp/body.json)
check "login con la contrasena anterior tras el reinicio" 401 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"
REUSE="{\"email\":\"$EMAIL\",\"currentPassword\":\"$RESET_TEMP\",\"newPassword\":\"$NEW_PASSWORD\"}"
check "reutilizar una contrasena anterior" 400 "$(code -X POST $B/api/v1/identity/auth/change-password -H "$JSON" -d "$REUSE")"
check "  ... por el historico" PASSWORD_POLICY_VIOLATION "$(field code < /tmp/body.json)"
NEW_PASSWORD="Recepcion otra $RANDOM ok!"
LOGIN_BODY="{\"email\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}"
FORCED_CHANGE="{\"email\":\"$EMAIL\",\"currentPassword\":\"$RESET_TEMP\",\"newPassword\":\"$NEW_PASSWORD\"}"
check "cambio forzado tras el reinicio" 200 "$(code -X POST $B/api/v1/identity/auth/change-password -H "$JSON" -d "$FORCED_CHANGE")"
check "login con la nueva tras el reinicio" 200 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"
check "cuenta inactiva no puede entrar" 200 "$(code -X PATCH $B/api/v1/identity/users/$PID/status -H "$AUTH" -H "$JSON" -d '{"status":"INACTIVE"}')"
check "login con cuenta inactiva" 403 "$(code -X POST $B/api/v1/identity/auth/login -H "$JSON" -d "$LOGIN_BODY")"

echo "== ms-identidad: $pass correctas, $fail fallidas =="
[ "$fail" -eq 0 ]
