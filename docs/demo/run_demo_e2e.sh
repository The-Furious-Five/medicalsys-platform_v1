#!/usr/bin/env bash
#
# MedicalSys — MVP-16 Demo E2E
#
# Reproduces the full clinical walking-skeleton flow (login -> org setup -> register
# patient/professional -> schedule appointment -> clinical encounter -> x-ray order ->
# upload result -> manual WhatsApp handoff -> demo invoice) entirely through ms-gateway,
# on a clean DB started from `docker compose up -d`.
#
# Built from a static read of every service's real Spring controllers (paths, roles,
# request/response DTOs) — NOT from the original planning doc, which had already
# diverged from what got implemented. See docs/demo/README.md for the full list of
# contract gaps found along the way and what was fixed vs. only documented.
#
# Requires: bash, curl, jq.
#
# Usage:
#   GATEWAY_BASE_URL=http://localhost:18080 ./run_demo_e2e.sh
#
# Exits non-zero if any step fails unexpectedly. Steps intentionally expecting an
# error (the 409 negative tests) are asserted explicitly and do not fail the run.

set -u
set -o pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-SanGabriel.Demo26!}"

PASS=0
FAIL=0
STEP_LOG=()

# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

# req METHOD PATH [BODY_JSON] [BEARER_TOKEN] [EXTRA_CURL_ARGS...]
# Leaves HTTP status in $STATUS and response body in $BODY.
req() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  shift 4 2>/dev/null || shift $#
  local -a curl_args=(-sS -o /tmp/medicalsys_demo_body.$$ -w '%{http_code}' -X "$method" "${GATEWAY_BASE_URL}${path}")
  curl_args+=(-H "Content-Type: application/json")
  curl_args+=(-H "X-Correlation-Id: demo-$(date +%s)-$RANDOM")
  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$body" ]]; then
    curl_args+=(-d "$body")
  fi
  curl_args+=("$@")
  STATUS=$(curl "${curl_args[@]}")
  BODY=$(cat /tmp/medicalsys_demo_body.$$ 2>/dev/null)
  rm -f /tmp/medicalsys_demo_body.$$
}

step() {
  local n="$1" title="$2"
  echo ""
  echo "=== Step ${n}: ${title} ==="
}

expect() {
  local n="$1" desc="$2" expected="$3" actual="$4"
  if [[ "$actual" == "$expected" ]]; then
    echo "  [PASS] ${desc} (HTTP ${actual})"
    PASS=$((PASS + 1))
    STEP_LOG+=("PASS  ${n}  ${desc}")
  else
    echo "  [FAIL] ${desc} (expected HTTP ${expected}, got HTTP ${actual})"
    echo "         Body: ${BODY}"
    FAIL=$((FAIL + 1))
    STEP_LOG+=("FAIL  ${n}  ${desc} (expected ${expected}, got ${actual})")
  fi
}

jqf() { echo "$BODY" | jq -r "$1"; }

# ---------------------------------------------------------------------------
# Step 1 — Login as ADMINISTRATOR (POST /api/v1/identity/auth/login)
# ---------------------------------------------------------------------------
step 1 "Login as ADMINISTRATOR (admin@medicalsys.demo)"
req POST /api/v1/identity/auth/login "$(jq -n --arg p "$DEMO_PASSWORD" '{email:"admin@medicalsys.demo",password:$p}')"
expect 1 "Admin login" 200 "$STATUS"
ADMIN_TOKEN=$(jqf '.accessToken')
ADMIN_ORG_ID=$(jqf '.user.organizationPublicId')
echo "  organizationId from admin JWT: ${ADMIN_ORG_ID}"

# ---------------------------------------------------------------------------
# Step 2 — Org setup: fetch the pre-seeded "Clinica Demo" tenant, its headquarters
# branch and its CONSULTATION / X_RAY service offerings (ms-tenencia via gateway).
#
# NOTE: there is no PLATFORM_ADMIN demo account, so this flow does NOT create a new
# organization (POST /api/v1/tenancy/organizacion is PLATFORM_ADMIN-only) — it uses
# the tenant tenancy's V2__seed_demo.sql already ships ("Clinica Demo"). This step
# only succeeds if ADMIN_ORG_ID above matches that seeded org's public_id
# (00000000-0000-4000-8000-0000000000a1). See README "Known contract gaps" #1.
# ---------------------------------------------------------------------------
step 2 "Org setup — GET current organization, branch, service offerings"
req GET /api/v1/tenancy/organizacion "" "$ADMIN_TOKEN"
expect 2 "GET current organization" 200 "$STATUS"
if [[ "$STATUS" != "200" ]]; then
  echo "  >>> Likely cause: IDENTITY_DEMO_ORGANIZATION (ms-identidad) does not match the"
  echo "      organization public_id seeded by ms-tenencia's V2__seed_demo.sql."
  echo "      See docs/demo/README.md, 'Known contract gaps' #1, for the exact fix."
