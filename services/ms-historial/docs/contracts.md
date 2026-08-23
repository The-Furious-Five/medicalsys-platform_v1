# Contrato de ms-historial

Congelado en la reunión de contratos. Un cambio incompatible crea `/api/v2`; un campo nuevo opcional
no rompe a nadie y no exige versión nueva.

## Convenciones

| Elemento | Acuerdo | Ejemplo |
| --- | --- | --- |
| Rutas | Español, como el resto de la plataforma | `/api/v1/encuentros` |
| Campos JSON | Inglés camelCase | `patientId`, `studyTypeCode` |
| Identificadores | UUID como texto de 36 caracteres | `7d1f8c2e-...` |
| Fechas y horas | ISO-8601 con zona; se almacena UTC | `2026-08-24T14:00:00Z` |
| Fechas de negocio | `yyyy-MM-dd` | `2026-09-01` |
| Números decimales | `DECIMAL` / `BigDecimal`, nunca `float` | `36.7` |
| Autenticación | El gateway propaga `X-Organization-Id`, `X-User-Id`, `X-Roles` | — |
| Trazabilidad | `X-Correlation-Id` de entrada y de salida | — |
| Campos nulos | No se serializan | — |

## Códigos HTTP

| Código | Cuándo |
| --- | --- |
| 200 | Consulta o actualización correcta |
| 201 | Recurso creado |
| 204 | Operación sin cuerpo (desactivar antecedente) |
| 400 | Request inválido o mal formado |
| 401 | Identidad ausente o no propagada por el gateway |
| 403 | Identidad válida sin el rol necesario |
| 404 | Recurso inexistente o de otro tenant |
| 409 | Conflicto de unicidad o de estado |
| 422 | Regla de negocio no procesable |
| 503 | ms-criptografía o ms-catálogos no disponible |

## Formato de error

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 422,
  "code": "ICD10_CODE_NOT_ALLOWED",
  "message": "El codigo CIE-10 'ZZZ999' no existe en el catalogo vigente",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/encuentros/9f2.../diagnosticos",
  "details": [{ "field": "icd10Code", "message": "no debe estar vacio" }]
}
```

`details` solo aparece en errores de validación.

### Códigos estables

`VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `METHOD_NOT_ALLOWED`,
`UNSUPPORTED_MEDIA_TYPE`, `MEDICAL_RECORD_NOT_FOUND`, `CLINICAL_ENCOUNTER_NOT_FOUND`,
`ALLERGY_NOT_FOUND`, `MEDICAL_BACKGROUND_NOT_FOUND`, `PRESCRIPTION_NOT_FOUND`,
`STUDY_ORDER_NOT_FOUND`, `RESOURCE_NOT_FOUND`, `MEDICAL_RECORD_ALREADY_EXISTS`,
`ENCOUNTER_ALREADY_EXISTS_FOR_APPOINTMENT`, `ALLERGY_ALREADY_REGISTERED`,
`DIAGNOSIS_ALREADY_REGISTERED`, `STUDY_RESULT_ALREADY_REGISTERED`, `NUMBERING_CONFLICT`,
`DATA_INTEGRITY_CONFLICT`, `ENCOUNTER_NOT_EDITABLE`, `ENCOUNTER_NOT_SIGNABLE`,
`ENCOUNTER_NOT_SIGNED`, `MEDICAL_RECORD_NOT_ACTIVE`, `STUDY_ORDER_TRANSITION_NOT_ALLOWED`,
`PRESCRIPTION_TRANSITION_NOT_ALLOWED`, `ICD10_CODE_NOT_ALLOWED`, `STUDY_TYPE_NOT_ALLOWED`,
`MEDICATION_CODE_NOT_ALLOWED`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`.

---

## Recorrido del guion de demostración

### 1. Crear u obtener el expediente

```http
POST /api/v1/expedientes
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-User-Id: 00000000-0000-4000-8000-0000000000f1
X-Roles: RECEPTIONIST
Content-Type: application/json

{ "patientId": "00000000-0000-4000-8000-0000000000b1",
  "branchId": "00000000-0000-4000-8000-0000000000a2" }
```

`201` la primera vez, `200` con el mismo `id` en cada llamada posterior.

```json
{
  "id": "3f1a...", "patientId": "0000...b1", "recordNumber": "EXP-2026-000001",
  "openedOn": "2026-08-24", "status": "ACTIVE", "sharesWithNetwork": false,
  "createdAt": "2026-08-24T18:00:00Z", "updatedAt": "2026-08-24T18:00:00Z"
}
```

### 2. Abrir el encuentro ligado a la cita

```http
POST /api/v1/encuentros
X-Roles: PHYSICIAN

{ "patientId": "0000...b1",
  "appointmentId": "0000...a11",
  "professionalId": "0000...e1",
  "branchId": "0000...a2",
  "type": "INITIAL_CONSULTATION" }
```

`201`. Si la cita ya tiene encuentro: `409 ENCOUNTER_ALREADY_EXISTS_FOR_APPOINTMENT`. El expediente
se recupera o se crea solo, de modo que atender no dependa de una llamada previa.

### 3. Registrar la narrativa (se cifra antes de persistir)

```http
PUT /api/v1/encuentros/{encounterId}/notas
X-Roles: PHYSICIAN

