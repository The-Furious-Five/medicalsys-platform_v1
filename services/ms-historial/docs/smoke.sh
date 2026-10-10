#!/bin/bash
# =====================================================================================
# Recorrido de humo de ms-historial contra el servicio en ejecucion.
#
# Reproduce el guion de la demostracion de punta a punta y verifica ademas los casos
# negativos: cita duplicada, CIE-10 invalido, encuentro firmado inmutable, 401 sin
# identidad, 403 por rol y 404 desde otra organizacion.
#
# Uso:
#   cd services/ms-historial
#   BASE=http://localhost:8088 ./docs/smoke.sh
#
# Todos los datos son ficticios y se generan en cada ejecucion.
# =====================================================================================
set -euo pipefail
BASE=${BASE:-http://localhost:8088}
ORG=00000000-0000-4000-8000-0000000000a1
USER=00000000-0000-4000-8000-0000000000f1
PROF=00000000-0000-4000-8000-0000000000e1
BRANCH=00000000-0000-4000-8000-0000000000a2
DOC=00000000-0000-4000-8000-000000000d99
PATIENT=$(uuidgen | tr 'A-Z' 'a-z')
APPT=$(uuidgen | tr 'A-Z' 'a-z')
CORR=smoke-$(date +%s)

# Permisos que cada rol tiene en clinical.* segun la asignacion inicial de ms-identidad (RBAC-01/03):
# desde RBAC-03 el servicio autoriza por X-Permissions, no por X-Roles.
MED_PERMISSIONS="clinical.addendum.create,clinical.addendum.read,clinical.allergy.create,clinical.allergy.read,clinical.allergy.update,clinical.background.create,clinical.background.deactivate,clinical.background.read,clinical.diagnosis.create,clinical.diagnosis.read,clinical.diagnosis.update,clinical.encounter.open,clinical.encounter.read,clinical.encounter.sign,clinical.encounter.update_notes,clinical.encounter.void,clinical.narrative.read,clinical.prescription.attach_document,clinical.prescription.change_status,clinical.prescription.create,clinical.prescription.read,clinical.procedure.create,clinical.procedure.read,clinical.record.create,clinical.record.read,clinical.record.share,clinical.study_order.change_status,clinical.study_order.create,clinical.study_order.read,clinical.study_order.schedule_collection,clinical.study_result.create,clinical.study_result.read,clinical.vital_sign.create,clinical.vital_sign.read"
REC_PERMISSIONS="clinical.allergy.read,clinical.background.read,clinical.diagnosis.read,clinical.encounter.read,clinical.prescription.attach_document,clinical.prescription.change_status,clinical.prescription.read,clinical.record.create,clinical.record.read,clinical.study_order.change_status,clinical.study_order.read,clinical.study_order.schedule_collection,clinical.study_result.create,clinical.study_result.read,clinical.vital_sign.create,clinical.vital_sign.read"
med=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: PHYSICIAN" -H "X-Permissions: $MED_PERMISSIONS" -H "X-Correlation-Id: $CORR" -H "Content-Type: application/json")
rec=(-H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: RECEPTIONIST" -H "X-Permissions: $REC_PERMISSIONS" -H "Content-Type: application/json")

say() { printf '\n\033[1m%s\033[0m\n' "$1"; }
code() { curl -s -o /tmp/body.json -w '%{http_code}' "$@"; }

say "1. Expediente (primera vez -> 201)"
S1=$(code -X POST "$BASE/api/v1/expedientes" "${med[@]}" -d "{\"patientId\":\"$PATIENT\",\"branchId\":\"$BRANCH\"}")
REC=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['id'])")
NUM=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['recordNumber'])")
echo "  HTTP $S1  recordId=$REC  recordNumber=$NUM"

say "2. Idempotencia (segunda vez -> 200, mismo id)"
S2=$(code -X POST "$BASE/api/v1/expedientes" "${med[@]}" -d "{\"patientId\":\"$PATIENT\",\"branchId\":\"$BRANCH\"}")
REC2=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['id'])")
echo "  HTTP $S2  mismo expediente: $([ "$REC" = "$REC2" ] && echo SI || echo NO)"

say "3. Encuentro ligado a la cita"
S3=$(code -X POST "$BASE/api/v1/encuentros" "${med[@]}" \
  -d "{\"patientId\":\"$PATIENT\",\"appointmentId\":\"$APPT\",\"professionalId\":\"$PROF\",\"branchId\":\"$BRANCH\",\"type\":\"INITIAL_CONSULTATION\"}")
ENC=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['id'])")
echo "  HTTP $S3  encounterId=$ENC"

say "4. Cita duplicada -> 409"
S4=$(code -X POST "$BASE/api/v1/encuentros" "${med[@]}" \
  -d "{\"patientId\":\"$PATIENT\",\"appointmentId\":\"$APPT\",\"professionalId\":\"$PROF\",\"branchId\":\"$BRANCH\"}")
echo "  HTTP $S4  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"

say "5. Narrativa SOAP (se cifra antes de persistir)"
S5=$(code -X PUT "$BASE/api/v1/encuentros/$ENC/notas" "${med[@]}" \
  -d '{"chiefComplaint":"Cefalea de tres dias","subjective":"Paciente ficticio refiere dolor frontal","objective":"Sin signos de alarma","assessment":"Cefalea tensional","plan":"Analgesia y control","suggestedNextAppointmentAt":"2026-09-01"}')