fi
ORG_NAME=$(jqf '.tradeName')
echo "  organization: ${ORG_NAME}"

req GET /api/v1/tenancy/sucursales "" "$ADMIN_TOKEN"
expect 2 "GET branches" 200 "$STATUS"
BRANCH_ID=$(jqf '.content[0].id')
echo "  branchId: ${BRANCH_ID}"

req GET "/api/v1/tenancy/prestaciones?specialtyCode=MEDICINA_GENERAL" "" "$ADMIN_TOKEN"
CONSULT_OFFERING_ID=$(jqf '.content[] | select(.code=="CONSULTATION") | .id')
req GET "/api/v1/tenancy/prestaciones" "" "$ADMIN_TOKEN"
XRAY_OFFERING_ID=$(jqf '.content[] | select(.code=="X_RAY") | .id')
echo "  CONSULTATION offeringId: ${CONSULT_OFFERING_ID}"
echo "  X_RAY offeringId: ${XRAY_OFFERING_ID}"

# ---------------------------------------------------------------------------
# Step 3 — Login as RECEPTIONIST and register a patient (POST /api/v1/patients)
# ---------------------------------------------------------------------------
step 3 "Login as RECEPTIONIST and register a patient"
req POST /api/v1/identity/auth/login "$(jq -n --arg p "$DEMO_PASSWORD" '{email:"recepcion@medicalsys.demo",password:$p}')"
expect 3 "Receptionist login" 200 "$STATUS"
RECEP_TOKEN=$(jqf '.accessToken')

DOC_NUMBER="DEMO-$(date +%s)"
PATIENT_BODY=$(jq -n --arg dn "$DOC_NUMBER" '{
  documentType: "CI",
  documentNumber: $dn,
  documentComplement: "",
  givenNames: "Paciente",
  paternalSurname: "Demo",
  maternalSurname: "E2E",
  birthDate: "1990-05-14",
  sex: "M",
  phoneE164: "+59170000001",
  email: "paciente.demo@medicalsys.invalid",
  city: "La Paz",
  department: "LA_PAZ"
}')
req POST /api/v1/patients "$PATIENT_BODY" "$RECEP_TOKEN"
expect 3 "Register patient" 201 "$STATUS"
PATIENT_ID=$(jqf '.patientId')
PATIENT_PHONE=$(jqf '.phoneE164')
echo "  patientId: ${PATIENT_ID}"

# ---------------------------------------------------------------------------
# Step 4 — Negative test: registering the SAME document again must 409
# (unique constraint uq_patient_document_type_document_number_document_complement)
# ---------------------------------------------------------------------------
step 4 "[Negative] Duplicate patient registration -> 409"
req POST /api/v1/patients "$PATIENT_BODY" "$RECEP_TOKEN"
expect 4 "Duplicate patient rejected" 409 "$STATUS"

# ---------------------------------------------------------------------------
# Step 5 — Register a professional: create the identity user first (ADMINISTRATOR),
# then the professional record in ms-profesionales (this service enforces no
# role/auth itself — organizationId travels in the request body, not headers).
# ---------------------------------------------------------------------------
step 5 "Register professional (identity user + ms-profesionales record)"
DOC_EMAIL="doctor.demo.$(date +%s)@medicalsys.invalid"
USER_BODY=$(jq -n --arg email "$DOC_EMAIL" --arg pw "$DEMO_PASSWORD" --arg org "$ADMIN_ORG_ID" '{
  email: $email,
  fullName: "Profesional Demo E2E",
  password: $pw,
  organizationPublicId: $org,
  language: "es",
  activateImmediately: true,
  roles: [ { role: "PHYSICIAN", organizationPublicId: $org } ]
}')
req POST /api/v1/identity/users "$USER_BODY" "$ADMIN_TOKEN"
expect 5 "Create identity user for professional" 201 "$STATUS"
PROF_USER_ID=$(jqf '.publicId')

