#!/usr/bin/env bash
# Pruebas de humo de ms-agenda contra una instancia en ejecucion.
B=${1:-http://localhost:8087}
pass=0; fail=0
check() { if [ "$2" = "$3" ]; then echo "  OK   $1 ($3)"; pass=$((pass+1));
          else echo "  FALLA $1: esperado $2, obtenido $3"; fail=$((fail+1)); cat /tmp/abody.json 2>/dev/null | head -3; fi }
code() { curl -s "${ID[@]}" -o /tmp/abody.json -w '%{http_code}' "$@"; }
jq_() { python -c "import json,sys;d=json.load(sys.stdin);print(d$1)"; }
J='Content-Type: application/json'

ORG=11111111-1111-1111-1111-111111111111
BRANCH=22222222-2222-2222-2222-222222222222
PROF=33333333-3333-3333-3333-333333333333
PROFORG=44444444-4444-4444-4444-444444444444
PAT=55555555-5555-5555-5555-555555555555
SVC=66666666-6666-6666-6666-666666666666
USR=77777777-7777-7777-7777-777777777777
PAT2=88888888-8888-8888-8888-888888888888
# Identidad que propagaria el gateway: el servicio deniega lo que el token no permite (RBAC-04).
PERMS=scheduling.appointment.attend,scheduling.appointment.cancel,scheduling.appointment.check_in,scheduling.appointment.confirm,scheduling.appointment.create,scheduling.appointment.no_show,scheduling.appointment.read,scheduling.appointment.reschedule,scheduling.availability.create,scheduling.availability.deactivate,scheduling.availability.read,scheduling.block.create,scheduling.block.delete,scheduling.block.read,scheduling.reservation.cancel,scheduling.reservation.create,scheduling.reservation.mark_in_use,scheduling.reservation.read,scheduling.reservation.release,scheduling.resource.change_status,scheduling.resource.create,scheduling.resource.read,scheduling.resource.update
ID=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USR" -H "X-Permissions: $PERMS")

echo "== ms-agenda =="
check "ping" 200 "$(code $B/api/v1/scheduling/ping)"
check "health" 200 "$(code $B/actuator/health)"

# ---------------------------------------------------------------- recursos
CODE="C$RANDOM"
R=$(curl -s "${ID[@]}" -X POST $B/api/v1/scheduling/resources -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"code\":\"$CODE\",
  \"name\":\"Consultorio de prueba\",\"type\":\"OFFICE\",\"floor\":\"1\",
  \"peopleCapacity\":2,\"preparationMinutes\":10,\"requiresSterilization\":false,
  \"equipment\":\"{\\\"camilla\\\":true}\"}")
RES=$(echo "$R" | jq_ "['publicId']")
echo "  recurso creado: $RES"
check "recurso duplicado" 409 "$(code -X POST $B/api/v1/scheduling/resources -H "$J" -d "{\"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"code\":\"$CODE\",\"name\":\"Otro\",\"type\":\"OFFICE\"}")"
check "recurso con tipo invalido" 400 "$(code -X POST $B/api/v1/scheduling/resources -H "$J" -d "{\"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"code\":\"X$RANDOM\",\"name\":\"Otro\",\"type\":\"NO_EXISTE\"}")"
check "obtener recurso" 200 "$(code $B/api/v1/scheduling/resources/$RES)"
check "recurso inexistente" 404 "$(code $B/api/v1/scheduling/resources/00000000-0000-0000-0000-000000000000)"
check "listar recursos por sucursal" 200 "$(code "$B/api/v1/scheduling/resources?organizationPublicId=$ORG&branchPublicId=$BRANCH")"
check "actualizar recurso" 200 "$(code -X PATCH $B/api/v1/scheduling/resources/$RES -H "$J" -d '{"name":"Consultorio 1 renombrado","peopleCapacity":3}')"

# ---------------------------------------------------------- disponibilidad
TOMORROW=$(python -c "import datetime;print((datetime.date.today()+datetime.timedelta(days=1)).isoformat())")
DOW=$(python -c "import datetime;print((datetime.date.today()+datetime.timedelta(days=1)).isoweekday())")
check "crear disponibilidad" 201 "$(code -X POST $B/api/v1/scheduling/availabilities -H "$J" -d "{
  \"professionalOrganizationPublicId\":\"$PROFORG\",\"dayOfWeek\":$DOW,
  \"startTime\":\"08:00:00\",\"endTime\":\"12:00:00\",\"slotDurationMinutes\":30,
  \"simultaneousCapacity\":1,\"modality\":\"IN_PERSON\",\"preferredResourcePublicId\":\"$RES\",
  \"validFrom\":\"2026-01-01\"}")"
AV=$(cat /tmp/abody.json | jq_ "['id']")
check "disponibilidad duplicada" 409 "$(code -X POST $B/api/v1/scheduling/availabilities -H "$J" -d "{
  \"professionalOrganizationPublicId\":\"$PROFORG\",\"dayOfWeek\":$DOW,
  \"startTime\":\"08:00:00\",\"endTime\":\"12:00:00\",\"validFrom\":\"2026-01-01\"}")"
check "disponibilidad con horas invertidas" 400 "$(code -X POST $B/api/v1/scheduling/availabilities -H "$J" -d "{
  \"professionalOrganizationPublicId\":\"$PROFORG\",\"dayOfWeek\":$DOW,
  \"startTime\":\"12:00:00\",\"endTime\":\"08:00:00\",\"validFrom\":\"2026-01-01\"}")"
check "dia de semana fuera de rango" 400 "$(code -X POST $B/api/v1/scheduling/availabilities -H "$J" -d "{
  \"professionalOrganizationPublicId\":\"$PROFORG\",\"dayOfWeek\":9,
  \"startTime\":\"08:00:00\",\"endTime\":\"12:00:00\",\"validFrom\":\"2026-01-01\"}")"
check "listar disponibilidades" 200 "$(code "$B/api/v1/scheduling/availabilities?professionalOrganizationPublicId=$PROFORG")"

# --------------------------------------------------------------------- huecos
check "huecos del dia" 200 "$(code "$B/api/v1/scheduling/availability/slots?organizationPublicId=$ORG&professionalOrganizationPublicId=$PROFORG&healthProfessionalPublicId=$PROF&date=$TOMORROW")"
SLOTS=$(cat /tmp/abody.json | jq_ "['totalSlots']")
echo "  huecos libres para $TOMORROW: $SLOTS"
[ "$SLOTS" = "8" ] && { echo "  OK   se generaron 8 huecos de 30 min entre 08:00 y 12:00"; pass=$((pass+1)); } \
                   || { echo "  FALLA huecos: esperado 8, obtenido $SLOTS"; fail=$((fail+1)); }
check "huecos de fecha pasada" 400 "$(code "$B/api/v1/scheduling/availability/slots?organizationPublicId=$ORG&professionalOrganizationPublicId=$PROFORG&healthProfessionalPublicId=$PROF&date=2020-01-01")"

# ----------------------------------------------------------------------- citas
S1="${TOMORROW}T09:00:00-04:00"; E1="${TOMORROW}T09:30:00-04:00"
A=$(curl -s "${ID[@]}" -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT\",
  \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
  \"startsAt\":\"$S1\",\"endsAt\":\"$E1\",\"modality\":\"IN_PERSON\",\"channelOrigin\":\"RECEPTION\",
  \"reasonForVisit\":\"Control\",\"estimatedPriceBob\":150.00,
  \"physicalResourcePublicId\":\"$RES\",\"createdByUserPublicId\":\"$USR\"}")
APT=$(echo "$A" | jq_ "['publicId']")
echo "  cita creada: $APT"
[ -n "$APT" ] && { echo "  OK   crear cita con reserva de consultorio"; pass=$((pass+1)); } || { echo "  FALLA crear cita"; fail=$((fail+1)); }

# --- lo importante del sprint: el solape debe devolver 409
check "SOLAPE de profesional devuelve 409" 409 "$(code -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT2\",
  \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
  \"startsAt\":\"${TOMORROW}T09:15:00-04:00\",\"endsAt\":\"${TOMORROW}T09:45:00-04:00\"}")"
