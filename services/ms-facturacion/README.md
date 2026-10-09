# ms-facturación

Microservicio de facturación de MedicalSys. Es el **único propietario de escritura** de `billing_db`.

> ⚠️ **Las facturas de este servicio no tienen validez tributaria.** El adaptador fiscal es un stub
> determinista sin red ni credenciales: toda factura sale con estado `DEMO_ACCEPTED`, con
> `demo: true` y con la leyenda `NO VALIDA TRIBUTARIAMENTE`.

| Dato | Valor |
| --- | --- |
| Puerto interno | `8090` |
| Base lógica | `billing_db` (MySQL 8) |
| Usuario de base | `billing_user` |
| Dirección interna | `http://ms-facturacion:8090` |
| Acceso desde el host | No. Solo el gateway publica `8080` |
| Responsable | Franco |

## 1. Responsabilidad

Es la única autoridad de escritura de `billing_db`, que tiene **las 12 tablas del capítulo 11 del
diccionario**. El código de este sprint cubre el flujo de **configuración fiscal, punto de venta, CUIS,
CUFD, correlativo, factura y detalle de factura**; las otras cinco tablas existen en el esquema pero
todavía no tienen entidad ni endpoint (ver §9.1).

El detalle campo por campo, con la verificación contra el diccionario, está en
[`docs/diccionario-datos.md`](docs/diccionario-datos.md).

**No** guarda:

- Narrativa clínica, diagnósticos, alergias ni ninguna tabla del expediente. La única trazabilidad
  hacia la atención son los UUID `appointmentId` y `clinicalEncounterId` dentro del detalle, **sin
  clave foránea** y sin ningún dato de la atención. Lo comprueba `SchemaConformanceContainerTest`.
- Datos de paciente: pide el **DTO fiscal mínimo** a ms-pacientes y congela documento y nombre en la
  factura.
- Precios ni catálogo de prestaciones: los publica ms-tenencia y se congelan como snapshot.
- Credenciales del SIN, token delegado ni certificado de firma. La columna
  `encrypted_delegated_token` existe en el modelo y **siempre queda nula** en este sprint.

## 2. Imagen Docker

```bash
docker build -t medicalsys/ms-facturacion:local services/ms-facturacion
```

`compose.yaml` y la ruta del gateway son responsabilidad de integración centralizada y no forman
parte de este microservicio. Al integrarlo se deben inyectar `DB_URL`, `DB_USER` y `DB_PASSWORD`, y
MySQL debe crear `billing_db` y `billing_user` con permisos **exclusivos** sobre esa base
(`infra/mysql/init/01-create-databases.sh` ya lo hace).

No requiere ninguna bandera especial del servidor MySQL: no crea triggers ni funciones.

### 2.1 Fuera de Docker

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## 3. Variables de entorno

| Variable | Obligatoria | Por defecto | Para qué sirve |
| --- | --- | --- | --- |
| `SERVER_PORT` | no | `8090` | Puerto del contenedor |
| `DB_URL` | **sí** | — | `jdbc:mysql://mysql:3306/billing_db` |
| `DB_USER` | **sí** | — | `billing_user` |
| `DB_PASSWORD` | **sí** | — | Viene de `FACTURACION_DB_PASSWORD` en `.env` |
| `PATIENTS_MODE` | no | `LOCAL` | `LOCAL` deriva un DTO fiscal ficticio; `REMOTE` consulta ms-pacientes |
| `PATIENTS_BASE_URL` | no | `http://ms-pacientes:8085` | Solo se usa con `REMOTE` |
| `TENANCY_MODE` | no | `LOCAL` | `LOCAL` usa el catálogo de demostración; `REMOTE` consulta ms-tenencia |
| `TENANCY_BASE_URL` | no | `http://ms-tenencia:8082` | Solo se usa con `REMOTE` |
| `TAX_AUTHORITY_MODE` | no | `SIMULATED` | Único modo implementado |
| `TAX_AUTHORITY_DEMO_CAPTION` | no | leyenda no tributaria | Texto impreso en toda factura |
| `BILLING_DEMO_SETUP` | no | `true` | Crea configuración fiscal y caja si el tenant no las tiene |
| `BILLING_DEMO_NIT` | no | `0000000000` | NIT ficticio del entorno local |
| `BILLING_DEMO_LEGAL_NAME` | no | `CLINICA DEMO MEDICALSYS` | Razón social ficticia |
| `LOG_LEVEL` | no | `INFO` | Nivel de log del paquete propio |

