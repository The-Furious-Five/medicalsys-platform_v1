#!/usr/bin/env bash
# Exporta el realm medicalsys del Keycloak en ejecucion a infra/keycloak/realm-medicalsys.json,
# el archivo que compose importa al arrancar (--import-realm).
#
# Antes de versionar el export se limpia:
#   - usuarios: solo queda el service account de ms-identidad (lleva el rol manage-users);
#     las cuentas reales y sus hashes de contrasena no salen del contenedor.
#   - secreto del cliente ms-identidad: vuelve el placeholder ${KEYCLOAK_CLIENT_SECRET}.
#
# Uso (desde la raiz del repo, con `docker compose up -d keycloak` corriendo):
#   bash infra/keycloak/export-realm.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
TARGET="infra/keycloak/realm-medicalsys.json"
RAW="$(mktemp)"
trap 'rm -f "$RAW"' EXIT

docker compose exec -T keycloak /opt/keycloak/bin/kc.sh export \
  --realm medicalsys --users realm_file --file /tmp/realm-medicalsys.json
docker compose cp keycloak:/tmp/realm-medicalsys.json "$RAW"

node - "$RAW" "$TARGET" <<'JS'
const fs = require('fs');
const [raw, target] = process.argv.slice(2);
const realm = JSON.parse(fs.readFileSync(raw, 'utf8'));
realm.users = (realm.users || []).filter(user => user.serviceAccountClientId === 'ms-identidad');
for (const client of realm.clients || []) {
  if (client.clientId === 'ms-identidad') {
    client.secret = '${KEYCLOAK_CLIENT_SECRET}';
  }
}
fs.writeFileSync(target, JSON.stringify(realm, null, 2) + '\n');
console.log(`Realm exportado a ${target} (${realm.clients.length} clientes, ${realm.users.length} usuario de servicio)`);
JS