{ "chiefComplaint": "Cefalea de tres dias",
  "subjective": "Paciente ficticio refiere dolor frontal",
  "objective": "Sin signos de alarma",
  "assessment": "Cefalea tensional",
  "plan": "Analgesia y control en una semana",
  "suggestedNextAppointmentAt": "2026-09-01" }
```

En la respuesta, `encrypted: true` indica que la base conserva *ciphertext*. El objeto `narrative`
solo se incluye para roles clínicos.

### 4. Diagnóstico CIE-10

```http
POST /api/v1/encuentros/{encounterId}/diagnosticos
X-Roles: PHYSICIAN

{ "icd10Code": "J00", "type": "FINAL", "primary": true, "chronic": false,
  "observation": "Cuadro leve" }
```

`201`. Un código inexistente devuelve `422 ICD10_CODE_NOT_ALLOWED` y **no** se persiste nada. El
mismo código dos veces en el mismo encuentro devuelve `409 DIAGNOSIS_ALREADY_REGISTERED`.

### 5. Orden de rayos X

```http
POST /api/v1/encuentros/{encounterId}/ordenes-estudio
X-Roles: PHYSICIAN

{ "studyTypeCode": "X_RAY", "priority": "ROUTINE",
  "instructions": "Placa de torax", "icd10Code": "J00" }
```

`201`. El `id` devuelto es el **orderId** que consume ms-agenda.

```json
{ "id": "b41c...", "encounterId": "9f2...", "medicalRecordId": "3f1a...",
  "patientId": "0000...b1", "studyTypeCode": "X_RAY", "priority": "ROUTINE",
  "status": "REQUESTED", "requestedById": "0000...e1",
  "requestedAt": "2026-08-24T18:10:00Z", "results": [] }
```

`REQUESTED` es el estado "solicitada" del guion.

### 6. Firmar el encuentro

```http
POST /api/v1/encuentros/{encounterId}/firma
X-Roles: PHYSICIAN
```

Desde este momento el encuentro es inmutable. `PUT .../notas` devuelve
`422 ENCOUNTER_NOT_EDITABLE`, y un `UPDATE` directo en la base es rechazado por el trigger.

### 7. Agendar la toma (lo llama recepción tras crear la cita en ms-agenda)

```http
PATCH /api/v1/ordenes-estudio/{orderId}/cita-toma

{ "collectionAppointmentId": "0000...a12" }
```

La orden pasa a `SCHEDULED`.

### 8. Registrar el resultado con la referencia documental

```http
POST /api/v1/ordenes-estudio/{orderId}/resultados
X-Roles: RECEPTIONIST

{ "textValue": "Sin hallazgos patologicos",
  "documentId": "0000...d99",
  "reportedById": "0000...e1" }
```

`201`. La orden pasa a `REPORTED` (el estado "disponible" del guion). ms-historial guarda **solo** el
`documentId`: el archivo vive en MinIO bajo ms-documental.

### 9. Corrección posterior (no sobrescribe)

```http
POST /api/v1/encuentros/{encounterId}/addendums
X-Roles: PHYSICIAN

{ "professionalId": "0000...e1", "reason": "CORRECTION",
  "text": "Se aclara la dosis indicada" }
```

---

## Rutas internas

Las consumen otros microservicios dentro de `medicalsys-interna`. Devuelven proyecciones mínimas:
nunca narrativa ni referencias de cifrado.

| Ruta | Consumidor | Devuelve |
| --- | --- | --- |
| `GET /internal/v1/expedientes/{recordId}` | ms-documental | `id`, `patientId`, `recordNumber`, `status` |
| `GET /internal/v1/expedientes?patientId=` | ms-documental | Ídem |
| `GET /internal/v1/encuentros/{encounterId}` | ms-agenda | Encuentro sin narrativa |
| `GET /internal/v1/encuentros?appointmentId=` | ms-agenda | Encuentro de una cita |
| `GET /internal/v1/ordenes-estudio/{orderId}` | ms-agenda, ms-documental | Orden mínima con su `patientId` |

---

## Contratos que ms-historial **consume**

Declarados aquí para que quien los implemente sepa exactamente qué se espera. Mientras no existan,
el servicio funciona en modo local (ver README, sección 4).

### ms-criptografía (`CRYPTO_BASE_URL`, modo `REMOTE`)

```http
POST /internal/v1/cifrado
{ "organizationId": "uuid", "plaintext": "texto clinico" }
200 -> { "keyPublicId": "uuid", "ciphertext": "base64" }

POST /internal/v1/descifrado
{ "keyPublicId": "uuid", "ciphertext": "base64" }
200 -> { "plaintext": "texto clinico" }
```

Todos los campos cifrados de un mismo encuentro deben compartir `keyPublicId`, tal como exige el
diccionario.

### ms-catálogos (`CATALOGS_BASE_URL`, modo `REMOTE`)

```http
GET /internal/v1/cie10/{code}           200 si existe, 404 si no
GET /internal/v1/tipos-estudio/{code}   200 si existe, 404 si no
GET /internal/v1/medicamentos/{code}    200 si existe, 404 si no
```

Un `404` es respuesta de contrato y se traduce a `422`. Una caída se traduce a `503` y nunca se
confunde con un código inválido.

### Timeouts

Toda llamada saliente lleva timeout de conexión (2 s) y de lectura (3 s), y propaga
`X-Correlation-Id`. No se reintentan `POST` no idempotentes.
