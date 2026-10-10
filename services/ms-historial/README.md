# ms-historial

Microservicio del expediente clínico de MedicalSys. Es el **único propietario de escritura** de
`medical_records_db`.

| Dato | Valor |
| --- | --- |
| Puerto interno | `8088` |
| Base lógica | `medical_records_db` (MySQL 8) |
| Usuario de base | `medical_records_user` |
| Dirección interna | `http://ms-historial:8088` |
| Acceso desde el host | No. Solo el gateway publica `8080` |
| Responsable | Franco |

## 1. Responsabilidad

Es propietario de: **expediente, alergia, antecedente, encuentro clínico, addendum, signo vital,
diagnóstico, receta, detalle de receta, orden de estudio, resultado de estudio y procedimiento
realizado** (12 tablas del capítulo 9 del diccionario).

**No** guarda:

- Binarios de archivos: viven en MinIO bajo ms-documental; aquí solo se guarda `documentId`.
- Narrativa clínica en claro: se persiste el *ciphertext* producido por ms-criptografía.
- Catálogos globales (CIE-10, tipos de estudio, vademécum): se validan por HTTP contra ms-catálogos.
- Datos de paciente, profesional, cita, organización o sucursal: son UUID externos **sin clave
  foránea**.

## 2. Imagen Docker

La imagen propia se construye desde la raíz del repositorio:

```bash
docker build -t medicalsys/ms-historial:local services/ms-historial
```

`compose.yaml` y la ruta del gateway son responsabilidad de integración centralizada y no forman
parte de este microservicio. Al integrarlo se deben inyectar `DB_URL`, `DB_USER` y `DB_PASSWORD`, y
MySQL debe crear `medical_records_db` y `medical_records_user` con permisos exclusivos sobre esa
base.

> **Requisito de infraestructura (coordinar con quien mantiene el compose):** el servidor MySQL
> compartido debe arrancar con `--log-bin-trust-function-creators=ON`. La migración `V2` crea los
> triggers de inmutabilidad clínica y MySQL rechaza `CREATE TRIGGER` (error 1419) si el binlog está
> activo y el usuario no tiene `SUPER`. Es una sola bandera en el servicio `mysql` del compose; la
> alternativa —dar `SUPER` a `medical_records_user`— rompería el aislamiento de credenciales.

### 2.1 Fuera de Docker

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo
```

## 3. Variables de entorno

| Variable | Obligatoria | Por defecto | Para qué sirve |
| --- | --- | --- | --- |
| `SERVER_PORT` | no | `8088` | Puerto del contenedor |
| `DB_URL` | **sí** | — | `jdbc:mysql://mysql:3306/medical_records_db` |
| `DB_USER` | **sí** | — | `medical_records_user` |
| `DB_PASSWORD` | **sí** | — | Viene de `HISTORIAL_DB_PASSWORD` en `.env` |
| `SPRING_PROFILES_ACTIVE` | no | — | `demo` agrega las semillas ficticias |
| `CRYPTO_MODE` | no | `LOCAL` | `LOCAL` cifra en proceso; `REMOTE` delega en ms-criptografía |
| `CRYPTO_BASE_URL` | no | `http://ms-criptografia:8084` | Solo se usa con `REMOTE` |
| `DEMO_MASTER_KEY` | no | vacío | Clave local de demostración; vacío genera una efímera |
| `CATALOGS_VALIDATION_MODE` | no | `LOCAL` | `LOCAL`, `REMOTE` o `PERMISSIVE` |
| `CATALOGS_BASE_URL` | no | `http://ms-catalogos:8083` | Solo se usa con `REMOTE` |
| `LOG_LEVEL` | no | `INFO` | Nivel de log del paquete propio |

La configuración **no contiene** `localhost` ni contraseñas. `DEMO_MASTER_KEY` nunca se versiona con
un valor real.

## 4. Modos de integración: por qué existen

ms-criptografía y ms-catálogos todavía no publican sus endpoints. Para que la rebanada vertical
funcione hoy sin bloquear a nadie, cada integración tiene un puerto con dos implementaciones y el
modelo de datos persistido es **el mismo** en ambas:

