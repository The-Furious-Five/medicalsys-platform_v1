# Contrato de ms-pacientes

Este documento define el contrato HTTP del microservicio `ms-pacientes` para el MVP del Sprint 2.
Es el acuerdo que deben respetar el gateway, Agenda, Historial y Facturacion. Primero se cambia este
contrato y despues el codigo; un cambio incompatible debe publicarse en `/api/v2`.

> **Datos de demostracion.** Durante este sprint solo se utilizan identidades, documentos, telefonos
> y correos claramente ficticios. Nunca se usan datos personales reales en requests, seeds, pruebas,
> capturas ni logs.

## 1. Responsabilidad y alcance

`ms-pacientes` es la autoridad de escritura del registro administrativo del paciente (MPI reducido).
En este sprint permite:

- registrar un paciente ficticio con documento unico y datos de contacto minimos;
- consultar un paciente por su UUID publico;
- buscar un paciente por tipo, numero y complemento de documento;
- entregar a Agenda y Facturacion proyecciones internas con los datos minimos que necesitan;
- proporcionar el telefono ficticio que se usara para preparar el aviso manual del resultado.

Queda fuera del contrato de este sprint:

- autorregistro y credenciales del paciente (pertenecen a `ms-identidad`);
- actualizacion completa del perfil y portal del paciente;
- historia clinica, diagnosticos, alergias y resultados (pertenecen a `ms-historial`);
- carga de archivos (pertenece a `ms-documental`);
- fusion de pacientes duplicados (`patient_merge` queda preparada para un incremento posterior);
- envio real por WhatsApp y cualquier credencial externa;
- endpoints para administrar `notification_preference`.

La tabla `notification_preference` existe porque el consentimiento de contacto pertenece al perfil
del paciente, no al mecanismo que envia mensajes. En el MVP no se modifica por API y el aviso es
completamente manual.

### Limite multi-tenant del MVP

`patient` representa una identidad global y por eso no contiene `organization_id`. Los headers de
organizacion se conservan para autorizacion y trazabilidad, pero la demostracion usa una sola
organizacion. Antes de habilitar multiples organizaciones se debe acordar un modelo explicito de
vinculo/acceso organizacion-paciente; no se debe fingir aislamiento agregando filtros que la base no
puede respaldar.

---

## 2. Convenciones

| Elemento | Acuerdo | Ejemplo |
| --- | --- | --- |
| Rutas | Ingles, igual que la ruta ya configurada en el gateway | `/api/v1/patients` |
| Campos JSON | Ingles y `camelCase` | `documentNumber`, `phoneE164` |
| Identificador expuesto | UUID publico como texto; nunca el `BIGINT id` interno | `00000000-0000-4000-8000-0000000000b1` |
| Fechas | ISO-8601 `yyyy-MM-dd` | `1995-04-10` |
| Fecha y hora | ISO-8601 con zona; internamente UTC | `2026-08-24T14:00:00Z` |
| Telefono | Formato E.164 | `+59170000001` |
| Campos nulos | No se serializan en las respuestas | - |
| Autenticacion publica | El gateway propaga identidad, organizacion y roles | - |
| Trazabilidad | Se recibe y devuelve `X-Correlation-Id` | UUID |

### Headers de endpoints publicos

```http
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-User-Id: 00000000-0000-4000-8000-0000000000f1
X-Roles: RECEPTIONIST
X-Correlation-Id: 2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90
Content-Type: application/json
```

`X-Organization-Id`, `X-User-Id` y `X-Roles` provienen del token validado por el gateway. Nunca se
aceptan sus equivalentes dentro del JSON enviado por el cliente.

### Roles del MVP

| Operacion | Roles permitidos |
| --- | --- |
| Registrar paciente | `RECEPTIONIST`, `ADMINISTRATOR`, `PLATFORM_ADMIN` |
| Consultar o buscar paciente | `RECEPTIONIST`, `PHYSICIAN`, `ADMINISTRATOR`, `PLATFORM_ADMIN` |
| Rutas `/internal/**` | Solo trafico interno entre microservicios en el entorno local |
| Ping | Sin autenticacion |

La red interna de Docker no reemplaza autenticacion entre servicios en produccion. En este MVP las
rutas internas no se publican en el gateway; la autenticacion servicio-a-servicio queda fuera del
sprint.