check "cita pegada al final si entra (09:30-10:00)" 201 "$(code -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT2\",
  \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
  \"startsAt\":\"${TOMORROW}T09:30:00-04:00\",\"endsAt\":\"${TOMORROW}T10:00:00-04:00\"}")"
APT2=$(cat /tmp/abody.json | jq_ "['publicId']")
check "SOLAPE de recurso devuelve 409" 409 "$(code -X POST $B/api/v1/scheduling/reservations -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"physicalResourcePublicId\":\"$RES\",
  \"startsAt\":\"${TOMORROW}T09:10:00-04:00\",\"endsAt\":\"${TOMORROW}T09:20:00-04:00\",\"purpose\":\"Limpieza\"}")"
check "reserva del recurso en otra franja" 201 "$(code -X POST $B/api/v1/scheduling/reservations -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"physicalResourcePublicId\":\"$RES\",
  \"startsAt\":\"${TOMORROW}T15:00:00-04:00\",\"endsAt\":\"${TOMORROW}T16:00:00-04:00\",\"purpose\":\"Mantenimiento\"}")"
RSV=$(cat /tmp/abody.json | jq_ "['id']")
check "rango invertido rechazado" 400 "$(code -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT\",
  \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
  \"startsAt\":\"${TOMORROW}T11:00:00-04:00\",\"endsAt\":\"${TOMORROW}T10:00:00-04:00\"}")"