| Dependencia | Modo por defecto | Qué hace | Cómo se cambia |
| --- | --- | --- | --- |
| Cifrado | `CRYPTO_MODE=LOCAL` | AES-256-GCM en proceso con `DEMO_MASTER_KEY` | `CRYPTO_MODE=REMOTE` |
| Catálogos | `CATALOGS_VALIDATION_MODE=LOCAL` | Valida formato y pertenencia a la lista de códigos de demostración | `CATALOGS_VALIDATION_MODE=REMOTE` |

Ambos modos locales emiten un `WARN` al arrancar. El contrato HTTP que se consumirá está declarado
en [`docs/contracts.md`](docs/contracts.md), listo para que Juanjo lo implemente sin sorpresas.

> `PERMISSIVE` solo valida el formato del código. No usarlo para evidencia de Review: con él un
> CIE-10 inexistente **no** se rechazaría.

## 5. Contrato HTTP

Rutas en español (como `/api/v1/pacientes` y `/api/v1/citas`), identificadores y campos JSON en
inglés camelCase, fechas ISO-8601 con zona, dinero en `DECIMAL`. El detalle completo con ejemplos
está en [`docs/contracts.md`](docs/contracts.md).

Resumen:

Desde RBAC-03 cada endpoint exige un permiso del catálogo de ms-identidad (`@RequiresPermission`), no un rol. La columna de roles indica quién lo tiene en la asignación inicial; el permiso exacto de cada endpoint está en [`docs/seguridad/rbac-01-endpoints-permisos.csv`](../../docs/seguridad/rbac-01-endpoints-permisos.csv).

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `GET /api/v1/medical-records/ping` | público | Verificar temporalmente el servicio y el enrutamiento |
| `POST /api/v1/expedientes` | recepción o clínico | Crear u obtener el expediente del paciente (idempotente) |
| `GET /api/v1/expedientes?patientId=` | recepción o clínico | Expediente de un paciente |
| `POST /api/v1/encuentros` | clínico | Abrir encuentro ligado a una cita |
| `PUT /api/v1/encuentros/{id}/notas` | clínico | Narrativa SOAP (se cifra antes de persistir) |
| `POST /api/v1/encuentros/{id}/diagnosticos` | clínico | Diagnóstico CIE-10 validado |
| `POST /api/v1/encuentros/{id}/ordenes-estudio` | clínico | Orden de estudio; devuelve el `orderId` |
| `POST /api/v1/encuentros/{id}/firma` | clínico | Firmar y sellar el encuentro |
| `POST /api/v1/encuentros/{id}/addendums` | clínico | Corrección posterior sin sobrescribir |
| `POST /api/v1/ordenes-estudio/{id}/resultados` | recepción o clínico | Registrar resultado con `documentId` |
| `GET /internal/v1/...` | otro microservicio | Proyecciones mínimas para agenda y documental |

