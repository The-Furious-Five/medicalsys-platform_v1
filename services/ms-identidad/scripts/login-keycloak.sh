#!/usr/bin/env bash
# IAM-03: los cuatro casos del login delegado a Keycloak, con curl contra ms-identidad.
#
#   set -a; source .env; set +a
#   bash services/ms-identidad/scripts/login-keycloak.sh http://localhost:8081
#
# Crea una cuenta de prueba como administrador del centro, recorre los casos y la deja inactiva.
#   1. contraseña válida          -> 202: falta el segundo factor; con el QR y el código, 200 con el JWT
#   2. contraseña inválida        -> 401 genérico (igual que un usuario inexistente)
#   3. 3 fallos seguidos          -> 423 ACCOUNT_LOCKED, también con la contraseña correcta
#   4. contraseña vencida         -> 403 PASSWORD_CHANGE_REQUIRED
# Para el caso 4 marca UPDATE_PASSWORD en Keycloak con la service account de ms-identidad: es lo
# mismo que hace Keycloak cuando pasan los 90 días de forceExpiredPasswordChange.
set -euo pipefail

B="${1:-http://localhost:8081}/api/v1/identity"
KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
SECRET="${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET:?falta KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET (source .env)}"
# IAM-07: el administrador entra con su usuario, su contraseña y el secreto TOTP de su aplicación.
ADMIN_USERNAME="${ADMIN_USERNAME:?Falta ADMIN_USERNAME, el usuario del administrador del centro}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
ADMIN_TOTP_SECRET="${ADMIN_TOTP_SECRET:?Falta ADMIN_TOTP_SECRET, el secreto Base32 de su segundo factor}"
JSON='Content-Type: application/json'
source "$(dirname "$0")/lib-mfa.sh"

EMAIL="iam03.prueba.$(date +%s)@medicalsys.demo"
PASSWORD="Prueba iam03 $(openssl rand -hex 4) Ok9!"

login() { # usuario, contraseña -> imprime "HTTP <código> <code/título>"
  local body status
  body="$(curl -sS -w '\n%{http_code}' -X POST "$B/auth/login" -H "$JSON" \
    -d "$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')")"
  status="$(tail -n1 <<<"$body")"
  printf 'HTTP %s  %s\n' "$status" "$(sed '$d' <<<"$body" | jq -r 'if .mfaToken then "\(.status) · contraseña aceptada, falta el segundo factor" else "\(.code // "-") · \(.detail)" end')"
}

admin_token="$(mfa_login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$ADMIN_TOTP_SECRET")"
[ -n "$admin_token" ] || { echo "No se pudo iniciar sesión como $ADMIN_USERNAME" >&2; exit 1; }
AUTH="Authorization: Bearer $admin_token"

# Desde IAM-04 el alta entrega una contraseña temporal; el primer ingreso la cambia sin sesión (IAM-05).
created="$(curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Prueba IAM-03", activateImmediately: true,
        roles: [{role: "RECEPTIONIST"}]}')")"
user_id="$(jq -r .publicId <<<"$created")"
USERNAME="$(jq -r .username <<<"$created")"
curl -sS -o /dev/null -X POST "$B/auth/change-password" -H "$JSON" \
  -d "$(jq -n --arg u "$USERNAME" --arg c "$(jq -r .temporaryPassword <<<"$created")" --arg p "$PASSWORD" \
        '{username: $u, currentPassword: $c, newPassword: $p}')"
trap 'curl -sS -o /dev/null -X PATCH "$B/users/$user_id/status" -H "$AUTH" -H "$JSON" -d "{\"status\":\"INACTIVE\"}"' EXIT
echo "Cuenta de prueba: $USERNAME ($EMAIL)"

echo; echo "== 1. Contraseña válida";  login "$USERNAME" "$PASSWORD"
read -r totp_secret access <<<"$(mfa_enroll "$USERNAME" "$PASSWORD")"
echo "   Con el QR registrado y el primer código: $([ -n "$access" ] && echo "HTTP 200  accessToken ${access:0:16}…")"
echo; echo "== 2. Contraseña inválida (1er fallo)"; login "$USERNAME" "Incorrecta 1 Xx9!"
# Con menos de 1 s entre fallos Keycloak aplica además un bloqueo temporal por ráfaga; se espera
# para que la evidencia muestre la regla del 3er intento sola.
sleep 1.2
echo "   Usuario inexistente:";       login "nadie.$(date +%s)" "Incorrecta 1 Xx9!"

echo; echo "== 3. Cuenta bloqueada al 3er intento"
echo "   2º fallo:"; login "$USERNAME" "Incorrecta 2 Xx9!"; sleep 1.2
echo "   3er fallo:"; login "$USERNAME" "Incorrecta 3 Xx9!"; sleep 1.2
echo "   Con la contraseña correcta:"; login "$USERNAME" "$PASSWORD"
curl -sS -o /dev/null -X PATCH "$B/users/$user_id/status" -H "$AUTH" -H "$JSON" -d '{"status":"ACTIVE","reason":"IAM-03"}'
echo "   Tras desbloquear desde la app:"; login "$USERNAME" "$PASSWORD"

echo; echo "== 4. Contraseña vencida"
service_token="$(curl -sS -X POST "$KC_URL/realms/medicalsys/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=ms-identidad --data-urlencode client_secret="$SECRET" | jq -r .access_token)"
kc_id="$(curl -sS "$KC_URL/admin/realms/medicalsys/users?email=$EMAIL&exact=true" -H "Authorization: Bearer $service_token" | jq -r '.[0].id')"
curl -sS -o /dev/null -X PUT "$KC_URL/admin/realms/medicalsys/users/$kc_id" -H "Authorization: Bearer $service_token" \
  -H "$JSON" -d '{"requiredActions":["UPDATE_PASSWORD"]}'
login "$USERNAME" "$PASSWORD"
