#!/usr/bin/env bash
# Prueba de humo del realm medicalsys (IAM-01).
#
#   1. Pide un token client_credentials con el service account de ms-identidad.
#   2. Con ese token crea (si no existe) un usuario de prueba via Admin REST API, lo que
#      demuestra el rol manage-users de realm-management.
#   3. Pide un token password (Direct Access Grants) para el usuario de prueba.
#
# Uso: bash infra/keycloak/smoke-token.sh [http://localhost:18080]
set -euo pipefail

BASE_URL="${1:-${KEYCLOAK_URL:-http://localhost:18080}}"
REALM="${KEYCLOAK_REALM:-medicalsys}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-ms-identidad}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-medicalsys-identity-secret-local}"
TEST_USER="${KC_TEST_USER:-usuario.prueba@medicalsys.demo}"
TEST_PASSWORD="${KC_TEST_PASSWORD:-Prueba1234!}"

TOKEN_URL="$BASE_URL/realms/$REALM/protocol/openid-connect/token"
ADMIN_URL="$BASE_URL/admin/realms/$REALM"

json_field() {
  sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p"
}

echo "== 1. Token del service account ($CLIENT_ID)"
SERVICE_TOKEN=$(curl -sS --fail-with-body "$TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id="$CLIENT_ID" \
  -d client_secret="$CLIENT_SECRET" | json_field access_token)
[ -n "$SERVICE_TOKEN" ] || { echo "No se obtuvo token del service account" >&2; exit 1; }
echo "   ok"

echo "== 2. Usuario de prueba $TEST_USER"
EXISTING=$(curl -sS --fail-with-body -H "Authorization: Bearer $SERVICE_TOKEN" \
  "$ADMIN_URL/users?username=$TEST_USER&exact=true")
if [ "$EXISTING" = "[]" ]; then
  curl -sS --fail-with-body -o /dev/null -H "Authorization: Bearer $SERVICE_TOKEN" \
    -H "Content-Type: application/json" "$ADMIN_URL/users" -d @- <<JSON
{
  "username": "$TEST_USER",
  "email": "$TEST_USER",
  "firstName": "Usuario",
  "lastName": "Prueba",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{"type": "password", "value": "$TEST_PASSWORD", "temporary": false}]
}
JSON
  echo "   creado con el service account (manage-users)"
else
  echo "   ya existia"
fi

echo "== 3. Token del usuario de prueba (Direct Access Grants)"
RESPONSE=$(curl -sS --fail-with-body "$TOKEN_URL" \
  -d grant_type=password \
  -d client_id="$CLIENT_ID" \
  -d client_secret="$CLIENT_SECRET" \
  -d username="$TEST_USER" \
  -d password="$TEST_PASSWORD" \
  -d scope=openid)
ACCESS_TOKEN=$(printf '%s' "$RESPONSE" | json_field access_token)
[ -n "$ACCESS_TOKEN" ] || { echo "Respuesta sin access_token: $RESPONSE" >&2; exit 1; }

# Muestra los claims del access token (payload JWT en base64url).
PAYLOAD=$(printf '%s' "$ACCESS_TOKEN" | cut -d. -f2 | tr '_-' '/+')
while [ $(( ${#PAYLOAD} % 4 )) -ne 0 ]; do PAYLOAD="$PAYLOAD="; done
echo "   token_type=$(printf '%s' "$RESPONSE" | json_field token_type)"
echo "   claims: $(printf '%s' "$PAYLOAD" | base64 -d 2>/dev/null)"
echo "OK: el token endpoint emitio un access token para $TEST_USER"