### Formato de error

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 422,
  "code": "ICD10_CODE_NOT_ALLOWED",
  "message": "El codigo CIE-10 'ZZZ999' no existe en el catalogo vigente",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/encuentros/.../diagnosticos"
}
```

El campo `code` es el estable. Los clientes reaccionan a él, no al `message`.

## 6. Seguridad y aislamiento

- El gateway valida el JWT y reescribe `X-Organization-Id`, `X-User-Id`, `X-Roles` y `X-Permissions`. Este servicio
  **nunca** acepta un `organizationId` que venga del cuerpo o de la query.
- Toda consulta está acotada por organización. Un recurso de otro tenant responde **404**, no 403:
  revelar su existencia rompería el aislamiento.
- Los endpoints declaran su permiso con `@RequiresPermission`. Una recepcionista ve que existe un encuentro y
  su estado, pero **no** su narrativa: el contenido descifrado exige `clinical.narrative.read`.
- Los logs llevan `serviceName`, `correlationId` y `organizationId`, y registran solo
  identificadores. Nunca narrativa clínica, ciphertext, claves ni JWT.

## 7. Reglas críticas garantizadas

| Regla | Dónde se garantiza |
| --- | --- |
| Un expediente por organización y paciente | `UNIQUE (organization_public_id, patient_public_id)` + apertura idempotente |
| Una cita produce un solo encuentro | `UNIQUE (appointment_public_id)` + verificación previa con 409 |
| Un CIE-10 inválido se rechaza | `CatalogsClient` antes de persistir, con 422 |
| La narrativa nunca se guarda en claro | Se cifra en el servicio; la base solo ve `VARBINARY` |
| **Un encuentro firmado no se sobrescribe** | Trigger `trg_clinical_encounter_immutable_on_update` (garantía final) + 422 en el servicio |
| Un registro clínico no se borra | Trigger `trg_clinical_encounter_no_delete`; se anula conservando motivo y responsable |
| Un addendum es inmutable | Triggers `trg_encounter_addendum_no_update` / `_no_delete` |
| El resultado no guarda binarios | Solo `document_public_id` hacia ms-documental |
| El IMC no tiene dos verdades | Columna generada `STORED` en la base |

## 8. Pruebas

```bash
./mvnw test
```

- **Unitarias y de contrato web**: se ejecutan siempre.
- **De integración** (`*ContainerTest`, `HistorialApplicationTests`): usan Testcontainers con MySQL
  8.4 real. Si Docker no está activo se **omiten** en vez de fallar; antes de la Review deben
  correrse con Docker encendido, porque son la evidencia de que la migración funciona desde una base
  vacía y de que el `UPDATE` sobre un encuentro firmado es rechazado.

### Recorrido de humo

Con el servicio levantado, [`docs/smoke.sh`](docs/smoke.sh) ejecuta el guion completo de la demo
contra la API real y verifica también los casos negativos:

```bash
BASE=http://localhost:8088 ./docs/smoke.sh
```

Evidencia adicional: ejecutar en `medical_records_db` después de firmar un encuentro:

```sql
UPDATE clinical_encounter
SET encrypted_subjective_notes = 'x'
WHERE status = 'SIGNED'
LIMIT 1;
-- ERROR 1644 (45000): CLINICAL_ENCOUNTER_IMMUTABLE: un encuentro firmado no se sobrescribe
```

## 9. Decisiones registradas

1. **Columna `encrypted` de `clinical_encounter`.** El diccionario la documenta con el tipo físico
   `A`, que no existe en MySQL. Se conserva el nombre normativo y se implementa como
   `VARBINARY(8192) NULL`, igual que las demás narrativas del encuentro (motivo de consulta cifrado).
   Queda anotado también en la migración `V1`.
2. **`spring.jpa.hibernate.ddl-auto: validate`.** Flyway es el único que crea o modifica el esquema;
   Hibernate lo valida al arrancar. Los mapeos declaran explícitamente los tipos físicos
   `BINARY(16)`, `SMALLINT UNSIGNED` y `TINYINT UNSIGNED` para que la validación corresponda con
   MySQL. `SchemaConformanceContainerTest` comprueba además tablas, restricciones, índices, ausencia
   de FK entre bases, columna generada y triggers contra `INFORMATION_SCHEMA`.
3. **Equivalencia de estados con el lenguaje del sprint.** El diccionario manda: `study_order.status`
   usa `REQUESTED` ("solicitada") y `REPORTED` ("disponible"); el tipo de estudio de rayos X es
   `X_RAY`. Los nombres en español se conservan solo como trazabilidad.
4. **Correlativos.** `record_number` y `folio` usan `PREFIJO-AAAA-NNNNNN` con relleno de ceros para
   que el máximo lexicográfico coincida con el numérico. La unicidad real la impone la base; el
   servicio reintenta ante colisión concurrente y devuelve 409 si agota los intentos.
5. **Sello de firma.** `content_hash` es el SHA-256 del *ciphertext* y de los datos estructurales del
   encuentro, no del texto en claro: puede verificarse sin descifrar nada y cualquier alteración
   posterior lo rompe.

## 10. Deuda consciente

Fuera del alcance de este sprint, por decisión del equipo: eventos y outbox/inbox, auditoría,
notificaciones automáticas, firma digital real con certificado, rotación de claves, Vault,
reintentos con backoff, réplicas de referencia y visor DICOM.

## 11. Datos para integración central

```text
Servicio: ms-historial
Puerto interno: 8088
Base de datos: medical_records_db (MySQL)
Usuario de base: medical_records_user
Variable de contraseña: HISTORIAL_DB_PASSWORD
Ruta del gateway: /api/v1/medical-records/**
Endpoint de prueba: GET /api/v1/medical-records/ping
Imagen local: medicalsys/ms-historial:local
Resultado de docker build: exitoso
```

La integración debe considerar `--log-bin-trust-function-creators=ON` en el MySQL compartido para
que el usuario aislado pueda crear los triggers de inmutabilidad sin recibir privilegios `SUPER`.
