# Contrato HTTP de ms-pacientes

Este documento describe la API v1 implementada por `ms-pacientes`. La autoridad de datos es
`patients_db` y está formada por `patient`, `notification_preference` y `patient_merge`.

## 1. Convenciones

- Rutas y JSON en inglés; propiedades JSON en `camelCase`.
- Los identificadores expuestos son UUID. El `BIGINT id` de MySQL nunca sale del servicio.
- Fechas: `yyyy-MM-dd`. Instantes: ISO-8601 con zona y almacenamiento UTC.
- Teléfonos: E.164, por ejemplo `+59170000001`.
- Los campos nulos no se incluyen en la respuesta.
- `encryptedAddressBase64` es el cifrado opaco producido por `ms-criptografia`; nunca texto plano.
- Las referencias a Identidad y Criptografía usan UUID externos sin claves foráneas entre bases.
- El gateway debe propagar `X-Organization-Id`, `X-User-Id`, `X-Roles` y `X-Correlation-Id`.

Ejemplo de headers públicos:

```http
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-User-Id: 00000000-0000-4000-8000-0000000000f1
X-Roles: RECEPTIONIST
X-Correlation-Id: 2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90
Content-Type: application/json
```

## 2. Cobertura del modelo

`PatientResponse` representa todos los datos funcionales de `patient`:

- identidad externa: `patientId`, `userAccountId`;
- documento: `documentType`, `documentNumber`, `documentComplement`, `issuedDepartment`;
- datos personales: nombres, fecha de nacimiento, sexo, estado civil, grupo sanguíneo y ocupación;
- contacto: teléfonos, correo, dirección cifrada, ciudad y departamento;
- emergencia y tutor: nombre, teléfono, relación y `guardianPatientId`;
- cifrado y consentimiento: `encryptionKeyId`, `dataConsentAt`;
- ciclo de vida: `status`, `deathDate`, `createdAt`, `updatedAt`.

Valores controlados:

- `documentType`: `CI`, `FOREIGN_ID_CARD`, `PASSPORT`, `BIRTH_CERTIFICATE`, `NO_DOCUMENT`.
- departamentos: `LA_PAZ`, `COCHABAMBA`, `SANTA_CRUZ`, `ORURO`, `POTOSI`, `CHUQUISACA`,
  `TARIJA`, `BENI`, `PANDO`, `FOREIGN`.
- `sex`: `M`, `F`, `INTERSEX`, `UNDECLARED`.
- `maritalStatus`: `SINGLE`, `MARRIED`, `DIVORCED`, `WIDOWED`, `DOMESTIC_PARTNERSHIP`,
  `UNDECLARED`.
- `bloodType`: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, `UNKNOWN`.
- `status`: `ACTIVE`, `INACTIVE`, `DECEASED`.

## 3. Pacientes

### 3.1 Ping

```http
GET /api/v1/patients/ping
```

No requiere headers de identidad. Respuesta `200`:

```json
{"service":"ms-pacientes","status":"ok"}
```

### 3.2 Registrar

```http
POST /api/v1/patients
```

Roles: `RECEPTIONIST`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

```json
{
  "userAccountId": "00000000-0000-4000-8000-0000000000c1",
  "documentType": "CI",
  "documentNumber": "123",
  "documentComplement": "",
  "issuedDepartment": "LA_PAZ",
  "givenNames": "Maria Elena",
  "paternalSurname": "Ficticia",
  "maternalSurname": "Prueba",
  "birthDate": "1995-04-10",
  "sex": "F",
  "maritalStatus": "SINGLE",
  "bloodType": "O+",
  "occupation": "Arquitecta",
  "phoneE164": "+59170000001",
  "alternatePhone": "+59170000002",
  "email": "paciente.demo@example.invalid",
  "encryptedAddressBase64": "AQIDBA==",
  "city": "Cochabamba",
  "department": "COCHABAMBA",
  "emergencyContactName": "Persona Ficticia",
  "emergencyContactPhone": "+59170000003",
  "emergencyContactRelationship": "Madre",
  "guardianPatientId": "00000000-0000-4000-8000-0000000000b2",
  "encryptionKeyId": "00000000-0000-4000-8000-0000000000d1",
  "dataConsentAt": "2026-08-24T18:00:00Z"
}
```

Obligatorios: `documentType`, `documentNumber`, `givenNames`, `paternalSurname` y `birthDate`.
Un registro nuevo siempre empieza en `ACTIVE`; `patientId`, `fullName`, estado y timestamps los
genera el servidor. Respuesta `201` con `Location: /api/v1/patients/{patientId}`.

El documento se normaliza con `trim` y mayúsculas. Un complemento ausente se guarda como `""`.
La unicidad es la terna:

```text
documentType + documentNumber + documentComplement
```

Por tanto, `CI 123` no puede registrarse dos veces, pero `CI 123` y `CI 123-A` son identidades
distintas. La comprobación existe en servicio y en la restricción UNIQUE de MySQL.

### 3.3 Consultar por UUID

```http
GET /api/v1/patients/{patientId}
```

Roles: `RECEPTIONIST`, `PHYSICIAN`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

Devuelve `200 PatientResponse` o `404 PATIENT_NOT_FOUND`.

### 3.4 Consultar exactamente por documento

```http
GET /api/v1/patients?documentType=CI&documentNumber=123&documentComplement=A
```

Aplica la misma normalización del registro y devuelve un único `PatientResponse`.

### 3.5 Buscar por nombre o número

