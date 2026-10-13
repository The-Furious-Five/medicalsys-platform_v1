#!/usr/bin/env bash
# Aplica a un realm ya importado la configuración de nivel realm de realm-medicalsys.json
# (política de contraseñas, fuerza bruta, etc.) sin tocar usuarios ni clientes.
#
#   set -a; source .env; set +a
#   bash infra/keycloak/sync-realm.sh
#
# Hace falta porque --import-realm ignora un realm que ya existe. Usa la cuenta de
# administración de arranque (KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD) del realm master.
set -euo pipefail

cd "$(dirname "$0")"
KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
REALM_FILE="realm-medicalsys.json"
realm="$(jq -r .realm "$REALM_FILE")"

admin_token="$(curl -sS -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode username="${KEYCLOAK_ADMIN_USER:-admin}" \
  --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:?falta KEYCLOAK_ADMIN_PASSWORD (source .env)}" \
  | jq -r '.access_token // empty')"
[ -n "$admin_token" ] || { echo "No se pudo autenticar como administrador de Keycloak" >&2; exit 1; }

status="$(jq 'del(.clients, .users)' "$REALM_FILE" | curl -sS -o /dev/null -w '%{http_code}' -X PUT \
  "$KC_URL/admin/realms/$realm" -H "Authorization: Bearer $admin_token" \
  -H 'Content-Type: application/json' --data-binary @-)"
[ "$status" = "204" ] || { echo "Keycloak respondió $status al actualizar el realm" >&2; exit 1; }
echo "Realm $realm sincronizado con $REALM_FILE (usuarios y clientes sin cambios)."
