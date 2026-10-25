#!/usr/bin/env bash
# Evidencia antes/despues de HARD-01 (SINS-12), HARD-02 (SINS-13), HARD-03 (SINS-14) y HARD-04 (SINS-15).
#
# Ejecuta exactamente las mismas solicitudes contra la version que este levantada y guarda la salida
# en docs/seguridad/evidencias/<fase>/. Se corre una vez sobre develop (fase "antes") y otra sobre la
# version corregida (fase "despues"); la diferencia entre ambas carpetas es la evidencia.
#
# Uso:
#   DC="docker compose -p <proyecto>" bash docs/seguridad/evidencias/capturar-evidencia.sh <antes|despues>
#
# Variables:
#   DC              comando compose del proyecto bajo prueba (por defecto "docker compose")
#   BASE            URL del gateway (por defecto http://localhost:8080)
#   DOCTOR_USERNAME, DOCTOR_PASSWORD, DOCTOR_TOTP_SECRET
#                   usuario, contrasena y secreto TOTP del medico (IAM-07; requiere jq y oathtool)
#   WEBHOOK_SECRET  si se define, agrega las pruebas del webhook con firma valida e invalida
set -uo pipefail

FASE=${1:?Uso: capturar-evidencia.sh <antes|despues>}
DC=${DC:-docker compose}
BASE=${BASE:-http://localhost:8080}
DOCTOR_USERNAME=${DOCTOR_USERNAME:?Falta DOCTOR_USERNAME}
DOCTOR_PASSWORD=${DOCTOR_PASSWORD:?Falta DOCTOR_PASSWORD}
DOCTOR_TOTP_SECRET=${DOCTOR_TOTP_SECRET:?Falta DOCTOR_TOTP_SECRET}
IDENTITY_BASE="$BASE/api/v1/identity"
source "$(cd "$(dirname "$0")/../.." && pwd)/services/ms-identidad/scripts/lib-mfa.sh"
OUT="$(cd "$(dirname "$0")" && pwd)/$FASE"
# En Windows "python3" puede ser el alias de la tienda, que existe pero no ejecuta nada.
if python3 -c "" 2>/dev/null; then PY=python3; else PY=python; fi
mkdir -p "$OUT"

ORG=00000000-0000-4000-8000-0000000000a1
DOCTOR=00000000-0000-4000-8000-000000000903
TOKEN=""

# Muestra el comando (sin el token) y despues el codigo HTTP, las cabeceras pedidas y el cuerpo recortado.
probar() { # probar <descripcion> <cabeceras a mostrar, regex o -> <args de curl...>
  local titulo=$1 cabeceras=$2; shift 2
  local visible="curl $*"
  [ -n "$TOKEN" ] && visible=${visible//$TOKEN/<TOKEN>}
  echo "## $titulo"
  echo "\$ $visible"
  local salida codigo
  salida=$(curl -sS -m 10 -D - "$@" 2>&1); codigo=$?
  if [ $codigo -ne 0 ]; then
    echo "SIN RESPUESTA (curl exit $codigo): $(echo "$salida" | tail -n 1)"
  else
    echo "$salida" | tr -d '\r' | sed -n '1p'
    [ "$cabeceras" != "-" ] && echo "$salida" | tr -d '\r' | sed '1d;/^$/q' | grep -iE "^($cabeceras):" | sort
    echo "$salida" | tr -d '\r' | sed '1,/^$/d' | cut -c1-300
  fi
  echo
}

encabezado() { echo "# $1"; echo "# Fase: $FASE — $(date -u +%Y-%m-%dT%H:%M:%SZ)"; echo; }

# --- HARD-01: acceso directo a un servicio con cabeceras de identidad falsas -------------------
{
  encabezado "HARD-01 (SINS-12) Acceso directo saltandose el gateway — OWASP A01"
  echo "## Puertos publicados en el host"
  echo "\$ $DC ps --format '{{.Service}} {{.Ports}}'"
  $DC ps --format '{{.Service}} {{.Ports}}' | grep -- '->' | sort || echo "(ninguno)"
  echo
  probar "Directo a ms-historial :8088 con X-Roles/X-Permissions inventados, sin token" - \
    "http://localhost:8088/api/v1/encuentros" \
    -H "X-Organization-Id: $ORG" -H "X-User-Id: $DOCTOR" \
    -H "X-Roles: PHYSICIAN" -H "X-Permissions: clinical.encounter.read"
  probar "Las mismas cabeceras por el gateway, sin token (el gateway las descarta)" - \
    "$BASE/api/v1/encuentros" \
    -H "X-Organization-Id: $ORG" -H "X-User-Id: $DOCTOR" \
    -H "X-Roles: PHYSICIAN" -H "X-Permissions: clinical.encounter.read"
} > "$OUT/01-hard01-acceso-directo.txt" 2>&1

# --- HARD-02: el mismo token antes y despues de cerrar sesion ------------------------------------
{
  encabezado "HARD-02 (SINS-13) Revocacion de sesion en el gateway — OWASP A07"
  echo "## Inicio de sesion ($DOCTOR_USERNAME): contrasena y codigo TOTP (IAM-07)"
  echo "\$ curl -X POST $BASE/api/v1/identity/auth/login -d '{\"username\":\"$DOCTOR_USERNAME\",\"password\":\"***\"}'"
  echo "\$ curl -X POST $BASE/api/v1/identity/auth/mfa/verify -d '{\"mfaToken\":\"***\",\"code\":\"******\"}'"
  TOKEN=$(mfa_login "$DOCTOR_USERNAME" "$DOCTOR_PASSWORD" "$DOCTOR_TOTP_SECRET")
  if [ -z "$TOKEN" ]; then echo "LOGIN FALLIDO"; else echo "HTTP 200 — token emitido (${#TOKEN} caracteres, no se guarda)"; fi
  echo
  probar "Historial con el token, sesion abierta" - "$BASE/api/v1/encuentros" -H "Authorization: Bearer $TOKEN"
  probar "Pacientes con el token, sesion abierta" - "$BASE/api/v1/patients/search?q=a" -H "Authorization: Bearer $TOKEN"
  probar "Cierre de sesion" - -X POST "$BASE/api/v1/identity/auth/logout" -H "Authorization: Bearer $TOKEN"
  probar "Historial con EL MISMO token, sesion cerrada" - "$BASE/api/v1/encuentros" -H "Authorization: Bearer $TOKEN"
  probar "Pacientes con EL MISMO token, sesion cerrada" - "$BASE/api/v1/patients/search?q=a" -H "Authorization: Bearer $TOKEN"
} > "$OUT/02-hard02-revocacion-sesion.txt" 2>&1
TOKEN=""

# --- HARD-03: webhook de WhatsApp ------------------------------------------------------------------
PAYLOAD='{"object":"whatsapp_business_account","entry":[]}'
{
  encabezado "HARD-03 (SINS-14) Firma del webhook de WhatsApp — OWASP A08"
  if [ -z "${WEBHOOK_SECRET:-}" ]; then
    echo "Configuracion por defecto: NOTIFICATION_PROVIDER=mock y WHATSAPP_APP_SECRET vacio."
    echo
    probar "Payload sin firma" - -X POST "$BASE/api/v1/notifications/webhooks/whatsapp" \
      -H 'Content-Type: application/json' -d "$PAYLOAD"
  else
    FIRMA=$(printf '%s' "$PAYLOAD" | "$PY" -c 'import hmac,hashlib,sys;print(hmac.new(sys.argv[1].encode(),sys.stdin.buffer.read(),hashlib.sha256).hexdigest())' "$WEBHOOK_SECRET")
    echo "WHATSAPP_APP_SECRET configurado (valor de prueba)."
    echo
    probar "Payload sin firma" - -X POST "$BASE/api/v1/notifications/webhooks/whatsapp" \
      -H 'Content-Type: application/json' -d "$PAYLOAD"
    probar "Payload con firma invalida" - -X POST "$BASE/api/v1/notifications/webhooks/whatsapp" \
      -H 'Content-Type: application/json' -H "X-Hub-Signature-256: sha256=$(printf '0%.0s' $(seq 1 64))" -d "$PAYLOAD"
    probar "Payload con firma HMAC-SHA256 valida" - -X POST "$BASE/api/v1/notifications/webhooks/whatsapp" \
      -H 'Content-Type: application/json' -H "X-Hub-Signature-256: sha256=$FIRMA" -d "$PAYLOAD"
  fi
} > "$OUT/03-hard03-webhook${WEBHOOK_SECRET:+-con-secreto}.txt" 2>&1
[ -n "${WEBHOOK_SECRET:-}" ] && { echo "Evidencia del webhook con secreto guardada en $OUT"; exit 0; }

# --- HARD-03: CORS -----------------------------------------------------------------------------------
{
  encabezado "HARD-03 (SINS-14) CORS del gateway — OWASP A05"
  probar "Preflight desde un origen no autorizado" "access-control-[a-z-]+" -X OPTIONS "$BASE/api/v1/patients/search" \
    -H "Origin: https://malicioso.example" -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization"
  probar "Preflight desde el frontend autorizado" "access-control-[a-z-]+" -X OPTIONS "$BASE/api/v1/patients/search" \
    -H "Origin: http://localhost:4200" -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization"
} > "$OUT/04-hard03-cors.txt" 2>&1

# --- HARD-03: rutas internas de identidad y detalle de Actuator -----------------------------------
# Se consulta desde dentro de la red de Compose (contenedor del gateway) para que la comparacion no
# dependa de si el puerto esta publicado: eso ya lo cubre HARD-01.
dentro() { # dentro <descripcion> <url>
  echo "## $1"
  echo "\$ $DC exec -T ms-gateway curl -s -w '\\nHTTP %{http_code}' $2"
  $DC exec -T ms-gateway curl -s -m 10 -w '\nHTTP %{http_code}' "$2" 2>&1 | tr -d '\r' | cut -c1-400
  echo; echo
}
{
  encabezado "HARD-03 (SINS-14) Rutas internas de identidad y Actuator — OWASP A05"
  dentro "Ruta interna de identidad, sin autenticacion" "http://ms-identidad:8081/internal/v1/users/$DOCTOR"
  dentro "Detalle de salud de identidad, sin autenticacion" "http://ms-identidad:8081/actuator/health"
  probar "Ruta interna de identidad directo desde el host :8081" - "http://localhost:8081/internal/v1/users/$DOCTOR"
} > "$OUT/05-hard03-internas-actuator.txt" 2>&1

# --- HARD-04: cabeceras de seguridad y banner ------------------------------------------------------
CABECERAS="strict-transport-security|x-content-type-options|content-security-policy|referrer-policy|x-frame-options|cross-origin-resource-policy|server"
{
  encabezado "HARD-04 (SINS-15) Cabeceras de seguridad del gateway"
  probar "Respuesta 200 del gateway" "$CABECERAS" "$BASE/actuator/health"
  probar "Respuesta 401 del gateway (token invalido)" "$CABECERAS" "$BASE/api/v1/encuentros" -H "Authorization: Bearer no-es-un-jwt"
} > "$OUT/06-hard04-cabeceras.txt" 2>&1

echo "Evidencia de la fase '$FASE' guardada en $OUT"