---

## 3. Codigos HTTP

| Codigo | Uso |
| --- | --- |
| `200 OK` | Consulta correcta |
| `201 Created` | Paciente registrado |
| `400 Bad Request` | JSON mal formado, campos invalidos o parametros incompletos |
| `401 Unauthorized` | Identidad ausente o no propagada por el gateway |
| `403 Forbidden` | Identidad valida sin un rol permitido |
| `404 Not Found` | Paciente inexistente |
| `409 Conflict` | Ya existe un paciente con el mismo documento |
| `422 Unprocessable Entity` | Regla de negocio no procesable en el MVP |
| `500 Internal Server Error` | Error interno no previsto; no expone detalles tecnicos |

## 4. Formato comun de error

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 409,
  "code": "PATIENT_DOCUMENT_ALREADY_EXISTS",
  "message": "Ya existe un paciente con el documento indicado",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/patients",
  "details": [
    {
      "field": "documentNumber",
      "message": "el documento ya se encuentra registrado"
    }
  ]
}
```

`details` aparece solamente cuando ayuda a identificar campos invalidos. Los consumidores deben
reaccionar al valor estable de `code`, no al texto de `message`.

### Codigos de error estables

`VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `METHOD_NOT_ALLOWED`,
`UNSUPPORTED_MEDIA_TYPE`, `PATIENT_NOT_FOUND`, `PATIENT_DOCUMENT_ALREADY_EXISTS`,
`DOCUMENT_TYPE_NOT_SUPPORTED_IN_MVP`, `PATIENT_NOT_ACTIVE`, `DATA_INTEGRITY_CONFLICT`,
`INTERNAL_ERROR`.

---

## 5. Ping tecnico

Comprueba que el gateway puede enrutar hacia el contenedor. No comprueba reglas de negocio.

```http
GET /api/v1/patients/ping
```

Respuesta `200 OK`:

```json
{
  "service": "ms-pacientes",
  "status": "ok"
}
```

---

## 6. Registrar paciente

```http
POST /api/v1/patients
```

Roles: `RECEPTIONIST`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

### Request del MVP

```json
{
  "documentType": "CI",
  "documentNumber": "DEMO-0001",
  "documentComplement": "",
  "issuedDepartment": "LA_PAZ",
  "givenNames": "Maria Elena",
  "paternalSurname": "Ficticia",
  "maternalSurname": "Prueba",
  "birthDate": "1995-04-10",
  "sex": "F",
  "phoneE164": "+59170000001",
  "email": "paciente.demo.0001@example.invalid",
  "city": "La Paz",
  "department": "LA_PAZ"
}
```

### Campos aceptados

| Campo | Obligatorio | Regla del MVP |
| --- | --- | --- |
| `documentType` | si | En este sprint se acepta `CI` |
| `documentNumber` | si | 1 a 20 caracteres; se normaliza quitando espacios externos y pasando a mayusculas |
| `documentComplement` | no | 0 a 5 caracteres; por defecto `""`; forma parte de la unicidad |
| `issuedDepartment` | no | Uno de los departamentos controlados |
| `givenNames` | si | 1 a 90 caracteres |
| `paternalSurname` | si | 1 a 60 caracteres |
| `maternalSurname` | si | 1 a 60 caracteres |
| `birthDate` | si | Desde `1900-01-01` y no puede estar en el futuro |
| `sex` | no | `M`, `F`, `INTERSEX` o `UNDECLARED`; por defecto `UNDECLARED` |
| `phoneE164` | si en el MVP | E.164, por ejemplo `+59170000001`; habilita el aviso manual |
| `email` | no | Correo valido, maximo 100 caracteres |
| `city` | no | Maximo 80 caracteres |
| `department` | no | Uno de los departamentos controlados |

Valores de departamento permitidos:

`LA_PAZ`, `COCHABAMBA`, `SANTA_CRUZ`, `ORURO`, `POTOSI`, `CHUQUISACA`, `TARIJA`, `BENI`,
`PANDO`, `FOREIGN`.