PROF_LICENSE="LIC-$(date +%s)"
PROF_BODY=$(jq -n \
  --arg userId "$PROF_USER_ID" \
  --arg lic "$PROF_LICENSE" \
  --arg org "$ADMIN_ORG_ID" \
  --arg branch "$BRANCH_ID" '{
  userAccountId: $userId,
  documentType: "CI",
  documentNumber: ("PDOC-" + (now|floor|tostring)),
  givenNames: "Profesional",
  paternalSurname: "Demo",
  professionalType: "PHYSICIAN",
  professionalLicenseNumber: $lic,
  specialties: [ { code: "MEDICINA_GENERAL", primary: true, graduationYear: 2015 } ],
  organizationId: $org,
  branchId: $branch,
  startDate: "2024-01-01"
}')
req POST /api/v1/professionals "$PROF_BODY"
expect 5 "Register professional" 201 "$STATUS"
PROFESSIONAL_ID=$(jqf '.id')
echo "  professionalId: ${PROFESSIONAL_ID}"

# ---------------------------------------------------------------------------
# Step 6 — Schedule the consultation appointment (ms-agenda; no auth enforced by
# this service today — organizationPublicId travels in the body)
# ---------------------------------------------------------------------------
step 6 "Schedule consultation appointment"
START_AT="$(date -u -v+1d +%Y-%m-%dT09:00:00Z 2>/dev/null || date -u -d '+1 day' +%Y-%m-%dT09:00:00Z)"
END_AT="$(date -u -v+1d -v+30M +%Y-%m-%dT09:30:00Z 2>/dev/null || date -u -d '+1 day +30 min' +%Y-%m-%dT09:30:00Z)"
APPT_BODY=$(jq -n \
  --arg org "$ADMIN_ORG_ID" --arg branch "$BRANCH_ID" --arg patient "$PATIENT_ID" \
  --arg prof "$PROFESSIONAL_ID" --arg offering "$CONSULT_OFFERING_ID" \
  --arg starts "$START_AT" --arg ends "$END_AT" '{
  organizationPublicId: $org, branchPublicId: $branch, patientPublicId: $patient,
  healthProfessionalPublicId: $prof, serviceOfferingPublicId: $offering,
  startsAt: $starts, endsAt: $ends, modality: "IN_PERSON", channelOrigin: "RECEPTION",
  reasonForVisit: "Consulta demo E2E", requiresConsent: false, estimatedPriceBob: 150.00
}')
req POST /api/v1/scheduling/appointments "$APPT_BODY"
expect 6 "Create consultation appointment" 201 "$STATUS"
APPOINTMENT_ID=$(jqf '.publicId')
echo "  appointmentId: ${APPOINTMENT_ID}"

# ---------------------------------------------------------------------------
# Step 7 — Negative test: booking the SAME professional for an overlapping slot
# must 409 (Postgres EXCLUDE constraint no_professional_overlap, translated by
# OverlapTranslator)
# ---------------------------------------------------------------------------
step 7 "[Negative] Overlapping booking for the same professional -> 409"
req POST /api/v1/scheduling/appointments "$APPT_BODY"
expect 7 "Duplicate/overlapping booking rejected" 409 "$STATUS"

# ---------------------------------------------------------------------------
# Step 8 — Appointment lifecycle: confirm -> check-in -> start -> complete
# ---------------------------------------------------------------------------
step 8 "Appointment lifecycle transitions"
req POST "/api/v1/scheduling/appointments/${APPOINTMENT_ID}/confirm"
expect 8 "Confirm appointment" 200 "$STATUS"
req POST "/api/v1/scheduling/appointments/${APPOINTMENT_ID}/check-in"
expect 8 "Check-in appointment" 200 "$STATUS"
req POST "/api/v1/scheduling/appointments/${APPOINTMENT_ID}/start"
expect 8 "Start appointment" 200 "$STATUS"
req POST "/api/v1/scheduling/appointments/${APPOINTMENT_ID}/complete"
expect 8 "Complete appointment (ATTENDED)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# Step 9 — Login as PHYSICIAN and open a clinical encounter (ms-historial)
# ---------------------------------------------------------------------------
step 9 "Login as PHYSICIAN and open clinical encounter"
req POST /api/v1/identity/auth/login "$(jq -n --arg p "$DEMO_PASSWORD" '{email:"doctor@medicalsys.demo",password:$p}')"
expect 9 "Physician login" 200 "$STATUS"
DOCTOR_TOKEN=$(jqf '.accessToken')

