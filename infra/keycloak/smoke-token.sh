#!/usr/bin/env bash
# IAM-01: prueba de humo del realm medicalsys contra un Keycloak en ejecución.
#
#   set -a; source .env; set +a
#   bash infra/keycloak/smoke-token.sh            # http://localhost:${KEYCLOAK_HOST_PORT:-18080}
#
# 1. El cliente ms-identidad obtiene un token propio (client_credentials): el secreto es válido.
# 2. Con ese token crea un usuario de prueba por la Admin REST API: la service account tiene
#    manage-users.
# 3. El usuario pide un token con su contraseña (Direct Access Grants): el token endpoint
#    responde como lo usará AuthService.login.
# 4. Borra el usuario de prueba, también si un paso falla.
#
# Requiere curl y jq. La contraseña de prueba se genera en cada corrida y no se imprime.
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
REALM="${REALM:-medicalsys}"
CLIENT_ID="ms-identidad"
CLIENT_SECRET="${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET:?falta KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET (source .env)}"
TOKEN_URL="$KC_URL/realms/$REALM/protocol/openid-connect/token"
ADMIN_URL="$KC_URL/admin/realms/$REALM"

TEST_USER="iam01.prueba.$(date +%s)"
TEST_PASSWORD="Iam01 prueba $(openssl rand -hex 6) Ok9!"
user_id=""

step() { printf '\n== %s\n' "$1"; }
fail() { printf 'FALLA: %s\n' "$1" >&2; exit 1; }

cleanup() {
  if [ -n "$user_id" ] && [ -n "${service_token:-}" ]; then
    curl -sS -o /dev/null -X DELETE -H "Authorization: Bearer $service_token" "$ADMIN_URL/users/$user_id" || true
    echo "Usuario de prueba borrado."
  fi
}
trap cleanup EXIT

claims() { # payload del JWT, sin verificar firma: solo para mostrar la evidencia
  local payload
  payload="$(cut -d. -f2 <<<"$1" | tr '_-' '/+')"
  while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
  base64 -d <<<"$payload" 2>/dev/null
}

step "1. Token del cliente ms-identidad (client_credentials)"
service_response="$(curl -sS -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials -d client_id="$CLIENT_ID" --data-urlencode client_secret="$CLIENT_SECRET")"
service_token="$(jq -r '.access_token // empty' <<<"$service_response")"
[ -n "$service_token" ] || fail "sin access_token: $(jq -c '{error, error_description}' <<<"$service_response")"
claims "$service_token" | jq '{iss, azp, preferred_username, roles_realm_management: .resource_access["realm-management"].roles}'

step "2. Alta de $TEST_USER con la service account (Admin REST API)"
status="$(curl -sS -o /dev/null -w '%{http_code}' -D /tmp/iam01-headers.$$ -X POST "$ADMIN_URL/users" \
  -H "Authorization: Bearer $service_token" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg u "$TEST_USER" --arg p "$TEST_PASSWORD" '{
        username: $u, email: ($u + "@medicalsys.invalid"), firstName: "Prueba", lastName: "IAM-01",
        enabled: true, emailVerified: true,
        credentials: [{type: "password", value: $p, temporary: false}] }')")"
[ "$status" = "201" ] || fail "el alta respondió $status (¿falta manage-users?)"
user_id="$(grep -i '^location:' /tmp/iam01-headers.$$ | tr -d '\r' | sed 's#.*/users/##')"
rm -f /tmp/iam01-headers.$$
echo "201 Created, id $user_id"

step "3. Token del usuario de prueba (password grant)"
user_response="$(curl -sS -X POST "$TOKEN_URL" \
  -d grant_type=password -d client_id="$CLIENT_ID" --data-urlencode client_secret="$CLIENT_SECRET" \
  --data-urlencode username="$TEST_USER" --data-urlencode password="$TEST_PASSWORD" -d scope=openid)"
user_token="$(jq -r '.access_token // empty' <<<"$user_response")"
[ -n "$user_token" ] || fail "sin access_token: $(jq -c '{error, error_description}' <<<"$user_response")"
jq '{token_type, expires_in, refresh_expires_in, scope}' <<<"$user_response"
claims "$user_token" | jq '{iss, azp, preferred_username, email, exp, iat}'

step "4. Contraseña incorrecta"
bad="$(curl -sS -X POST "$TOKEN_URL" \
  -d grant_type=password -d client_id="$CLIENT_ID" --data-urlencode client_secret="$CLIENT_SECRET" \
  --data-urlencode username="$TEST_USER" --data-urlencode password="incorrecta")"
jq -c '{error, error_description}' <<<"$bad"
[ "$(jq -r .error <<<"$bad")" = "invalid_grant" ] || fail "se esperaba invalid_grant"

printf '\nOK: realm %s listo. client_credentials, manage-users y password grant funcionan.\n' "$REALM"