Aunque la base ya reserva otros tipos de documento para el modelo futuro, `FOREIGN_ID_CARD`,
`PASSPORT`, `BIRTH_CERTIFICATE` y `NO_DOCUMENT` no se habilitan en este incremento. Recibirlos
devuelve `422 DOCUMENT_TYPE_NOT_SUPPORTED_IN_MVP` hasta acordar sus reglas de identificacion.

### Campos que el cliente no puede enviar

- `id` o `patientId`: el servicio genera un UUID.
- `fullName`: lo calcula la base.
- `status`: todo registro nuevo comienza en `ACTIVE`.
- `createdAt` y `updatedAt`: los genera la base.
- `organizationId`, `userId` o roles: provienen del gateway.
- `password`: pertenece a `ms-identidad`.
- `diagnosis`, `allergies`, `medicalHistory`: pertenecen a `ms-historial`.
- `encryptedAddress` y claves de cifrado: requieren un contrato posterior con `ms-criptografia`.

### Normalizacion previa a la unicidad

Antes de consultar o guardar, el servicio normaliza:

- `documentType`, `documentNumber` y `documentComplement`: `trim` y mayusculas;
- `email`: `trim` y minusculas;
- nombres: elimina espacios externos, sin cambiar arbitrariamente su escritura.

La unicidad final la garantiza MySQL sobre:

```text
document_type + document_number + document_complement
```

La aplicacion puede consultar antes para ofrecer un error claro, pero tambien debe convertir una
violacion concurrente de la restriccion unica en `409 PATIENT_DOCUMENT_ALREADY_EXISTS`.

### Respuesta exitosa

`201 Created`

```http
Location: /api/v1/patients/00000000-0000-4000-8000-0000000000b1
```

```json
{
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "documentType": "CI",
  "documentNumber": "DEMO-0001",
  "documentComplement": "",
  "issuedDepartment": "LA_PAZ",
  "givenNames": "Maria Elena",
  "paternalSurname": "Ficticia",
  "maternalSurname": "Prueba",
  "fullName": "Ficticia Prueba Maria Elena",
  "birthDate": "1995-04-10",
  "sex": "F",
  "phoneE164": "+59170000001",
  "email": "paciente.demo.0001@example.invalid",
  "city": "La Paz",
  "department": "LA_PAZ",
  "status": "ACTIVE",
  "createdAt": "2026-08-24T18:00:00Z",
  "updatedAt": "2026-08-24T18:00:00Z"
}
```

### Errores importantes

| Situacion | Respuesta |
| --- | --- |
| Falta un campo obligatorio | `400 VALIDATION_ERROR` |
| Fecha de nacimiento futura | `400 VALIDATION_ERROR` |
| Telefono fuera de E.164 | `400 VALIDATION_ERROR` |
| Tipo de documento fuera del alcance | `422 DOCUMENT_TYPE_NOT_SUPPORTED_IN_MVP` |
| Documento repetido | `409 PATIENT_DOCUMENT_ALREADY_EXISTS` |

---

## 7. Consultar paciente por UUID publico

```http
GET /api/v1/patients/{patientId}
```

Roles: `RECEPTIONIST`, `PHYSICIAN`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

Ejemplo:

```http
GET /api/v1/patients/00000000-0000-4000-8000-0000000000b1
```

Respuesta `200 OK`: el mismo `PatientResponse` documentado en la seccion 6.

Un UUID bien formado que no existe devuelve:

```http
404 PATIENT_NOT_FOUND
```

Un valor que no es UUID devuelve:

```http
400 VALIDATION_ERROR
```

---

## 8. Buscar paciente por documento

La combinacion del documento es unica, por lo que esta consulta devuelve un paciente, no una lista.

```http
GET /api/v1/patients?documentType=CI&documentNumber=DEMO-0001&documentComplement=
```

Roles: `RECEPTIONIST`, `PHYSICIAN`, `ADMINISTRATOR`, `PLATFORM_ADMIN`.

Parametros:

| Parametro | Obligatorio | Regla |
| --- | --- | --- |
| `documentType` | si | `CI` en el MVP |
| `documentNumber` | si | Se aplica la misma normalizacion del registro |
| `documentComplement` | no | Por defecto `""` |

Respuesta `200 OK`: el mismo `PatientResponse` de la seccion 6.

Si no existe coincidencia:

```http
404 PATIENT_NOT_FOUND
```

