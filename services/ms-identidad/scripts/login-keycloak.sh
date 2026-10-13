#!/usr/bin/env bash
# IAM-03: los cuatro casos del login delegado a Keycloak, con curl contra ms-identidad.
#
#   set -a; source .env; set +a
#   bash services/ms-identidad/scripts/login-keycloak.sh http://localhost:8081
#
# Crea una cuenta de prueba como administrador de la demo, recorre los casos y la deja inactiva.
#   1. contraseña válida          -> 200 con el JWT propio de MedicalSys
#   2. contraseña inválida        -> 401 genérico (igual que un correo inexistente)
#   3. 3 fallos seguidos          -> 423 ACCOUNT_LOCKED, también con la contraseña correcta
#   4. contraseña vencida         -> 403 PASSWORD_CHANGE_REQUIRED
# Para el caso 4 marca UPDATE_PASSWORD en Keycloak con la service account de ms-identidad: es lo
# mismo que hace Keycloak cuando pasan los 90 días de forceExpiredPasswordChange.
set -euo pipefail

B="${1:-http://localhost:8081}/api/v1/identity"
KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
SECRET="${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET:?falta KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET (source .env)}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@medicalsys.demo}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${IDENTITY_DEMO_PASSWORD:-Demo1234!}}"
JSON='Content-Type: application/json'

EMAIL="iam03.prueba.$(date +%s)@medicalsys.demo"
PASSWORD="Prueba iam03 $(openssl rand -hex 4) Ok9!"

login() { # correo, contraseña -> imprime "HTTP <código> <code/título>"
  local body status
  body="$(curl -sS -w '\n%{http_code}' -X POST "$B/auth/login" -H "$JSON" \
    -d "$(jq -n --arg e "$1" --arg p "$2" '{email: $e, password: $p}')")"
  status="$(tail -n1 <<<"$body")"
  printf 'HTTP %s  %s\n' "$status" "$(sed '$d' <<<"$body" | jq -r 'if .accessToken then "accessToken \(.accessToken[0:16])… roles \(.user.roles)" else "\(.code // "-") · \(.detail)" end')"
}

admin_token="$(curl -sS -X POST "$B/auth/login" -H "$JSON" \
  -d "$(jq -n --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email: $e, password: $p}')" | jq -r '.accessToken // empty')"
[ -n "$admin_token" ] || { echo "No se pudo iniciar sesión como $ADMIN_EMAIL" >&2; exit 1; }
AUTH="Authorization: Bearer $admin_token"

# Desde IAM-04 el alta entrega una contraseña temporal; el primer ingreso la cambia sin sesión (IAM-05).
created="$(curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Prueba IAM-03", activateImmediately: true,
        roles: [{role: "RECEPTIONIST"}]}')")"
user_id="$(jq -r .publicId <<<"$created")"
curl -sS -o /dev/null -X POST "$B/auth/change-password" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" --arg c "$(jq -r .temporaryPassword <<<"$created")" --arg p "$PASSWORD" \
        '{email: $e, currentPassword: $c, newPassword: $p}')"
trap 'curl -sS -o /dev/null -X PATCH "$B/users/$user_id/status" -H "$AUTH" -H "$JSON" -d "{\"status\":\"INACTIVE\"}"' EXIT
echo "Cuenta de prueba: $EMAIL"

echo; echo "== 1. Contraseña válida";  login "$EMAIL" "$PASSWORD"
echo; echo "== 2. Contraseña inválida (1er fallo)"; login "$EMAIL" "Incorrecta 1 Xx9!"
# Con menos de 1 s entre fallos Keycloak aplica además un bloqueo temporal por ráfaga; se espera
# para que la evidencia muestre la regla del 3er intento sola.
sleep 1.2
echo "   Correo inexistente:";        login "nadie.$(date +%s)@medicalsys.demo" "Incorrecta 1 Xx9!"

echo; echo "== 3. Cuenta bloqueada al 3er intento"
echo "   2º fallo:"; login "$EMAIL" "Incorrecta 2 Xx9!"; sleep 1.2
echo "   3er fallo:"; login "$EMAIL" "Incorrecta 3 Xx9!"; sleep 1.2
echo "   Con la contraseña correcta:"; login "$EMAIL" "$PASSWORD"
curl -sS -o /dev/null -X PATCH "$B/users/$user_id/status" -H "$AUTH" -H "$JSON" -d '{"status":"ACTIVE","reason":"IAM-03"}'
echo "   Tras desbloquear desde la app:"; login "$EMAIL" "$PASSWORD"

echo; echo "== 4. Contraseña vencida"
service_token="$(curl -sS -X POST "$KC_URL/realms/medicalsys/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=ms-identidad --data-urlencode client_secret="$SECRET" | jq -r .access_token)"
kc_id="$(curl -sS "$KC_URL/admin/realms/medicalsys/users?email=$EMAIL&exact=true" -H "Authorization: Bearer $service_token" | jq -r '.[0].id')"
curl -sS -o /dev/null -X PUT "$KC_URL/admin/realms/medicalsys/users/$kc_id" -H "Authorization: Bearer $service_token" \
  -H "$JSON" -d '{"requiredActions":["UPDATE_PASSWORD"]}'
login "$EMAIL" "$PASSWORD"
