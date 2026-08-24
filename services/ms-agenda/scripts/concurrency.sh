#!/usr/bin/env bash
# Diez recepcionistas piden la MISMA franja del mismo profesional al mismo tiempo.
# Solo una cita debe quedar creada; el resto debe recibir 409.
B=${1:-http://localhost:8087}
J='Content-Type: application/json'
ORG=11111111-1111-1111-1111-111111111111
BRANCH=22222222-2222-2222-2222-222222222222
SVC=66666666-6666-6666-6666-666666666666
PROF=$(python -c "import uuid;print(uuid.uuid4())")
DAY=$(python -c "import datetime;print((datetime.date.today()+datetime.timedelta(days=3)).isoformat())")

echo "== concurrencia: 10 solicitudes simultaneas para ${DAY}T10:00 =="
rm -f /tmp/conc_*.code
for i in $(seq 1 10); do
  PAT=$(python -c "import uuid;print(uuid.uuid4())")
  curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/v1/scheduling/appointments -H "$J" -d "{
    \"organizationPublicId\":\"$ORG\",\"branchPublicId\":\"$BRANCH\",\"patientPublicId\":\"$PAT\",
    \"healthProfessionalPublicId\":\"$PROF\",\"serviceOfferingPublicId\":\"$SVC\",
    \"startsAt\":\"${DAY}T10:00:00-04:00\",\"endsAt\":\"${DAY}T10:30:00-04:00\"}" > /tmp/conc_$i.code &
done
wait

CODES=$(for f in /tmp/conc_*.code; do cat "$f"; echo; done)
CREATED=$(echo "$CODES" | grep -c '^201$')
CONFLICT=$(echo "$CODES" | grep -c '^409$')
OTHER=$(echo "$CODES" | grep -c -v '^201$\|^409$')
echo "  201 creadas   : $CREATED"
echo "  409 conflictos: $CONFLICT"
echo "  otros codigos : $OTHER  ($(cat /tmp/conc_*.code | tr '\n' ' '))"

STORED=$(curl -s "$B/api/v1/scheduling/appointments?organizationPublicId=$ORG&healthProfessionalPublicId=$PROF" \
  | python -c 'import json,sys;print(json.load(sys.stdin)["totalElements"])')
echo "  citas realmente guardadas para ese profesional: $STORED"

if [ "$CREATED" = "1" ] && [ "$CONFLICT" = "9" ] && [ "$STORED" = "1" ]; then
  echo "== OK: la restriccion EXCLUDE impidio la doble reserva =="
  exit 0
fi
echo "== FALLA: se esperaba 1 creada, 9 conflictos y 1 cita guardada =="
exit 1