check "campos obligatorios faltantes" 400 "$(code -X POST $B/api/v1/scheduling/appointments -H "$J" -d '{"branchPublicId":null}')"

check "un hueco menos tras las 2 citas" 200 "$(code "$B/api/v1/scheduling/availability/slots?organizationPublicId=$ORG&professionalOrganizationPublicId=$PROFORG&healthProfessionalPublicId=$PROF&date=$TOMORROW")"
SLOTS2=$(cat /tmp/abody.json | jq_ "['totalSlots']")
[ "$SLOTS2" = "6" ] && { echo "  OK   quedan 6 huecos (2 ocupados)"; pass=$((pass+1)); } \
                    || { echo "  FALLA huecos tras citas: esperado 6, obtenido $SLOTS2"; fail=$((fail+1)); }

check "obtener cita" 200 "$(code $B/api/v1/scheduling/appointments/$APT)"
check "cita inexistente" 404 "$(code $B/api/v1/scheduling/appointments/00000000-0000-0000-0000-000000000000)"
check "buscar citas de la organizacion" 200 "$(code "$B/api/v1/scheduling/appointments?organizationPublicId=$ORG&healthProfessionalPublicId=$PROF")"
check "contrato interno de cita" 200 "$(code $B/internal/v1/appointments/$APT)"

# ------------------------------------------------------- maquina de estados
check "transicion invalida REQUESTED -> IN_PROGRESS" 409 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/start -H "$J" -d '{}')"
check "confirmar" 200 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/confirm -H "$J" -d "{\"userAccountPublicId\":\"$USR\"}")"
check "confirmar dos veces" 409 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/confirm -H "$J" -d '{}')"
check "check-in" 200 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/check-in -H "$J" -d '{}')"
check "iniciar atencion" 200 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/start -H "$J" -d '{}')"
check "completar" 200 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/complete -H "$J" -d '{}')"
check "cancelar una cita ya atendida" 409 "$(code -X POST $B/api/v1/scheduling/appointments/$APT/cancel -H "$J" -d '{"reason":"tarde"}')"
check "historial de estados" 200 "$(code $B/api/v1/scheduling/appointments/$APT/history)"
HN=$(cat /tmp/abody.json | python -c 'import json,sys;print(len(json.load(sys.stdin)))')
[ "$HN" = "5" ] && { echo "  OK   el historial registro 5 cambios de estado"; pass=$((pass+1)); } \
                || { echo "  FALLA historial: esperado 5 registros, obtenido $HN"; fail=$((fail+1)); }