No enviar `documentType` o `documentNumber` devuelve `400 VALIDATION_ERROR`.

> Esta ruta queda reservada en el MVP para busqueda exacta. Listado, busqueda parcial por nombre y
> paginacion se agregaran como contratos separados para no cambiar silenciosamente esta respuesta.

---

## 9. Resumen interno para Agenda

Agenda valida que el paciente exista y este activo antes de crear una cita. No necesita documento,
telefono, correo ni ninguna informacion clinica.

```http
GET /internal/v1/patients/{patientId}/scheduling-summary
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-Correlation-Id: 2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90
```

Respuesta `200 OK`:

```json
{
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "fullName": "Ficticia Prueba Maria Elena",
  "status": "ACTIVE"
}
```

Si el paciente no existe: `404 PATIENT_NOT_FOUND`.

Si existe, pero no esta activo: el endpoint responde `200` con su estado real. Agenda decide si
rechaza la cita y puede traducirlo a su propio error de negocio. De esta manera no se confunde
"no existe" con "existe, pero esta inactivo".

---

## 10. Resumen interno para Facturacion

Facturacion necesita identificar al cliente, pero no debe recibir telefono, correo, fecha de
nacimiento ni datos clinicos.

```http
GET /internal/v1/patients/{patientId}/billing-summary
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-Correlation-Id: 2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90
```

Respuesta `200 OK`:

```json
{
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "fullName": "Ficticia Prueba Maria Elena",
  "documentType": "CI",
  "documentNumber": "DEMO-0001",
  "documentComplement": "",
  "status": "ACTIVE"
}
```

Si el paciente no existe: `404 PATIENT_NOT_FOUND`.

Facturacion debe verificar `status == ACTIVE` antes de emitir y convertir cualquier otro estado en
su error `PATIENT_NOT_BILLABLE`.

---

## 11. Aviso manual del resultado

`ms-pacientes` solo proporciona `phoneE164` mediante la consulta publica autorizada. El estado del
resultado pertenece a `ms-historial`; ningun endpoint de pacientes debe copiarlo ni persistirlo.

Para la demostracion, recepcion combina visualmente:

```text
phoneE164 de ms-pacientes + estado del resultado de ms-historial + texto ficticio
```

Ejemplo de texto que se copia manualmente fuera del sistema:

```text
MedicalSys Demo: el resultado ficticio de su estudio ya esta disponible. Este mensaje es parte de
una demostracion local y no contiene informacion medica real.
```

No existe llamada a WhatsApp, token externo, webhook ni registro de entrega en este sprint.

---

## 12. Matriz minima de pruebas del contrato

| Caso | Resultado esperado |
| --- | --- |
| Registrar un CI ficticio valido | `201`, `Location` y UUID publico |
| Repetir el mismo CI/complemento | `409 PATIENT_DOCUMENT_ALREADY_EXISTS` |
| Mismo numero con complemento diferente | Se evalua como documento distinto |
| Omitir `givenNames` | `400 VALIDATION_ERROR` |
| Enviar fecha futura | `400 VALIDATION_ERROR` |
| Enviar telefono no E.164 | `400 VALIDATION_ERROR` |
| Consultar UUID existente | `200` |
| Consultar UUID inexistente | `404 PATIENT_NOT_FOUND` |
| Buscar documento existente | `200` |
| Buscar documento inexistente | `404 PATIENT_NOT_FOUND` |
| Consultar resumen de Agenda | No contiene documento, telefono ni correo |
| Consultar resumen de Facturacion | No contiene telefono, correo ni fecha de nacimiento |
| Usar datos reales en una prueba | Caso invalido: debe reemplazarse por datos ficticios |

## 13. Definition of Done del contrato

El alcance de `ms-pacientes` esta terminado cuando:

1. los endpoints anteriores responden con los JSON y codigos documentados;
2. el UUID publico es el unico identificador expuesto;
3. un documento duplicado devuelve `409`, incluso ante intentos concurrentes;
4. Agenda y Facturacion reciben proyecciones diferentes y minimas;
5. las pruebas usan solamente datos ficticios;
6. el servicio arranca con `docker compose` y Flyway valida el esquema;
7. `/actuator/health` y `/api/v1/patients/ping` responden;
8. el registro y la consulta funcionan directamente y a traves del gateway.