ENCOUNTER_BODY=$(jq -n --arg patient "$PATIENT_ID" --arg appt "$APPOINTMENT_ID" \
  --arg prof "$PROFESSIONAL_ID" --arg branch "$BRANCH_ID" '{
  patientId: $patient, appointmentId: $appt, professionalId: $prof, branchId: $branch,
  type: "INITIAL_CONSULTATION"
}')
req POST /api/v1/encuentros "$ENCOUNTER_BODY" "$DOCTOR_TOKEN"
expect 9 "Open clinical encounter" 201 "$STATUS"
ENCOUNTER_ID=$(jqf '.id')
echo "  encounterId: ${ENCOUNTER_ID}"

# ---------------------------------------------------------------------------
# Step 10 — Request the X-ray study order
# ---------------------------------------------------------------------------
step 10 "Request X-RAY study order"
# J00 esta tanto en la semilla de ms-catalogos como en la lista local de ms-historial
# (CATALOGS_ICD10_CODES). Z00.0 solo existe en ms-catalogos, de modo que en el modo
# LOCAL por defecto ms-historial lo rechaza con 422 ICD10_CODE_NOT_ALLOWED.
ORDER_BODY='{"studyTypeCode":"X_RAY","priority":"ROUTINE","instructions":"Radiografia de torax (demo)","icd10Code":"J00"}'
req POST "/api/v1/encuentros/${ENCOUNTER_ID}/ordenes-estudio" "$ORDER_BODY" "$DOCTOR_TOKEN"
expect 10 "Create study order" 201 "$STATUS"
ORDER_ID=$(jqf '.id')
echo "  orderId: ${ORDER_ID}"

# ---------------------------------------------------------------------------
# Step 11 — Schedule the X-ray appointment and link it back to the study order
# ---------------------------------------------------------------------------
step 11 "Schedule X-RAY appointment and link to study order"
XRAY_START="$(date -u -v+1d -v+1H +%Y-%m-%dT10:00:00Z 2>/dev/null || date -u -d '+1 day +1 hour' +%Y-%m-%dT10:00:00Z)"
XRAY_END="$(date -u -v+1d -v+1H -v+20M +%Y-%m-%dT10:20:00Z 2>/dev/null || date -u -d '+1 day +1 hour +20 min' +%Y-%m-%dT10:20:00Z)"
XRAY_APPT_BODY=$(jq -n \
  --arg org "$ADMIN_ORG_ID" --arg branch "$BRANCH_ID" --arg patient "$PATIENT_ID" \
  --arg prof "$PROFESSIONAL_ID" --arg offering "$XRAY_OFFERING_ID" \
  --arg starts "$XRAY_START" --arg ends "$XRAY_END" '{
  organizationPublicId: $org, branchPublicId: $branch, patientPublicId: $patient,
  healthProfessionalPublicId: $prof, serviceOfferingPublicId: $offering,
  startsAt: $starts, endsAt: $ends, modality: "IN_PERSON", channelOrigin: "RECEPTION",
  reasonForVisit: "Toma de radiografia (demo)", requiresConsent: false, estimatedPriceBob: 250.00
}')
req POST /api/v1/scheduling/appointments "$XRAY_APPT_BODY"
expect 11 "Create X-RAY appointment" 201 "$STATUS"
XRAY_APPOINTMENT_ID=$(jqf '.publicId')

req PATCH "/api/v1/ordenes-estudio/${ORDER_ID}/cita-toma" "$(jq -n --arg id "$XRAY_APPOINTMENT_ID" '{collectionAppointmentId:$id}')" "$DOCTOR_TOKEN"
expect 11 "Link X-RAY appointment to study order" 200 "$STATUS"

# ---------------------------------------------------------------------------
# Step 12 — Upload the X-ray result document (multipart, ms-documental -> MinIO)
# ---------------------------------------------------------------------------
step 12 "Upload X-RAY result document"
# ms-documental (DocumentFilePolicy) solo admite image/jpeg, image/png, image/tiff,
# application/dicom o application/octet-stream para la clasificacion X_RAY: un .txt
# se rechaza con 422 FILE_TYPE_NOT_ALLOWED. Se genera un PNG 1x1 valido.
TMP_FILE=$(mktemp /tmp/medicalsys_demo_xray.XXXXXX.png)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n\x2d\xb4\x00\x00\x00\x00IEND\xaeB\x60\x82' > "$TMP_FILE"
METADATA=$(jq -n --arg patient "$PATIENT_ID" --arg order "$ORDER_ID" '{
  type: "X_RAY", patientId: $patient,
  link: { entityType: "STUDY_ORDER", entityId: $order, description: "Resultado de rayos X (demo)" }
}')
UPLOAD_STATUS=$(curl -sS -o /tmp/medicalsys_demo_upload.$$ -w '%{http_code}' \
  -X POST "${GATEWAY_BASE_URL}/api/v1/documents" \
  -H "Authorization: Bearer ${DOCTOR_TOKEN}" \
  -F "file=@${TMP_FILE};type=image/png" \
  -F "metadata=$(echo "$METADATA" | jq -c .);type=application/json")