# ------------------------------------------------------------- reprogramacion
check "reprogramar la segunda cita" 201 "$(code -X POST $B/api/v1/scheduling/appointments/$APT2/reschedule -H "$J" -d "{
  \"startsAt\":\"${TOMORROW}T11:00:00-04:00\",\"endsAt\":\"${TOMORROW}T11:30:00-04:00\",
  \"reason\":\"El paciente pidio otro horario\",\"userAccountPublicId\":\"$USR\"}")"
NEW=$(cat /tmp/abody.json | jq_ "['sourceAppointmentPublicId']")
[ "$NEW" = "$APT2" ] && { echo "  OK   la cita nueva referencia a la original"; pass=$((pass+1)); } \
                     || { echo "  FALLA source_appointment_id: esperado $APT2, obtenido $NEW"; fail=$((fail+1)); }
check "la cita original quedo RESCHEDULED" 200 "$(code $B/api/v1/scheduling/appointments/$APT2)"
ST=$(cat /tmp/abody.json | jq_ "['status']")
[ "$ST" = "RESCHEDULED" ] && { echo "  OK   estado de la cita original: RESCHEDULED"; pass=$((pass+1)); } \
                          || { echo "  FALLA estado original: obtenido $ST"; fail=$((fail+1)); }

# ------------------------------------------------------------------- bloqueos
check "crear bloqueo del profesional" 201 "$(code -X POST $B/api/v1/scheduling/blocks -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"healthProfessionalPublicId\":\"$PROF\",\"branchPublicId\":\"$BRANCH\",
  \"type\":\"TRAINING\",\"startsAt\":\"${TOMORROW}T14:00:00-04:00\",\"endsAt\":\"${TOMORROW}T18:00:00-04:00\",
  \"reason\":\"Capacitacion\",\"createdByUserPublicId\":\"$USR\"}")"
BLK=$(cat /tmp/abody.json | jq_ "['id']")
check "bloqueo sin destino" 400 "$(code -X POST $B/api/v1/scheduling/blocks -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"type\":\"OTHER\",
  \"startsAt\":\"${TOMORROW}T14:00:00-04:00\",\"endsAt\":\"${TOMORROW}T18:00:00-04:00\"}")"
check "cita dentro del bloqueo devuelve 409" 409 "$(code -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT\",
  \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
  \"startsAt\":\"${TOMORROW}T15:00:00-04:00\",\"endsAt\":\"${TOMORROW}T15:30:00-04:00\"}")"
check "listar bloqueos" 200 "$(code "$B/api/v1/scheduling/blocks?organizationPublicId=$ORG&healthProfessionalPublicId=$PROF")"
check "eliminar bloqueo" 204 "$(code -X DELETE $B/api/v1/scheduling/blocks/$BLK)"

# -------------------------------------------------------------------- reservas
check "listar reservas" 200 "$(code "$B/api/v1/scheduling/reservations?organizationPublicId=$ORG&physicalResourcePublicId=$RES")"
check "liberar reserva" 200 "$(code -X POST $B/api/v1/scheduling/reservations/$RSV/release)"
check "cancelar reserva" 200 "$(code -X DELETE $B/api/v1/scheduling/reservations/$RSV)"
check "desactivar disponibilidad" 200 "$(code -X DELETE $B/api/v1/scheduling/availabilities/$AV)"

# ------------------------------------------- recurso fuera de servicio no reserva
check "poner recurso fuera de servicio" 200 "$(code -X PATCH $B/api/v1/scheduling/resources/$RES/status -H "$J" -d '{"status":"OUT_OF_SERVICE"}')"
check "reservar recurso fuera de servicio" 409 "$(code -X POST $B/api/v1/scheduling/reservations -H "$J" -d "{
  \"organizationPublicId\":\"$ORG\",\"physicalResourcePublicId\":\"$RES\",
  \"startsAt\":\"${TOMORROW}T20:00:00-04:00\",\"endsAt\":\"${TOMORROW}T21:00:00-04:00\"}")"

echo "== ms-agenda: $pass correctas, $fail fallidas =="
[ "$fail" -eq 0 ]
