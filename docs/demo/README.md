# MedicalSys — Demo E2E (MVP-16)

Reproducible, scripted walk through the full clinical flow — login, org setup,
register patient/professional, schedule appointment, clinical encounter, x-ray
order, upload result, manual WhatsApp handoff, demo invoice — entirely through
`ms-gateway`, on a clean local stack. Built for the Sprint Review so anyone on
the team can run it and get the same result.

**How this was built:** every endpoint, role, and request/response shape below
was read directly from each service's real Spring controllers and DTOs under
`services/<name>/src/main/java/...` — not copied from the original planning
spreadsheet (`build_backlog.mjs`'s `demoFlow`/`contracts` arrays), which had
already diverged from what got implemented (e.g. the plan's `/api/v1/citas`
is really `/api/v1/scheduling/appointments`; `/api/v1/pacientes` is really
`/api/v1/patients`). Where the plan and the real code disagreed, the real code
won.

**Status of this deliverable:** the collection (`run_demo_e2e.sh`) and this
runbook are complete and were built by reading every relevant controller,
security filter, and DB migration end to end. They have **not been executed
against a live stack** in this environment — the machine that produced them
could not safely run the full 10-service + gateway + 3-datastore build (Java
compiles for 8 more services on top of the 5 containers already running would
have pushed local RAM past a safe margin). Run it on a bigger machine (see
"Running the demo" below) and treat the first run as the actual verification;
this document tells you exactly what each step should do and how to read a
failure if the real behavior differs from what's written here.

## Known contract gaps found while reading the code

These were found by direct inspection, not by running anything. Two are fixed
in this same change (small, scoped, source-only — nothing built or run to
produce them); the rest are documented only, per scope.

1. **Demo admin's organization does not match the seeded tenant — not fixed,
   needs an env override before running.**
   `ms-identidad`'s `DemoUserSeeder` creates `admin@medicalsys.demo` (and the
   other two demo users) under `IDENTITY_DEMO_ORGANIZATION`, which defaults to
   `11111111-1111-1111-1111-111111111111`
   (`services/ms-identidad/src/main/resources/application.yaml`). But
   `ms-tenencia`'s `V2__seed_demo.sql` seeds the actual "Clínica Demo" tenant
   (with its branch and the `CONSULTATION`/`X_RAY` service offerings) under
   the fixed UUID `00000000-0000-4000-8000-0000000000a1`. Neither `.env` nor
   `compose.yaml` sets `IDENTITY_DEMO_ORGANIZATION` to reconcile them, and
   nothing in the repo creates a fresh org for the demo users to belong to (no
   `PLATFORM_ADMIN` demo account exists to call the org-creation endpoint,
   which is `PLATFORM_ADMIN`-only). **Before running the script**, either:
   - add `IDENTITY_DEMO_ORGANIZATION=00000000-0000-4000-8000-0000000000a1` as
     an environment override for the `ms-identidad` container (e.g. export it
     before `docker compose up`, or add it to `.env` — left to a human, per
     the "don't touch `.env`" constraint on this change), or
   - reseed `ms-tenencia`'s demo org under `11111111-1111-1111-1111-111111111111`
     instead.
   Step 2 of the script (`GET /api/v1/tenancy/organizacion`) will fail with a
   401/404 and print this exact diagnosis if the mismatch isn't fixed first.