La configuración **no contiene** `localhost`, contraseñas ni credenciales fiscales.

## 4. Modos de integración: por qué existen

ms-pacientes y ms-tenencia todavía no publican sus rutas internas, y no existe integración real con
el SIN. Para que la rebanada vertical funcione hoy sin bloquear a nadie, cada dependencia tiene un
puerto con implementación local y el modelo de datos persistido es **el mismo** en ambos modos:

| Dependencia | Modo por defecto | Qué hace | Cómo se cambia |
| --- | --- | --- | --- |
| Cliente fiscal | `PATIENTS_MODE=LOCAL` | Deriva un DTO ficticio determinista del UUID del paciente | `PATIENTS_MODE=REMOTE` |
| Prestaciones y precios | `TENANCY_MODE=LOCAL` | Catálogo de demostración de `application.yaml` (CONSULTATION 150.00, X_RAY 250.00) | `TENANCY_MODE=REMOTE` |
| Sistema fiscal | `TAX_AUTHORITY_MODE=SIMULATED` | Stub determinista sin red; `DEMO_ACCEPTED` + leyenda | No hay adaptador real en este sprint |

Los tres emiten un `WARN` al arrancar. Los contratos HTTP que se consumirán están declarados en
[`docs/contracts.md`](docs/contracts.md), §6, listos para Nicole (NI-03) y para FR-03.

### 4.1 Qué hace y qué no hace el adaptador fiscal simulado

**Hace:** deriva CUIS, CUFD y CUF de un SHA-256 de los datos del documento (mismos datos → mismo
código, para que la demo sea reproducible); prefija todo con `DEMO-`; devuelve `DEMO_ACCEPTED` y la
leyenda no tributaria.

**No hace, por decisión explícita:** no abre ninguna conexión de red; no usa credenciales, token
delegado ni certificado; no implementa el algoritmo real de CUF ni su dígito verificador —copiarlo
sin validación normativa daría una falsa sensación de validez fiscal—; no devuelve una URL de QR
inventada (`qrUrl` queda nula); y no afirma en ningún campo que el documento sea válido ante la
autoridad tributaria.

## 5. Contrato HTTP

Rutas en español, campos JSON en inglés camelCase, fechas ISO-8601 con zona, dinero en `DECIMAL`.
El detalle completo con ejemplos está en [`docs/contracts.md`](docs/contracts.md).

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `GET /api/v1/billing/ping` | público | Verificar temporalmente el servicio y el enrutamiento |
| `POST /api/v1/facturas` | caja, recepción o admin | Emitir factura desde paciente y prestaciones |
| `GET /api/v1/facturas/{id}` | caja, recepción o admin | Factura con detalle, correlativo y etiqueta demo |
| `GET /api/v1/facturas?patientId=` | caja, recepción o admin | Listado paginado |
| `GET /internal/v1/facturas/{id}` | otro microservicio | Proyección mínima |

El cuerpo del `POST` **no acepta** `organizationId` (lo impone el gateway), ni precios, ni datos del
cliente: quien emite elige **qué** se cobra y **cuánta** cantidad, nunca **a qué precio**.

## 6. Seguridad y aislamiento

- El gateway valida el JWT y reescribe `X-Organization-Id`, `X-User-Id` y `X-Roles`. Este servicio
  **nunca** acepta un `organizationId` que venga del cuerpo o de la query.
- Toda consulta está acotada por organización. Una factura de otro tenant responde **404**, no 403:
  revelar su existencia rompería el aislamiento.
- Los endpoints declaran su rol con `@RequiresRole`.
- Los logs llevan `serviceName`, `correlationId` y `organizationId`, y registran solo
  identificadores, correlativos e importes. Nunca un JWT, una credencial fiscal ni un dato clínico.
