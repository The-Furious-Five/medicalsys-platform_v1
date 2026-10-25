#!/usr/bin/env bash
# Prueba de funcionamiento general (smoke test) a traves del gateway: una lectura por microservicio
# con una sesion real. Confirma que cerrar los puertos (HARD-01) no rompio el enrutamiento.
#
# Uso: BASE=http://localhost:8080 bash docs/seguridad/evidencias/smoke-test.sh
set -uo pipefail
BASE=${BASE:-http://localhost:8080}
# IAM-07: usuario, contrasena y secreto TOTP del administrador del centro (requiere jq y oathtool).
ADMIN_USERNAME=${ADMIN_USERNAME:?Falta ADMIN_USERNAME}
ADMIN_PASSWORD=${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}
ADMIN_TOTP_SECRET=${ADMIN_TOTP_SECRET:?Falta ADMIN_TOTP_SECRET}
IDENTITY_BASE="$BASE/api/v1/identity"
source "$(cd "$(dirname "$0")/../.." && pwd)/services/ms-identidad/scripts/lib-mfa.sh"

TOKEN=$(mfa_login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$ADMIN_TOTP_SECRET")
[ -n "$TOKEN" ] || { echo "FALLO: no se pudo iniciar sesion como $ADMIN_USERNAME"; exit 1; }
echo "OK    login $ADMIN_USERNAME (contrasena y segundo factor)"

FALLOS=0
esperar() { # esperar <codigo> <servicio> <ruta>
  local codigo
  codigo=$(curl -s -m 15 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE$3")
  if [ "$codigo" = "$1" ]; then echo "OK    $codigo  $2  $3"; else echo "FALLO $codigo (esperado $1)  $2  $3"; FALLOS=$((FALLOS + 1)); fi
}
esperar 200 ms-identidad      /api/v1/identity/auth/me
esperar 200 ms-tenencia       /api/v1/tenancy/sucursales
esperar 200 ms-catalogos      "/api/v1/catalogs/cie10?q=a"
esperar 200 ms-pacientes      "/api/v1/patients/search?q=a"
esperar 200 ms-profesionales  /api/v1/professionals
esperar 200 ms-agenda         /api/v1/scheduling/appointments
esperar 200 ms-historial      /api/v1/expedientes
esperar 200 ms-documental     /api/v1/documents
esperar 200 ms-facturacion    /api/v1/facturas
esperar 200 ms-marketing      /api/v1/campanas
esperar 200 ms-auditoria      /api/v1/audit/events
esperar 200 ms-notificaciones /api/v1/notifications
# ms-criptografia no tiene superficie publica: solo se consume entre servicios.
esperar 401 ms-criptografia   /api/v1/cryptography/ping

echo
if [ $FALLOS -eq 0 ]; then echo "SMOKE TEST: PASA (13 servicios detras del gateway)"; else echo "SMOKE TEST: $FALLOS fallo(s)"; exit 1; fi
