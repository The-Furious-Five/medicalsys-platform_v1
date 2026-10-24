#!/usr/bin/env bash
# IAM-04: alta, modificación y baja de una cuenta, comparando MedicalSys con Keycloak en cada paso.
#
#   set -a; source .env; set +a
#   bash services/ms-identidad/scripts/abm-keycloak.sh http://localhost:8081
#
# Usa el administrador de la demo para la API y la service account de ms-identidad para leer Keycloak.
# La contraseña temporal que devuelve el alta no se imprime.
set -euo pipefail

B="${1:-http://localhost:8081}/api/v1/identity"
KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
SECRET="${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET:?falta KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET (source .env)}"
# Sin contrasena comun: la que eligio admin@ tras el reinicio del OSI (PR-02).
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD, la contrasena actual de admin@medicalsys.demo}"
JSON='Content-Type: application/json'
stamp="$(date +%s)"
EMAIL="iam04.alta.$stamp@medicalsys.demo"
NEW_EMAIL="iam04.modificada.$stamp@medicalsys.demo"

admin="$(curl -sS -X POST "$B/auth/login" -H "$JSON" \
  -d "$(jq -n --arg p "$ADMIN_PASSWORD" '{email: "admin@medicalsys.demo", password: $p}')" | jq -r '.accessToken // empty')"
[ -n "$admin" ] || { echo "No se pudo iniciar sesión como admin@medicalsys.demo" >&2; exit 1; }
AUTH="Authorization: Bearer $admin"
kc_token="$(curl -sS -X POST "$KC_URL/realms/medicalsys/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=ms-identidad --data-urlencode client_secret="$SECRET" | jq -r .access_token)"

keycloak_view() { # public_id -> estado del usuario en Keycloak (el username es el public_id)
  curl -sS "$KC_URL/admin/realms/medicalsys/users?username=$1&exact=true" -H "Authorization: Bearer $kc_token" \
    | jq -c '.[0] | {username, email, firstName, lastName, enabled, requiredActions}'
}
login() {
  curl -sS -X POST "$B/auth/login" -H "$JSON" -d "$(jq -n --arg e "$1" --arg p "$2" '{email: $e, password: $p}')" \
    | jq -c 'if .accessToken then {resultado: "200 acceso"} else {status, code, detail} end'
}

echo "== Alta con una contraseña que no cumple la política"
curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Lucia Quispe Mamani", password: "corta9!A",
        activateImmediately: true, roles: [{role: "RECEPTIONIST"}]}')" | jq -c '{status, code, detail}'

echo; echo "== Alta (el sistema genera la contraseña temporal)"
created="$(curl -sS -X POST "$B/users" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Lucia Quispe Mamani", activateImmediately: true,
        roles: [{role: "RECEPTIONIST"}]}')")"
public_id="$(jq -r .publicId <<<"$created")"
temporary="$(jq -r .temporaryPassword <<<"$created")"
jq -c '{userCode, publicId, email, fullName, status, temporaryPassword: (if .temporaryPassword then "(entregada una sola vez, \(.temporaryPassword | length) caracteres)" else null end)}' <<<"$created"
echo "MedicalSys GET:  $(curl -sS "$B/users/$public_id" -H "$AUTH" | jq -c '{userCode, temporaryPassword}')"
echo "Keycloak:        $(keycloak_view "$public_id")"
echo "Login con la temporal: $(login "$EMAIL" "$temporary")"

echo; echo "== Modificación (nombre y correo)"
curl -sS -X PATCH "$B/users/$public_id" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg e "$NEW_EMAIL" '{fullName: "Lucia Quispe de Rojas", email: $e}')" | jq -c '{userCode, email, fullName}'
echo "Keycloak:        $(keycloak_view "$public_id")"

echo; echo "== Baja (deshabilita, nunca borra)"
curl -sS -X PATCH "$B/users/$public_id/status" -H "$AUTH" -H "$JSON" -d '{"status":"INACTIVE","reason":"IAM-04"}' \
  | jq -c '{userCode, status}'
echo "Keycloak:        $(keycloak_view "$public_id")"
echo "Login:           $(login "$NEW_EMAIL" "$temporary")"
echo "La cuenta sigue en MedicalSys: $(curl -sS "$B/users/$public_id" -H "$AUTH" | jq -c '{userCode, status}')"