- `billing_user` solo tiene permisos sobre `billing_db`; en particular **no** puede leer
  `medical_records_db`.

## 7. Reglas críticas garantizadas

| Regla | Dónde se garantiza |
| --- | --- |
| **El total coincide con la suma del detalle** | `Invoice.recalculateTotals()` es la única fuente de los importes + `totalsMatchItems()` antes de persistir + `InvoiceTotalsTest` e `InvoicePersistenceContainerTest` |
| El subtotal de una línea lo calcula el dominio | `InvoiceItem.of()`; nunca llega desde el cliente |
| **El correlativo no se repite** | `SELECT ... FOR UPDATE` sobre `document_sequence` + `UNIQUE (point_of_sale_id, tax_document_type, invoice_number)` |
| Una prestación inválida no consume correlativo | Se resuelven todas las prestaciones antes de tocar la base |
| El precio no lo elige quien factura | Snapshot de ms-tenencia; el request no tiene campo de precio |
| Los datos del cliente no los elige quien factura | Snapshot del DTO fiscal de ms-pacientes |
| La factura se marca como demostración | `status = DEMO_ACCEPTED`, `demo: true` y leyenda no tributaria |
| No hay FK entre bases | `SchemaConformanceContainerTest.declaresOnlyLocalForeignKeys` |
| La base fiscal no contiene tablas ni narrativa clínica | `SchemaConformanceContainerTest` (dos pruebas) |
| El dinero nunca usa coma flotante | `DECIMAL` en la base, `BigDecimal` y `Money` en Java |

## 8. Pruebas

```bash
./mvnw test
```

95 pruebas: **unitarias y de contrato web** (se ejecutan siempre) y **de integración**
(`*ContainerTest`, `FacturacionApplicationTests`) con Testcontainers y MySQL 8.4 real. Si Docker no
está activo se **omiten** en vez de fallar; antes de la Review deben correrse con Docker encendido,
porque son la evidencia de que la migración funciona desde una base vacía, de que el total cuadra y
de que el correlativo no se repite.

### Recorrido de humo

Con el servicio levantado, [`docs/smoke.sh`](docs/smoke.sh) ejecuta el paso "Cobrar" de la demo
contra la API real y verifica también los casos negativos:

```bash
BASE=http://localhost:8090 ./docs/smoke.sh
```

Evidencia adicional, en `billing_db`:

```sql
-- El total siempre cuadra con su detalle: la consulta no debe devolver ninguna fila.
SELECT f.id, f.total_amount, SUM(i.subtotal) AS suma
FROM invoice f JOIN invoice_item i ON i.invoice_id = f.id
GROUP BY f.id, f.total_amount
HAVING f.total_amount <> SUM(i.subtotal);

-- El correlativo no se repite: la consulta no debe devolver ninguna fila.
SELECT point_of_sale_id, tax_document_type, invoice_number, COUNT(*)
FROM invoice
GROUP BY point_of_sale_id, tax_document_type, invoice_number
HAVING COUNT(*) > 1;
```

## 9. Decisiones registradas

### 9.1 El esquema está completo; el código, no

La migración `V1` crea las 7 tablas que necesita el guion de la demostración —configurar,
correlacionar, emitir y consultar—: `billing_configuration`, `point_of_sale`, `cuis`, `cufd`,
`document_sequence`, `invoice` e `invoice_item`. `cuis` y `cufd` se incluyen porque `invoice.cufd_id`
es `NOT NULL` en el diccionario.

La migración `V2` completa el capítulo 11 con las cinco restantes: `economic_activity`, `payment`,
`credit_debit_note`, `significant_event` y `tax_submission`. Se agregó en una migración nueva porque
una migración ya aplicada no se edita.

Estas cinco son **solo DDL**: no tienen entidad JPA, repositorio ni endpoint, porque todavía no hay
flujo que las use (ver §10). La decisión fue que el esquema sea conforme al diccionario desde ya, en
lugar de arrastrar una base incompleta hasta el sprint que implemente pagos y anulación.

### 9.2 `invoice.patient_public_id`

