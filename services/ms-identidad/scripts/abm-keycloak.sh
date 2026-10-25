#!/usr/bin/env bash
# IAM-04: alta, modificación y baja de una cuenta, comparando MedicalSys con Keycloak en cada paso.
#
#   set -a; source .env; set +a
#   bash services/ms-identidad/scripts/abm-keycloak.sh http://localhost:8081
#
# Usa el administrador del centro para la API y la service account de ms-identidad para leer Keycloak.
# La contraseña temporal que devuelve el alta no se imprime.
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
stamp="$(date +%s)"
EMAIL="iam04.alta.$stamp@medicalsys.demo"
NEW_EMAIL="iam04.modificada.$stamp@medicalsys.demo"

admin="$(mfa_login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$ADMIN_TOTP_SECRET")"
[ -n "$admin" ] || { echo "No se pudo iniciar sesión como $ADMIN_USERNAME" >&2; exit 1; }
AUTH="Authorization: Bearer $admin"
kc_token="$(curl -sS -X POST "$KC_URL/realms/medicalsys/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=ms-identidad --data-urlencode client_secret="$SECRET" | jq -r .access_token)"

keycloak_view() { # public_id -> estado del usuario en Keycloak (el username es el public_id)
  curl -sS "$KC_URL/admin/realms/medicalsys/users?username=$1&exact=true" -H "Authorization: Bearer $kc_token" \
    | jq -c '.[0] | {username, email, firstName, lastName, enabled, requiredActions}'
}
login() {
  curl -sS -X POST "$B/auth/login" -H "$JSON" -d "$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')" \
    | jq -c 'if .mfaToken then {resultado: "202 falta el segundo factor", status} else {status, code, detail} end'
}

echo "== Alta con una contraseña que no cumple la política"
curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, givenNames: "Lucia", firstSurname: "Quispe", secondSurname: "Mamani", password: "corta9!A",
        activateImmediately: true, roles: [{role: "RECEPTIONIST"}]}')" | jq -c '{status, code, detail}'

echo; echo "== Alta (el sistema genera la contraseña temporal)"
created="$(curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, givenNames: "Lucia", firstSurname: "Quispe", secondSurname: "Mamani", activateImmediately: true,
        roles: [{role: "RECEPTIONIST"}]}')")"
public_id="$(jq -r .publicId <<<"$created")"
username="$(jq -r .username <<<"$created")"
temporary="$(jq -r .temporaryPassword <<<"$created")"
jq -c '{userCode, username, publicId, email, fullName, status, temporaryPassword: (if .temporaryPassword then "(entregada una sola vez, \(.temporaryPassword | length) caracteres)" else null end)}' <<<"$created"
echo "MedicalSys GET:  $(curl -sS "$B/users/$public_id" -H "$AUTH" | jq -c '{userCode, temporaryPassword}')"
echo "Keycloak:        $(keycloak_view "$public_id")"
echo "Login con la temporal: $(login "$username" "$temporary")"

echo; echo "== Modificación (nombre y correo)"
curl -sS -X PATCH "$B/users/$public_id" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$NEW_EMAIL" '{fullName: "Lucia Quispe de Rojas", email: $e}')" | jq -c '{userCode, username, email, fullName}'
echo "Keycloak:        $(keycloak_view "$public_id")"

echo; echo "== Baja (deshabilita, nunca borra)"
curl -sS -X PATCH "$B/users/$public_id/status" -H "$AUTH" -H "$JSON" -d '{"status":"INACTIVE","reason":"IAM-04"}' \
  | jq -c '{userCode, status}'
echo "Keycloak:        $(keycloak_view "$public_id")"
echo "Login:           $(login "$username" "$temporary")"
echo "La cuenta sigue en MedicalSys: $(curl -sS "$B/users/$public_id" -H "$AUTH" | jq -c '{userCode, status}')"