UPLOAD_BODY=$(cat /tmp/medicalsys_demo_upload.$$ 2>/dev/null)
rm -f /tmp/medicalsys_demo_upload.$$ "$TMP_FILE"
STATUS="$UPLOAD_STATUS"; BODY="$UPLOAD_BODY"
expect 12 "Upload X-RAY document" 201 "$STATUS"
DOCUMENT_ID=$(jqf '.documentId')
echo "  documentId: ${DOCUMENT_ID}"

# ---------------------------------------------------------------------------
# Step 13 — Register the study result, linking the uploaded document
# ---------------------------------------------------------------------------
step 13 "Register study result"
RESULT_BODY=$(jq -n --arg doc "$DOCUMENT_ID" --arg prof "$PROFESSIONAL_ID" '{
  parameter: "RESULT",
  textValue: "Estudio sin hallazgos patologicos (demo, sin valor clinico real).",
  status: "FINAL",
  documentId: $doc,
  reportedById: $prof
}')
req POST "/api/v1/ordenes-estudio/${ORDER_ID}/resultados" "$RESULT_BODY" "$DOCTOR_TOKEN"
expect 13 "Register study result" 201 "$STATUS"

# ---------------------------------------------------------------------------
# Step 14 — Manual WhatsApp handoff (simulated: no ms-notificaciones exists in the
# MVP; the plan explicitly calls this a manual, out-of-system step)
# ---------------------------------------------------------------------------
step 14 "Manual WhatsApp handoff (simulated — no real send)"
echo "  [MANUAL_HANDOFF_SIMULATED] Send WhatsApp to ${PATIENT_PHONE}:"
echo "    \"Sus resultados de rayos X ya estan disponibles (orden ${ORDER_ID}, documento ${DOCUMENT_ID}).\""
echo "  No automated send exists in the MVP by design — this mirrors the plan's step 14."
PASS=$((PASS + 1))
STEP_LOG+=("PASS  14  Manual WhatsApp handoff simulated (no real send, by design)")

# ---------------------------------------------------------------------------
# Step 15 — Issue and fetch the demo invoice (ms-facturacion)
# ---------------------------------------------------------------------------
step 15 "Issue and fetch demo invoice"
INVOICE_BODY=$(jq -n --arg patient "$PATIENT_ID" --arg branch "$BRANCH_ID" \
  --arg consult "$CONSULT_OFFERING_ID" --arg xray "$XRAY_OFFERING_ID" \
  --arg apptC "$APPOINTMENT_ID" --arg apptX "$XRAY_APPOINTMENT_ID" '{
  patientId: $patient, branchId: $branch, paymentMethodCode: 1,
  items: [
    { serviceOfferingId: $consult, quantity: 1, appointmentId: $apptC },
    { serviceOfferingId: $xray, quantity: 1, appointmentId: $apptX }
  ]
}')
req POST /api/v1/facturas "$INVOICE_BODY" "$RECEP_TOKEN"
expect 15 "Issue demo invoice" 201 "$STATUS"
INVOICE_ID=$(jqf '.id')

req GET "/api/v1/facturas/${INVOICE_ID}" "" "$RECEP_TOKEN"
expect 15 "Fetch demo invoice" 200 "$STATUS"
echo "  invoice status : $(jqf '.status')"
echo "  invoice total  : $(jqf '.totalAmount') BOB"
echo "  invoice caption: $(jqf '.caption')"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================================================"
echo "  MedicalSys Demo E2E — summary"
echo "================================================================"
for line in "${STEP_LOG[@]}"; do echo "  $line"; done
echo "----------------------------------------------------------------"
echo "  PASS: ${PASS}   FAIL: ${FAIL}"
echo "================================================================"

[[ "$FAIL" -eq 0 ]]