El diccionario no lo lista entre las columnas de `invoice`, pero declara el índice
`ix_invoice_patient_id_issued_at` y la matriz de referencias externas asigna
`billing_db → patient_public_id` a ms-pacientes ("DTO fiscal mínimo"). Se implementa como
`BINARY(16) NOT NULL` sin FK, con el nombre canónico de la plataforma y su índice.

### 9.3 `invoice.status`

Tampoco aparece en la lista de columnas, pero el diccionario declara el índice
`ix_invoice_status_issued_at` y el CHECK `estado <> 'VOIDED' OR (voided_at IS NOT NULL AND ...)`. Se
implementa como `ENUM` con los valores de `credit_debit_note` (`ISSUED`, `SEND_PENDING`, `VALIDATED`,
`REJECTED`, `VOIDED`) más `DEMO_ACCEPTED`, que es el estado que el sprint exige para una factura de
demostración.

Ambas quedan anotadas también en la cabecera de la migración `V1`.

### 9.4 Configuración DEMO por alta automática, no por semilla SQL

Con `BILLING_DEMO_SETUP=true` (por defecto) el servicio crea la configuración fiscal, el punto de
venta y el CUIS/CUFD simulados de una organización que todavía no los tiene. Se eligió esto en lugar
de una migración repetible `R__seed_demo` por tres razones: funciona para cualquier `organizationId`
—no solo el UUID sembrado—, es idempotente por construcción (`demoSetupIsCreatedOnce` lo comprueba) y
el mismo código sirve en un entorno real apagando el interruptor, donde la configuración la carga un
administrador y la ausencia produce un `422` claro.

El CUFD, en cambio, se renueva siempre a través del `TaxAuthorityGateway`: es una responsabilidad del
sistema fiscal tanto en la simulación como en una integración real.

### 9.5 El correlativo es una tabla, no un `AUTO_INCREMENT`

El correlativo fiscal pertenece al punto de venta, no a la tabla de facturas: dos cajas numeran en
paralelo desde uno y el contador debe poder auditarse. `document_sequence` se bloquea con
`SELECT ... FOR UPDATE` dentro de la transacción de emisión.

### 9.6 Emitir no es idempotente

Cada `POST` consume un correlativo. El gateway y los clientes no deben reintentar automáticamente un
`POST` fallido sin una clave de idempotencia (guía Sprint 2, §4.6). La clave queda en el backlog.

## 10. Deuda consciente

Fuera del alcance de este sprint, por decisión del equipo: integración real con el SIAT (CUIS, CUFD y
CUF reales, firma digital con certificado, envío y validación); anulación de facturas y notas de
crédito/débito; registro de pagos; eventos significativos y contingencia offline; generación de XML y
PDF hacia ms-documental; clave de idempotencia en el `POST`; eventos y outbox/inbox; auditoría;
notificaciones automáticas.

Lo que falta de estos flujos es **código**, no esquema: `economic_activity`, `payment`,
`credit_debit_note`, `significant_event` y `tax_submission` ya están creadas por `V2` con la forma
exacta del diccionario, a la espera de su entidad y su endpoint.

## 11. Datos para integración central

```text
Servicio: ms-facturacion
Puerto interno: 8090
Base de datos: billing_db (MySQL)
Usuario de base: billing_user
Variable de contraseña: FACTURACION_DB_PASSWORD
Ruta del gateway: /api/v1/billing/** y /api/v1/facturas/**
Endpoint de prueba: GET /api/v1/billing/ping
Imagen local: medicalsys/ms-facturacion:local
Resultado de docker build: exitoso
```

Variables que el `compose.yaml` debe inyectar:

```yaml
SERVER_PORT: "8090"
DB_URL: jdbc:mysql://mysql:3306/billing_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER: billing_user
DB_PASSWORD: ${FACTURACION_DB_PASSWORD}
```

Puerto opcional al host para depurar: `BILLING_HOST_PORT` (ya está en `.env.example`).

> **Dos rutas en el gateway.** `/api/v1/billing/**` es el ping acordado en la tabla de rutas;
> `/api/v1/facturas/**` es el recurso de negocio, en español como el resto de la plataforma. Ambas
> apuntan a `http://ms-facturacion:8090`.
