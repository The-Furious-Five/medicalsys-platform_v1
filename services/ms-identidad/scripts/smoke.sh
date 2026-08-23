#!/usr/bin/env bash
# Pruebas de humo de ms-identidad contra una instancia en ejecucion.
B=${1:-http://localhost:8081}
pass=0; fail=0
check() { # check <descripcion> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then echo "  OK   $1 ($3)"; pass=$((pass+1));
  else echo "  FALLA $1: esperado $2, obtenido $3"; fail=$((fail+1)); fi
}
code() { curl -s -o /tmp/body.json -w '%{http_code}' "$@"; }

echo "== ms-identidad =="
check "ping" 200 "$(code $B/api/v1/identity/ping)"
check "health" 200 "$(code $B/actuator/health)"

TOKEN=$(curl -s -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@medicalsys.demo","password":"Demo1234!"}' | python -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])')
AUTH="Authorization: Bearer $TOKEN"

check "login con credenciales malas" 401 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@medicalsys.demo","password":"NoEsLaClave1"}')"
check "login con email inexistente" 401 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d '{"email":"nadie@medicalsys.demo","password":"Demo1234!"}')"
check "login con cuerpo invalido" 400 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d '{"email":"no-es-email","password":"x"}')"

check "auth/me con token" 200 "$(code $B/api/v1/identity/auth/me -H "$AUTH")"
check "auth/me sin token" 401 "$(code $B/api/v1/identity/auth/me)"
check "auth/me con token falso" 401 "$(code $B/api/v1/identity/auth/me -H 'Authorization: Bearer no.es.un.jwt')"

check "listar roles" 200 "$(code $B/api/v1/identity/roles -H "$AUTH")"
check "listar permisos por modulo" 200 "$(code "$B/api/v1/identity/permissions?module=SCHEDULING" -H "$AUTH")"
check "listar usuarios" 200 "$(code $B/api/v1/identity/users -H "$AUTH")"

EMAIL="prueba-$RANDOM@medicalsys.demo"
NEW=$(curl -s -X POST $B/api/v1/identity/users -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"email\":\"$EMAIL\",\"fullName\":\"Usuario De Prueba\",\"password\":\"Prueba1234!\",
  \"phoneE164\":\"+59171234567\",\"organizationPublicId\":\"11111111-1111-1111-1111-111111111111\",
  \"activateImmediately\":true,\"roles\":[{\"role\":\"RECEPTIONIST\"}]}")
PID=$(echo "$NEW" | python -c 'import json,sys;print(json.load(sys.stdin)["publicId"])')
echo "  usuario creado: $PID"
check "crear usuario duplicado" 409 "$(code -X POST $B/api/v1/identity/users -H "$AUTH" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"fullName\":\"Duplicado\",\"password\":\"Prueba1234!\",\"activateImmediately\":true}")"
check "obtener usuario" 200 "$(code $B/api/v1/identity/users/$PID -H "$AUTH")"
check "obtener usuario inexistente" 404 "$(code $B/api/v1/identity/users/00000000-0000-0000-0000-000000000000 -H "$AUTH")"
check "actualizar usuario" 200 "$(code -X PATCH $B/api/v1/identity/users/$PID -H "$AUTH" -H 'Content-Type: application/json' -d '{"fullName":"Usuario Renombrado"}')"
check "telefono invalido rechazado" 400 "$(code -X PATCH $B/api/v1/identity/users/$PID -H "$AUTH" -H 'Content-Type: application/json' -d '{"phoneE164":"71234567"}')"
check "asignar rol" 200 "$(code -X POST $B/api/v1/identity/users/$PID/roles -H "$AUTH" -H 'Content-Type: application/json' -d '{"role":"PHYSICIAN"}')"
check "asignar rol repetido" 409 "$(code -X POST $B/api/v1/identity/users/$PID/roles -H "$AUTH" -H 'Content-Type: application/json' -d '{"role":"PHYSICIAN"}')"
check "revocar rol" 200 "$(code -X DELETE $B/api/v1/identity/users/$PID/roles/PHYSICIAN -H "$AUTH")"
check "revocar rol no asignado" 404 "$(code -X DELETE $B/api/v1/identity/users/$PID/roles/BILLING_CLERK -H "$AUTH")"
check "contrato interno" 200 "$(code $B/internal/v1/users/$PID)"

# El usuario nuevo tiene rol RECEPTIONIST: puede leer agenda pero no administrar cuentas.
NTOKEN=$(curl -s -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Prueba1234!\"}" | python -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])')
NAUTH="Authorization: Bearer $NTOKEN"
check "recepcionista no puede listar usuarios" 403 "$(code $B/api/v1/identity/users -H "$NAUTH")"
check "recepcionista si puede ver su perfil" 200 "$(code $B/api/v1/identity/auth/me -H "$NAUTH")"

check "cerrar sesion" 200 "$(code -X POST $B/api/v1/identity/auth/logout -H "$NAUTH")"
check "token revocado ya no sirve" 401 "$(code $B/api/v1/identity/auth/me -H "$NAUTH")"

# Bloqueo por intentos fallidos (5 intentos configurados)
for i in 1 2 3 4 5; do
  code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"ClaveIncorrecta1\"}" > /dev/null
done
check "cuenta bloqueada tras 5 fallos" 423 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"Prueba1234!\"}")"
check "desbloquear cambiando estado" 200 "$(code -X PATCH $B/api/v1/identity/users/$PID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"ACTIVE","reason":"prueba de humo"}')"
check "login tras desbloqueo" 200 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"Prueba1234!\"}")"
check "cuenta inactiva no puede entrar" 200 "$(code -X PATCH $B/api/v1/identity/users/$PID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"INACTIVE"}')"
check "login con cuenta inactiva" 403 "$(code -X POST $B/api/v1/identity/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"Prueba1234!\"}")"

echo "== ms-identidad: $pass correctas, $fail fallidas =="
[ "$fail" -eq 0 ]