echo "  HTTP $S5  encrypted=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['encrypted'])")"

say "6. Diagnostico CIE-10 valido"
S6=$(code -X POST "$BASE/api/v1/encuentros/$ENC/diagnosticos" "${med[@]}" \
  -d '{"icd10Code":"J00","type":"FINAL","primary":true,"observation":"Cuadro leve"}')
echo "  HTTP $S6  icd10=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['icd10Code'])")"

say "7. Diagnostico CIE-10 invalido -> 422"
S7=$(code -X POST "$BASE/api/v1/encuentros/$ENC/diagnosticos" "${med[@]}" -d '{"icd10Code":"ZZZ999"}')
echo "  HTTP $S7  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"

say "8. Orden de RAYOS X -> estado solicitada"
S8=$(code -X POST "$BASE/api/v1/encuentros/$ENC/ordenes-estudio" "${med[@]}" \
  -d '{"studyTypeCode":"X_RAY","priority":"ROUTINE","instructions":"Placa de torax","icd10Code":"J00"}')
ORD=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['id'])")
echo "  HTTP $S8  orderId=$ORD  status=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['status'])")  expediente=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['medicalRecordId'])")"

say "9. Receta"
S9=$(code -X POST "$BASE/api/v1/encuentros/$ENC/recetas" "${med[@]}" \
  -d '{"validityDays":30,"generalInstructions":"Tomar con alimentos","items":[{"medicationCode":"PARACETAMOL_500","dose":"500 mg","administrationRoute":"ORAL","frequencyHours":8,"durationDays":3,"quantity":9,"unit":"comprimido"}]}')
echo "  HTTP $S9  folio=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['folio'])")"

say "10. Firmar encuentro"
S10=$(code -X POST "$BASE/api/v1/encuentros/$ENC/firma" "${med[@]}")
echo "  HTTP $S10  status=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['status'])")  contentHash=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['contentHash'][:16]+'...')")"

say "11. Editar encuentro firmado -> 422 (inmutable)"
S11=$(code -X PUT "$BASE/api/v1/encuentros/$ENC/notas" "${med[@]}" -d '{"subjective":"intento de sobrescritura"}')
echo "  HTTP $S11  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"

say "12. Addendum sobre encuentro firmado"
S12=$(code -X POST "$BASE/api/v1/encuentros/$ENC/addendums" "${med[@]}" \
  -d "{\"professionalId\":\"$PROF\",\"reason\":\"CORRECTION\",\"text\":\"Se aclara la dosis indicada\"}")
echo "  HTTP $S12  reason=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['reason'])")"

say "13. Agendar cita de toma"
S13=$(code -X PATCH "$BASE/api/v1/ordenes-estudio/$ORD/cita-toma" "${rec[@]}" \
  -d "{\"collectionAppointmentId\":\"$(uuidgen | tr 'A-Z' 'a-z')\"}")
echo "  HTTP $S13  status=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['status'])")"

say "14. Registrar resultado con documentId -> orden informada"
S14=$(code -X POST "$BASE/api/v1/ordenes-estudio/$ORD/resultados" "${rec[@]}" \
  -d "{\"textValue\":\"Sin hallazgos patologicos\",\"documentId\":\"$DOC\",\"reportedById\":\"$PROF\"}")
echo "  HTTP $S14  documentId=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['documentId'])")  status=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['status'])")"
S14b=$(code "$BASE/api/v1/ordenes-estudio/$ORD" "${rec[@]}")
echo "  orden ahora: $(python3 -c "import json;print(json.load(open('/tmp/body.json'))['status'])")"

say "15. Narrativa: medico la ve, recepcion no"
code "$BASE/api/v1/encuentros/$ENC" "${med[@]}" > /dev/null
echo "  PHYSICIAN  -> narrative.subjective = $(python3 -c "import json;d=json.load(open('/tmp/body.json'));print(d.get('narrative',{}).get('subjective'))")"
code "$BASE/api/v1/encuentros/$ENC" "${rec[@]}" > /dev/null
echo "  RECEPTIONIST -> narrative = $(python3 -c "import json;print(json.load(open('/tmp/body.json')).get('narrative'))")"

say "16. Sin identidad -> 401 / rol insuficiente -> 403"
echo "  sin headers: $(code "$BASE/api/v1/encuentros/$ENC")  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"
echo "  recepcion firma: $(code -X POST "$BASE/api/v1/encuentros/$ENC/firma" "${rec[@]}")  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"

say "17. Otra organizacion no ve el expediente -> 404"
echo "  $(code "$BASE/api/v1/expedientes/$REC" -H "X-Organization-Id: 00000000-0000-4000-8000-0000000000a9" -H "X-Roles: PHYSICIAN" -H "X-Permissions: $MED_PERMISSIONS")  code=$(python3 -c "import json;print(json.load(open('/tmp/body.json'))['code'])")"

say "18. Rutas internas para agenda y documental"
echo "  /internal/v1/ordenes-estudio/{id}: $(code "$BASE/internal/v1/ordenes-estudio/$ORD" -H "X-Organization-Id: $ORG")"
cat /tmp/body.json | python3 -m json.tool | head -12

say "19. correlationId propagado"
curl -s -D- -o /dev/null "$BASE/api/v1/encuentros/$ENC" "${med[@]}" | grep -i x-correlation-id
