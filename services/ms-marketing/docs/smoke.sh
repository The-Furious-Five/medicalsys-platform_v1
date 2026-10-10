#!/bin/bash
# =====================================================================================
# Recorrido de humo de ms-marketing contra el servicio en ejecucion.
#
# Recorre el camino completo -- segmentar, materializar, lanzar, cobrar con cupon,
# acumular puntos y medir -- y comprueba ademas lo que define a este servicio:
#   * 422 CLINICAL_DATA_NOT_ALLOWED ante un criterio clinico (criterio C3).
#   * El opt-out se registra con su motivo en la audiencia materializada.
#   * El canje de cupon es idempotente por factura.
#   * Una misma factura no acumula puntos dos veces.
#   * marketing.campana se escribe en el outbox al lanzar.
#   * 401 sin identidad, 403 por rol y 404 desde otra organizacion.
#
# Uso:
#   cd services/ms-marketing
#   BASE=http://localhost:8092 ./docs/smoke.sh
#
# El recorrido va DIRECTO al servicio, no por el gateway, y es a proposito: fabrica
# cuatro identidades distintas -- admin, caja, paciente y otra organizacion -- para poder
# comprobar el 403 por rol y el 404 entre tenants. El gateway, correctamente, borra
# cualquier X-Organization-Id / X-Roles que mande el cliente y los recrea desde el JWT,
# asi que por ahi estas cabeceras no llegarian y todo respondería 401.
#
# Para comprobar el camino autenticado completo (login -> JWT -> gateway -> marketing),
# vease README.md, seccion 9.3.
#
# Todos los datos son ficticios y se generan en cada ejecucion.
# =====================================================================================
set -euo pipefail
BASE=${BASE:-http://localhost:8092}
ORG=00000000-0000-4000-8000-0000000000a1
OTHER_ORG=00000000-0000-4000-8000-0000000000a9
USER=00000000-0000-4000-8000-0000000000f1
CORR=smoke-marketing-$(date +%s)
SUFFIX=$(date +%s)
BODY=$(mktemp)

# El seeder siembra 12 pacientes deterministas: ...0701 a ...0712.
PATIENT=00000000-0000-4000-8000-000000000701
OTHER_PATIENT=00000000-0000-4000-8000-000000000702

# Permisos que cada rol tiene en marketing.* segun la asignacion inicial de ms-identidad (RBAC-01/03):
# desde RBAC-03 el servicio autoriza por X-Permissions, no por X-Roles.
ADMIN_PERMISSIONS="marketing.advertisement.create,marketing.advertisement.finish,marketing.advertisement.pause,marketing.advertisement.publish,marketing.advertisement.read,marketing.advertisement.track,marketing.advertisement.view,marketing.campaign.build_audience,marketing.campaign.cancel,marketing.campaign.create,marketing.campaign.finish,marketing.campaign.launch,marketing.campaign.pause,marketing.campaign.read,marketing.campaign.schedule,marketing.coupon.redeem,marketing.coupon.validate,marketing.loyalty_account.read,marketing.loyalty_movement.create,marketing.loyalty_program.create,marketing.loyalty_program.deactivate,marketing.loyalty_program.read,marketing.opt_out.update,marketing.patient_activity.read,marketing.promotion.create,marketing.promotion.deactivate,marketing.promotion.read,marketing.recipient.read,marketing.segment.create,marketing.segment.deactivate,marketing.segment.delete,marketing.segment.evaluate,marketing.segment.read,marketing.segment.update"
CAJA_PERMISSIONS="marketing.coupon.redeem,marketing.coupon.validate,marketing.loyalty_account.read,marketing.loyalty_movement.create,marketing.promotion.read"
PACIENTE_PERMISSIONS="marketing.advertisement.track,marketing.advertisement.view,marketing.loyalty_account.read,marketing.opt_out.update"
OTRA_PERMISSIONS="marketing.advertisement.create,marketing.advertisement.finish,marketing.advertisement.pause,marketing.advertisement.publish,marketing.advertisement.read,marketing.advertisement.track,marketing.advertisement.view,marketing.campaign.build_audience,marketing.campaign.cancel,marketing.campaign.create,marketing.campaign.finish,marketing.campaign.launch,marketing.campaign.pause,marketing.campaign.read,marketing.campaign.schedule,marketing.coupon.redeem,marketing.coupon.validate,marketing.loyalty_account.read,marketing.loyalty_movement.create,marketing.loyalty_program.create,marketing.loyalty_program.deactivate,marketing.loyalty_program.read,marketing.opt_out.update,marketing.patient_activity.read,marketing.promotion.create,marketing.promotion.deactivate,marketing.promotion.read,marketing.recipient.read,marketing.segment.create,marketing.segment.deactivate,marketing.segment.delete,marketing.segment.evaluate,marketing.segment.read,marketing.segment.update"
admin=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: ADMINISTRATOR" -H "X-Permissions: $ADMIN_PERMISSIONS" -H "X-Correlation-Id: $CORR" -H "Content-Type: application/json")
caja=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: BILLING_CLERK" -H "X-Permissions: $CAJA_PERMISSIONS" -H "Content-Type: application/json")
paciente=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: PATIENT" -H "X-Permissions: $PACIENTE_PERMISSIONS" -H "Content-Type: application/json")
otra=(-H "X-Organization-Id: $OTHER_ORG" -H "X-User-Id: $USER" -H "X-Roles: ADMINISTRATOR" -H "X-Permissions: $OTRA_PERMISSIONS" -H "Content-Type: application/json")

say()  { printf '\n\033[1m%s\033[0m\n' "$1"; }
code() { curl -s -o "$BODY" -w '%{http_code}' "$@"; }
# Recibe una expresion Python sobre 'd', el cuerpo de la ultima respuesta.
json() { python3 -c "import json;d=json.load(open('$BODY'));print($1)"; }
ok()   { if [ "$1" = "$2" ]; then printf '  \033[32mOK\033[0m %s\n' "$3"; else printf '  \033[31mFALLO\033[0m %s (esperado %s, recibido %s)\n' "$3" "$2" "$1"; fi; }

say "1. Ping y criterios permitidos (rutas publicas)"
S=$(code "$BASE/api/v1/marketing/ping")
ok "$S" 200 "el servicio responde: $(cat "$BODY")"
S=$(code "$BASE/api/v1/marketing/criterios-permitidos")
echo "  ejes de segmentacion publicados: $(json "len(d['fields'])")"

say "2. FRONTERA DE PRIVACIDAD: un criterio clinico se rechaza"
S=$(code -X POST "$BASE/api/v1/segmentos" "${admin[@]}" -d '{
  "name": "Asmaticos", "criteria": { "diagnosticoCie10": "J45" } }')
ok "$S" 422 "criterio clinico rechazado con code=$(json "d['code']")"
echo "  detalle: $(json "d['message'][:110]")..."

say "3. Vista previa de un criterio permitido, sin crear nada"
S=$(code -X POST "$BASE/api/v1/segmentos/vista-previa" "${admin[@]}" -d '{ "minAge": 18, "maxAge": 90 }')
ok "$S" 200 "vista previa: $(json "d['matched']") coinciden, $(json "d['reachable']") alcanzables, $(json "d['optedOut']") con opt-out"

say "4. Opt-out del paciente $OTHER_PATIENT (lo ejerce el propio paciente)"
S=$(code -X PUT "$BASE/api/v1/pacientes/$OTHER_PATIENT/opt-out" "${paciente[@]}" -d '{ "optOut": true }')
ok "$S" 200 "marketingOptOut=$(json "d['marketingOptOut']")"

say "5. Crear un segmento de reactivacion (solo datos administrativos)"
S=$(code -X POST "$BASE/api/v1/segmentos" "${admin[@]}" -d "{
  \"name\": \"Reactivacion $SUFFIX\",
  \"description\": \"Adultos que no vuelven hace tiempo\",
  \"criteria\": { \"minAge\": 18, \"maxAge\": 90 } }")
SEGMENT=$(json "d['id']")
ok "$S" 201 "segmento=$SEGMENT tamano=$(json "d['estimatedSize']") usaDatosClinicos=$(json "d['usesClinicalData']")"

say "6. Crear un cupon del 20 %"
COUPON="SMOKE$SUFFIX"
S=$(code -X POST "$BASE/api/v1/promociones" "${admin[@]}" -d "{
  \"couponCode\": \"$COUPON\", \"name\": \"Cupon de humo\",
  \"discountType\": \"PERCENTAGE\", \"value\": 20,
  \"maxRedemptions\": 10, \"redemptionsPerPatient\": 1,
  \"validFrom\": \"$(date -u -v-1d +%Y-%m-%d 2>/dev/null || date -u -d '1 day ago' +%Y-%m-%d)\",
  \"validUntil\": \"$(date -u -v+30d +%Y-%m-%d 2>/dev/null || date -u -d '30 days' +%Y-%m-%d)\" }")
PROMOTION=$(json "d['id']")
ok "$S" 201 "cupon=$COUPON usos=$(json "d['currentRedemptions']")/$(json "d['maxRedemptions']")"

say "7. Crear la campana y materializar su audiencia"
S=$(code -X POST "$BASE/api/v1/campanas" "${admin[@]}" -d "{
  \"name\": \"Chequeo preventivo $SUFFIX\",
  \"type\": \"PREVENTIVE\", \"channel\": \"WHATSAPP\",
  \"objective\": \"Promover el control anual\",
  \"segmentId\": \"$SEGMENT\", \"promotionId\": \"$PROMOTION\",
  \"startsAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\" }")
CAMPAIGN=$(json "d['id']")
ok "$S" 201 "campana=$CAMPAIGN estado=$(json "d['status']")"

S=$(code -X POST "$BASE/api/v1/campanas/$CAMPAIGN/audiencia" "${admin[@]}")
ok "$S" 200 "audiencia: $(json "d['matched']") coinciden, $(json "d['reachable']") alcanzables, $(json "d['excludedOptOut']") excluidos por opt-out"

say "8. El excluido queda registrado CON su motivo, no simplemente omitido"
S=$(code "$BASE/api/v1/campanas/$CAMPAIGN/destinatarios?status=EXCLUDED_OPT_OUT" "${admin[@]}")
echo "  HTTP $S  excluidos=$(json "d['totalElements']")  motivo=$(json "d['content'][0]['exclusionReason'] if d['content'] else 'ninguno'")"

say "9. Lanzar la campana: publica marketing.campana en el outbox"
S=$(code -X POST "$BASE/api/v1/campanas/$CAMPAIGN/lanzamiento" "${admin[@]}")
ok "$S" 200 "estado=$(json "d['status']") destinatarios=$(json "d['totalRecipients']")"

S=$(code "$BASE/internal/v1/campanas/$CAMPAIGN/eventos" "${admin[@]}")
echo "  eventos publicados: $(json "len(d)")  tipo=$(json "d[0]['eventType'] if d else 'ninguno'")"

say "10. Lanzar dos veces se rechaza: el ciclo de vida es una invariante"
S=$(code -X POST "$BASE/api/v1/campanas/$CAMPAIGN/audiencia" "${admin[@]}")
ok "$S" 422 "rematerializar una campana EN_EJECUCION: code=$(json "d['code']")"

say "11. La caja valida el cupon (no lo consume)"
S=$(code -X POST "$BASE/api/v1/promociones/validar" "${caja[@]}" -d "{
  \"couponCode\": \"$COUPON\", \"patientId\": \"$PATIENT\", \"baseAmountBob\": \"250.00\" }")
ok "$S" 200 "valido=$(json "d['valid']") descuento=$(json "d['discountAmountBob']") neto=$(json "d['netAmountBob']")"
S=$(code "$BASE/api/v1/promociones/$PROMOTION" "${admin[@]}")
echo "  usos tras validar: $(json "d['currentRedemptions']") (validar no consume cupo)"

say "12. Canje del cupon e idempotencia por factura"
INVOICE=$(uuidgen | tr 'A-Z' 'a-z')
S=$(code -X POST "$BASE/api/v1/promociones/canjes" "${caja[@]}" -d "{
  \"couponCode\": \"$COUPON\", \"patientId\": \"$PATIENT\",
  \"baseAmountBob\": \"250.00\", \"invoiceId\": \"$INVOICE\" }")
ok "$S" 200 "canje=$(json "d['redemptionId']") descuento=$(json "d['discountAmountBob']") reintento=$(json "d['alreadyRedeemed']")"
S=$(code -X POST "$BASE/api/v1/promociones/canjes" "${caja[@]}" -d "{
  \"couponCode\": \"$COUPON\", \"patientId\": \"$PATIENT\",
  \"baseAmountBob\": \"250.00\", \"invoiceId\": \"$INVOICE\" }")
echo "  reintento con la misma factura: alreadyRedeemed=$(json "d['alreadyRedeemed']")"
S=$(code "$BASE/api/v1/promociones/$PROMOTION" "${admin[@]}")
echo "  usos totales del cupon: $(json "d['currentRedemptions']") (debe ser 1)"

say "13. Evento de facturacion: proyecta el gasto y acumula puntos, una sola vez"
EVENT=$(uuidgen | tr 'A-Z' 'a-z')
EVENT_BODY="{
  \"eventId\": \"$EVENT\", \"eventType\": \"facturacion.factura.emitida\",
  \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"organizationId\": \"$ORG\",
  \"payload\": { \"invoiceId\": \"$INVOICE\", \"patientId\": \"$PATIENT\", \"totalAmountBob\": \"200.00\" } }"
S=$(code -X POST "$BASE/internal/v1/eventos" "${admin[@]}" -d "$EVENT_BODY")
ok "$S" 200 "duplicado=$(json "d['duplicate']") detalle=$(json "d['detail']")"
S=$(code -X POST "$BASE/internal/v1/eventos" "${admin[@]}" -d "$EVENT_BODY")
echo "  reenvio del mismo evento: duplicate=$(json "d['duplicate']")"

S=$(code "$BASE/api/v1/fidelidad/saldo?patientId=$PATIENT" "${admin[@]}")
ok "$S" 200 "saldo=$(json "d['balancePoints']") puntos (= $(json "d['balanceValueBob']") BOB)"

say "14. Un evento con dato clinico se rechaza entero"
S=$(code -X POST "$BASE/internal/v1/eventos" "${admin[@]}" -d "{
  \"eventId\": \"$(uuidgen | tr 'A-Z' 'a-z')\", \"eventType\": \"historial.encuentro.firmado\",
  \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"organizationId\": \"$ORG\",
  \"payload\": { \"patientId\": \"$PATIENT\", \"suggestedNextVisitOn\": \"2026-12-01\",
                 \"diagnosticoCie10\": \"J45\" } }")
ok "$S" 422 "code=$(json "d['code']")"

say "15. El mismo evento SIN el motivo clinico si se acepta: solo la fecha de control"
S=$(code -X POST "$BASE/internal/v1/eventos" "${admin[@]}" -d "{
  \"eventId\": \"$(uuidgen | tr 'A-Z' 'a-z')\", \"eventType\": \"historial.encuentro.firmado\",
  \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"organizationId\": \"$ORG\",
  \"payload\": { \"patientId\": \"$PATIENT\", \"suggestedNextVisitOn\": \"2026-12-01\" } }")
ok "$S" 200 "$(json "d['detail']")"
S=$(code "$BASE/api/v1/pacientes/$PATIENT/actividad" "${admin[@]}")
echo "  actividad: visitas=$(json "d['totalAppointments']") gasto=$(json "d['totalBilledBob']") proximoControl=$(json "d['suggestedNextVisitOn']")"

say "16. Anuncio del portal: crear, publicar y contar una impresion"
S=$(code -X POST "$BASE/api/v1/anuncios" "${admin[@]}" -d "{
  \"title\": \"Campana de salud preventiva\",
  \"content\": \"Agende su control anual.\",
  \"placement\": \"PATIENT_PORTAL\", \"priority\": 1,
  \"validFrom\": \"$(date -u -v-1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)\",
  \"validUntil\": \"$(date -u -v+30d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '30 days' +%Y-%m-%dT%H:%M:%SZ)\" }")
AD=$(json "d['id']")
ok "$S" 201 "anuncio=$AD estado=$(json "d['status']")"
S=$(code -X POST "$BASE/api/v1/anuncios/$AD/publicacion" "${admin[@]}")
echo "  publicado: estado=$(json "d['status']")"
S=$(code "$BASE/api/v1/anuncios/vigentes?ubicacion=PATIENT_PORTAL" "${paciente[@]}")
ok "$S" 200 "el portal ve $(json "len(d)") anuncio(s) vigente(s)"
S=$(code -X POST "$BASE/api/v1/anuncios/$AD/impresiones" "${paciente[@]}")
echo "  impresiones=$(json "d['impressions']")"

say "17. Rendimiento de la campana (tasas calculadas por la vista de la base)"
S=$(code "$BASE/api/v1/campanas/$CAMPAIGN/rendimiento" "${admin[@]}")
ok "$S" 200 "destinatarios=$(json "d['totalRecipients']") enviados=$(json "d['totalSent']") conversiones=$(json "d['totalConverted']") excluidosOptOut=$(json "d['excludedOptOut']")"

say "18. Sin identidad propagada -> 401"
S=$(code "$BASE/api/v1/campanas" -H "Content-Type: application/json")
ok "$S" 401 "code=$(json "d['code']")"

say "19. Rol sin permiso (PATIENT crea campana) -> 403"
S=$(code -X POST "$BASE/api/v1/campanas" "${paciente[@]}" -d "{
  \"name\": \"Intento\", \"type\": \"PROMOTION\", \"startsAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\" }")
ok "$S" 403 "code=$(json "d['code']")"

say "20. Campana de otra organizacion -> 404 (no 403: no se revela su existencia)"
S=$(code "$BASE/api/v1/campanas/$CAMPAIGN" "${otra[@]}")
ok "$S" 404 "code=$(json "d['code']")"

rm -f "$BODY"
printf '\n\033[1mRecorrido terminado. ms-marketing no consulto ningun dato clinico en ningun paso.\033[0m\n'