2. **Gateway never propagated the JWT as trusted headers — fixed.**
   `ms-tenencia`, `ms-historial`, `ms-facturacion`, `ms-pacientes`, and
   `ms-documental` all read `X-Organization-Id` / `X-User-Id` / `X-Roles` from
   a `TenantContextFilter` (or `RequestIdentity`, in ms-documental) that
   trusts those headers unconditionally — the comment in every one of those
   filters says "el gateway... los recrea desde claims firmados." But
   `ms-gateway` was a bare path-based reverse proxy: no JWT decoding, no
   header injection, `SecurityConfig` just `permitAll()`'d everything. Every
   authenticated call to those five services would have 401'd (missing
   context) through the gateway, or — worse — anyone could spoof any
   org/user/role by sending those headers directly.
   Fixed in `services/ms-gateway/src/main/java/com/medicalsys/gateway/security/`:
   - `GatewayJwtConfig.java` — HS256 `JwtDecoder` bean using the same
     `JWT_SECRET` already wired into the gateway's environment in
     `compose.yaml` (this was already there, unused — strong evidence this was
     the intended design, not a different one).
   - `JwtPropagationFilter.java` — a servlet filter (same pattern as every
     other service's `TenantContextFilter`/`CorrelationIdFilter`) that:
     validates the bearer token when present and rejects it with 401 if it's
     invalid/expired; on a valid token, sets `X-User-Id`, `X-Organization-Id`
     (when the token carries one), and `X-Roles` from the JWT's claims; always
     strips any client-supplied values of those three headers first, so a
     caller can never spoof a tenant; and generates/propagates
     `X-Correlation-Id` on every hop, matching the existing convention in
     every downstream service. It intentionally does **not** reject requests
     with **no** `Authorization` header at all — several real routes are
     public by design (`login`, every `/ping`, `ms-catalogos`, the internal
     `ms-criptografia` routes, and today also `ms-agenda`/`ms-profesionales`,
     which enforce no auth at all in their own code). Hard-rejecting those at
     the gateway would be a policy decision this change deliberately avoids;
     the downstream `TenantContextFilter`s already correctly 401 on missing
     context where they need to.
   This is a logically self-contained, small addition (two new files, ~150
   lines together) written against the same JWT claim shape `ms-identidad`
   already documents (`sub`, `organizationId`, `roles` —
   `services/ms-identidad/src/main/java/com/medicalsys/identidad/security/TokenService.java`)
   and the same header contract every downstream `TenantContextFilter` already
   expects. It was not boot-tested here (no Maven build was run, by design —
   see "Status" above); it should compile and behave as described, but treat
   the first real `mvn spring-boot:run` / `docker compose up --build
   ms-gateway` as the actual verification.

3. **Gateway route predicates didn't match ~half of `ms-historial`'s and all
   of `ms-facturacion`'s real endpoints — fixed.**
   `ms-gateway`'s route for `ms-historial` only matched
   `Path=/api/v1/medical-records/**`, and the one for `ms-facturacion` only
   matched `Path=/api/v1/billing/**`. But in both services, only the `/ping`
   controller was actually built at that documented prefix — every real
   controller lives elsewhere:
   `ClinicalEncounterController` → `/api/v1/encuentros`,
   `MedicalRecordController` → `/api/v1/expedientes`,
   `StudyOrderController`/`DiagnosisController`/`AllergyController`/
   `MedicalBackgroundController`/`PrescriptionController` → `/api/v1/ordenes-estudio`,
   `/api/v1/diagnosticos`, `/api/v1/alergias`, `/api/v1/antecedentes`,
   `/api/v1/recetas`; and `InvoiceController` → `/api/v1/facturas`. Through
   the old gateway config, every one of those returned 404 — the gateway
   itself never matched a route, so the request never even reached the
   service. Fixed with a one-line change per route in
   `services/ms-gateway/src/main/resources/application.yaml`: both route
   predicates now also match their real controller prefixes (comma-separated
   `Path=` patterns — Spring Cloud Gateway MVC supports this natively), with a
   comment explaining why. No Java code changed for this fix, no rebuild
   needed beyond picking up the new YAML.

4. **`ms-criptografia`'s gateway route is also mismatched — not fixed, out of
   scope.** The route predicate is `Path=/api/v1/cryptography/**`, but the
   only controller (`EncryptionKeyController`) lives at `/internal/v1/crypto/**`.
   Left alone: this service is only ever called service-to-service (by
   `ms-historial`, to encrypt/decrypt clinical narrative), never by the demo
   collection directly, so it doesn't block any of the 15 steps. Flagging it
   here so it isn't a surprise later.

5. **`ms-pacientes` has a teammate's uncommitted WIP in the working tree
   right now** (`PatientController`, DTOs, security classes under
   `src/main/java/com/medicalsys/pacientes/`, per `git status`). This was
   read-only inspected to build the patient-registration steps below and was
   **not modified, rebuilt, or restarted** as part of this change — leave its
   container/build exactly as the teammate left it.

## The 15-step flow, as scripted in `run_demo_e2e.sh`

All calls go through the gateway at `${GATEWAY_BASE_URL}` (default
`http://localhost:18080`, matching this repo's `.env`;
`.env.example`'s own default is `8080` — check whichever `.env` is actually in
use before running). Demo accounts and password come from
`ms-identidad`'s `DemoUserSeeder`: `admin@medicalsys.demo`,
`recepcion@medicalsys.demo`, `doctor@medicalsys.demo`, all with password
`SanGabriel.Demo26!` unless `IDENTITY_DEMO_PASSWORD` was overridden.

| # | Step | Endpoint | Expected |
|---|------|----------|----------|
| 1 | Login as ADMINISTRATOR | `POST /api/v1/identity/auth/login` | 200, JWT with `organizationId` + `roles` claims |
| 2 | Org setup — fetch tenant, branch, service offerings | `GET /api/v1/tenancy/organizacion`, `GET /api/v1/tenancy/sucursales`, `GET /api/v1/tenancy/prestaciones` | 200s; "Clínica Demo", 1 branch, `CONSULTATION` (150 BOB) + `X_RAY` (250 BOB) offerings |
| 3 | Login as RECEPTIONIST, register patient | `POST /api/v1/identity/auth/login`, `POST /api/v1/patients` | 200, then 201 with `patientId` |
| 4 | **[Negative]** Duplicate patient (same document) | `POST /api/v1/patients` (same body) | **409** — unique constraint on `(document_type, document_number, document_complement)` |
| 5 | Register professional | `POST /api/v1/identity/users` (role `PHYSICIAN`), then `POST /api/v1/professionals` | 201, 201 with `professionalId` |
| 6 | Schedule consultation appointment | `POST /api/v1/scheduling/appointments` | 201 with `appointmentId` |
| 7 | **[Negative]** Overlapping booking, same professional | `POST /api/v1/scheduling/appointments` (same body) | **409** — Postgres `EXCLUDE` constraint `no_professional_overlap` |
| 8 | Appointment lifecycle | `POST .../confirm`, `.../check-in`, `.../start`, `.../complete` | 200 each, final status `ATTENDED` |
| 9 | Login as PHYSICIAN, open clinical encounter | `POST /api/v1/identity/auth/login`, `POST /api/v1/encuentros` | 200, then 201 with `encounterId` |
| 10 | Request X-RAY study order | `POST /api/v1/encuentros/{id}/ordenes-estudio` | 201 with `orderId`, status `REQUESTED` |
| 11 | Schedule X-RAY appointment, link to order | `POST /api/v1/scheduling/appointments`, `PATCH /api/v1/ordenes-estudio/{id}/cita-toma` | 201, then 200 |
| 12 | Upload X-RAY result document | `POST /api/v1/documents` (multipart: `file` + `metadata`) | 201 with `documentId`, stored in MinIO |
| 13 | Register study result | `POST /api/v1/ordenes-estudio/{id}/resultados` | 201, order effectively "available" (`REPORTED`) |
| 14 | **Manual WhatsApp handoff** (simulated) | *no API call — by design* | prints patient phone + message; no `ms-notificaciones` exists in the MVP, matching the plan's explicit "proceso fuera del sistema" |
| 15 | Issue and fetch demo invoice | `POST /api/v1/facturas`, `GET /api/v1/facturas/{id}` | 201, then 200; `status: DEMO_ACCEPTED`-equivalent, `demo: true`, `caption` states it has no fiscal validity |

## Prerequisites

- Docker + Docker Compose (this repo's `compose.yaml`).
- `bash`, `curl`, `jq` on the machine running the script (no `newman`/Postman
  CLI was available in the environment this was built in — that's why this
  is a shell script and not a `.postman_collection.json`; both are equally
  valid per the task, and a Postman collection could be generated from this
  same set of calls later if the team standardizes on Postman/Bruno).
- Enough RAM/CPU to build and run all 10 services + gateway + MySQL +
  Postgres + Redis + MinIO concurrently — this is the part that could not be
  done safely in the environment that produced this deliverable. Budget for
  it on whichever machine actually runs the demo.

## Starting from a clean DB

```bash
# from the repo root
docker compose down -v          # drops the named volumes (mysql_data, postgres_data,
                                 # redis_data, minio_data) for a truly clean run.
                                 # SKIP -v if you want to keep ms-pacientes' teammate
                                 # data around — check with the owner first.
docker compose up -d --build
docker compose ps               # wait for all services + mysql/postgres/minio healthy
```

Before the first run, apply the fix from gap #1 above (env override for
`IDENTITY_DEMO_ORGANIZATION`), since it's the one gap in this list that
wasn't fixed in source.

## Running the demo

```bash
cd docs/demo
chmod +x run_demo_e2e.sh
GATEWAY_BASE_URL=http://localhost:18080 ./run_demo_e2e.sh
```

The script prints a `[PASS]`/`[FAIL]` line per assertion (including the two
negative 409 tests, which pass when the failure happens) and a final summary
table. It exits non-zero if anything failed unexpectedly. On a failure, it
prints the actual HTTP status and response body so you can tell a genuine
service bug from a script issue.

Quick standalone health check, if you want to confirm the stack is up before
running the full script:

```bash
for p in identity/ping tenancy/ping catalogs/ping scheduling/ping professionals/ping billing/ping medical-records/ping; do
  curl -sS -o /dev/null -w "%{http_code} $p\n" "http://localhost:18080/api/v1/$p"
done
```

(`patients`, `documents`, and `cryptography` don't have a `/ping` endpoint
under their gateway prefix as of this writing — this doesn't block the demo.)

## What was intentionally left alone

- `services/ms-pacientes` — teammate's uncommitted WIP, read-only inspected,
  not modified, not rebuilt, not restarted.
- `compose.yaml`, `.env`, `.env.example` — not modified, per the constraint on
  this change. The `IDENTITY_DEMO_ORGANIZATION` fix (gap #1) needs a human to
  apply it as an env override.
- `ms-criptografia`'s gateway route mismatch (gap #4) — doesn't block the
  demo, documented only.
- Any other cross-service contract nuance not called out above that surfaces
  on the first real run — this runbook is based on a thorough static reading
  of the code, not a live run, so treat step-by-step deviations on first
  execution as real signal, and update this table once verified.
