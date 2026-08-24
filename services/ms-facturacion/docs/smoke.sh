#!/bin/bash
# =====================================================================================
# Recorrido de humo de ms-facturacion contra el servicio en ejecucion.
#
# Reproduce el paso "Cobrar" del guion de la demostracion y verifica ademas los casos
# negativos: prestacion inexistente, punto de venta inexistente, 401 sin identidad,
# 403 por rol y 404 desde otra organizacion.
#
# Uso:
#   cd services/ms-facturacion
#   BASE=http://localhost:8090 ./docs/smoke.sh
#   # o a traves del gateway:
#   BASE=http://localhost:8080 ./docs/smoke.sh
#
# Todos los datos son ficticios y se generan en cada ejecucion. Las facturas producidas
# NO tienen validez tributaria.
# =====================================================================================
set -euo pipefail
BASE=${BASE:-http://localhost:8090}
ORG=00000000-0000-4000-8000-0000000000a1
OTHER_ORG=00000000-0000-4000-8000-0000000000a9
USER=00000000-0000-4000-8000-0000000000f1
BRANCH=00000000-0000-4000-8000-0000000000a2
CONSULTATION=00000000-0000-4000-8000-000000000501
XRAY=00000000-0000-4000-8000-000000000502
PATIENT=$(uuidgen | tr 'A-Z' 'a-z')
APPT=$(uuidgen | tr 'A-Z' 'a-z')
CORR=smoke-$(date +%s)
BODY=$(mktemp)

caja=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: RECEPTIONIST" -H "X-Correlation-Id: $CORR" -H "Content-Type: application/json")
otra=(-H "X-Organization-Id: $OTHER_ORG" -H "X-User-Id: $USER" -H "X-Roles: RECEPTIONIST" -H "Content-Type: application/json")
paciente=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: PATIENT" -H "Content-Type: application/json")

say()  { printf '\n\033[1m%s\033[0m\n' "$1"; }
code() { curl -s -o "$BODY" -w '%{http_code}' "$@"; }
# Recibe una expresion Python sobre 'd', el cuerpo de la ultima respuesta.
json() { python3 -c "import json;d=json.load(open('$BODY'));print($1)"; }

say "1. Ping (ruta publica)"
S=$(code "$BASE/api/v1/billing/ping")
echo "  HTTP $S  $(cat "$BODY")"

say "2. Emitir factura: consulta + dos rayos X con descuento"
S=$(code -X POST "$BASE/api/v1/facturas" "${caja[@]}" -d "{
  \"patientId\": \"$PATIENT\",
  \"branchId\": \"$BRANCH\",
  \"items\": [
    { \"serviceOfferingId\": \"$CONSULTATION\", \"quantity\": 1, \"appointmentId\": \"$APPT\" },
    { \"serviceOfferingId\": \"$XRAY\", \"quantity\": 2, \"discountAmount\": \"50.00\" }
  ]
}")
INVOICE=$(json "d['id']")
NUMBER=$(json "d['invoiceNumber']")
TOTAL=$(json "d['totalAmount']")
STATUS=$(json "d['status']")
CAPTION=$(json "d['caption']")
SUMA=$(python3 -c "import json;d=json.load(open('$BODY'));print(sum(float(i['subtotal']) for i in d['items']))")
echo "  HTTP $S  invoiceId=$INVOICE  numero=$NUMBER"
echo "  total=$TOTAL  suma de detalles=$SUMA  cuadra: $([ "$(python3 -c "print(abs(float('$TOTAL')-$SUMA)<0.005)")" = True ] && echo SI || echo NO)"
echo "  estado=$STATUS  leyenda=$CAPTION"

say "3. Segunda factura: el correlativo avanza y no se repite"
S=$(code -X POST "$BASE/api/v1/facturas" "${caja[@]}" -d "{
  \"patientId\": \"$(uuidgen | tr 'A-Z' 'a-z')\",
  \"items\": [ { \"serviceOfferingId\": \"$CONSULTATION\" } ]
}")
NUMBER2=$(json "d['invoiceNumber']")
echo "  HTTP $S  numero=$NUMBER2  correlativo distinto: $([ "$NUMBER" != "$NUMBER2" ] && echo SI || echo NO)"

say "4. Consultar la factura (GET listo para la coleccion E2E)"
S=$(code "$BASE/api/v1/facturas/$INVOICE" "${caja[@]}")
echo "  HTTP $S  numero=$(json "d['invoiceNumber']")  total=$(json "d['totalAmount']")  demo=$(json "d['demo']")"
echo "  items=$(json "len(d['items'])")  cuf=$(json "d['cuf']")"

say "5. Listado por paciente"
S=$(code "$BASE/api/v1/facturas?patientId=$PATIENT" "${caja[@]}")
echo "  HTTP $S  facturas=$(json "d['totalElements']")"

say "6. Prestacion inexistente -> 422"
S=$(code -X POST "$BASE/api/v1/facturas" "${caja[@]}" -d "{
  \"patientId\": \"$PATIENT\",
  \"items\": [ { \"serviceOfferingId\": \"00000000-0000-4000-8000-0000000009ff\" } ]
}")
echo "  HTTP $S  code=$(json "d['code']")"

say "7. Punto de venta inexistente -> 404"
S=$(code -X POST "$BASE/api/v1/facturas" "${caja[@]}" -d "{
  \"patientId\": \"$PATIENT\",
  \"pointOfSale\": { \"taxBranchCode\": 9, \"pointOfSaleCode\": 9 },
  \"items\": [ { \"serviceOfferingId\": \"$CONSULTATION\" } ]
}")
echo "  HTTP $S  code=$(json "d['code']")"

say "8. Factura sin items -> 400"
S=$(code -X POST "$BASE/api/v1/facturas" "${caja[@]}" -d "{ \"patientId\": \"$PATIENT\", \"items\": [] }")
echo "  HTTP $S  code=$(json "d['code']")"

say "9. Sin identidad propagada -> 401"
S=$(code "$BASE/api/v1/facturas/$INVOICE" -H "Content-Type: application/json")
echo "  HTTP $S  code=$(json "d['code']")"

say "10. Rol sin permiso (PATIENT) -> 403"
S=$(code "$BASE/api/v1/facturas/$INVOICE" "${paciente[@]}")
echo "  HTTP $S  code=$(json "d['code']")"

say "11. Factura de otra organizacion -> 404 (no 403: no se revela su existencia)"
S=$(code "$BASE/api/v1/facturas/$INVOICE" "${otra[@]}")
echo "  HTTP $S  code=$(json "d['code']")"

rm -f "$BODY"
printf '\n\033[1mRecorrido terminado. Las facturas emitidas NO tienen validez tributaria.\033[0m\n'