```http
GET /api/v1/patients/search?q=Maria&page=0&size=20
```

La respuesta paginada tiene `content`, `page`, `size`, `totalElements` y `totalPages`.

### 3.6 Actualizar datos mutables

```http
PUT /api/v1/patients/{patientId}
```

Roles: `RECEPTIONIST`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

Es un reemplazo completo de los campos mutables. El documento y `patientId` son inmutables. Los
campos opcionales omitidos o nulos se limpian. Debe incluir nuevamente nombres, fecha de nacimiento
y `status`.

Reglas de estado:

- `DECEASED` exige `deathDate`;
- `ACTIVE` e `INACTIVE` exigen que `deathDate` sea nulo;
- `deathDate` no puede preceder al nacimiento ni estar en el futuro;
- un paciente no puede ser su propio tutor.

## 4. Preferencias de notificación

Los envíos pertenecen a otros servicios; aquí solo se conserva el consentimiento del paciente.

Valores:

- canales: `WHATSAPP`, `SMS`, `EMAIL`, `PUSH`;
- categorías: `CLINICAL`, `ADMINISTRATIVE`;
- fuentes: `WEB_FORM`, `RECEPTION`, `WHATSAPP_RESPONSE`, `PHYSICAL_CONTRACT`, `IMPORT`.

### Listar

```http
GET /api/v1/patients/{patientId}/notification-preferences
```

### Registrar, activar o revocar

```http
PUT /api/v1/patients/{patientId}/notification-preferences/{channel}/{category}
```

```json
{"optIn":true,"consentSource":"RECEPTION"}
```

`optIn` es obligatorio. Al activar se registra `optInAt`; al revocar se registra `optOutAt`. No hay
DELETE público porque borrar la fila destruiría la trazabilidad del consentimiento.

## 5. Fusión de pacientes duplicados

### Fusionar

```http
POST /api/v1/patients/{survivingPatientId}/merges
```

Roles: `ADMINISTRATOR`, `PLATFORM_ADMIN`.

```json
{
  "mergedPatientId": "00000000-0000-4000-8000-0000000000b2",
  "reason": "Registro duplicado de demostracion",
  "eventId": "00000000-0000-4000-8000-0000000000e1"
}
```

La identidad fusionada pasa a `INACTIVE`. `eventId` hace idempotente el reintento: repetir el mismo
evento para los mismos pacientes devuelve la evidencia existente; reutilizarlo en otra fusión
devuelve `409 MERGE_EVENT_ALREADY_USED`.

### Historial de fusiones recibidas por el superviviente

```http
GET /api/v1/patients/{survivingPatientId}/merges
```

## 6. Contratos internos

Estas rutas no deben publicarse en el gateway.

```http
GET /internal/v1/patients/{patientId}/scheduling-summary
GET /internal/v1/patients/{patientId}/billing-summary
```

Agenda recibe únicamente `patientId`, `fullName` y `status`. Facturación recibe esos campos y la
terna del documento. Ninguna recibe contacto, dirección, fecha de nacimiento ni datos de emergencia.

## 7. Errores

Todos los errores siguen este formato:

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 409,
  "code": "PATIENT_DOCUMENT_ALREADY_EXISTS",
  "message": "Ya existe un paciente con el mismo tipo, numero y complemento de documento",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/patients"
}
```

Códigos principales: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`,
`PATIENT_NOT_FOUND`, `PATIENT_DOCUMENT_ALREADY_EXISTS`, `PATIENT_USER_ACCOUNT_ALREADY_LINKED`,
`PATIENT_ALREADY_MERGED`, `MERGE_EVENT_ALREADY_USED`, `INVALID_PATIENT_MERGE`,
`DATA_INTEGRITY_CONFLICT`, `INTERNAL_ERROR`.

## 8. Comprobación local

### Datos de demostración

Cuando se levanta mediante `compose.yaml`, Flyway carga también
`db/demo/R__seed_demo_patients.sql`. El seed contiene seis identidades completamente ficticias:

| Caso | patientId | Documento | Estado |
| --- | --- | --- | --- |
| Paciente principal | `10000000-0000-4000-8000-000000000001` | `CI 123` | `ACTIVE` |
| Mismo número, otro complemento | `10000000-0000-4000-8000-000000000002` | `CI 123-A` | `ACTIVE` |
| Menor con tutor | `10000000-0000-4000-8000-000000000003` | `BIRTH_CERTIFICATE DEMO-BIRTH-003` | `ACTIVE` |
| Pasaporte extranjero | `10000000-0000-4000-8000-000000000004` | `PASSPORT DEMO-PASS-004` | `ACTIVE` |
| Identidad fusionada | `10000000-0000-4000-8000-000000000005` | `CI DEMO-MERGED-005` | `INACTIVE` |
| Paciente histórico | `10000000-0000-4000-8000-000000000006` | `CI DEMO-DECEASED-006` | `DECEASED` |

También incluye tres preferencias de notificación y una fusión de ejemplo. Todos los correos usan
el dominio reservado `.invalid` y ningún dato corresponde a una persona real.

Para arrancar sin datos demo, defina:

```dotenv
PACIENTES_FLYWAY_LOCATIONS=classpath:db/migration
```

### Pruebas automatizadas

```bash
./mvnw clean test
```

El conjunto de pruebas cubre validación HTTP, autorización, proyecciones internas, mapeo de todos
los campos, preferencias, fusiones y, expresamente, los casos `CI 123` repetido y `CI 123-A`.
