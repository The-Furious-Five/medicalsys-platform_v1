# ms-marketing

Microservicio de campañas, segmentación de audiencias, cupones y fidelización de MedicalSys.
Es el **único propietario de escritura** de `marketing_db`.

> 🔒 **Este servicio no puede leer el expediente clínico.** No es una promesa de comportamiento: es
> una propiedad estructural. Su base no contiene ni una tabla clínica, no tiene credenciales de
> `medical_records_db` y no existe ningún cliente HTTP hacia ms-historial. Tres pruebas
> automatizadas lo verifican en cada ejecución de la suite.

| Dato | Valor |
| --- | --- |
| Puerto interno | `8092` |
| Base lógica | `marketing_db` (MySQL 8) |
| Usuario de base | `marketing_user` |
| Dirección interna | `http://ms-marketing:8092` |
| Runtime | Spring Boot MVC (Boot 4.1, Java 21) |
| Criterio de descomposición | **C5** (opcionalidad) + **C3** (frontera de privacidad) |
| Publica | `marketing.campana` |
| Consume | `agenda.cita`, `facturacion.factura`, `pacientes.paciente`, `tenencia.servicio`, `tenencia.organizacion`, `historial.encuentro`, `notificaciones.mensaje` |

---

## 1. Por qué es un servicio propio

La sección 5.12 del documento de descomposición da tres razones. Vale la pena leer qué consecuencia
concreta tiene cada una en este código, porque las tres son verificables y no retóricas.

### 1.1 Es opcional por contrato — criterio C5

`plan_suscripcion.incluye_marketing` admite el valor 0. «Un módulo que puede no existir para un
cliente es, por definición, un servicio aparte».

Aquí eso no se queda en un argumento de diseño. `MarketingEntitlementService` comprueba
`ref_organization.includes_marketing` al principio de **cada** caso de uso de gestión: el contenedor
puede estar desplegado y sano y aun así responder `403 MARKETING_NOT_INCLUDED_IN_PLAN` a un tenant
que no lo contrató. Sin esa comprobación, la opcionalidad no tendría ninguna consecuencia en tiempo
de ejecución.

### 1.2 Es el servicio de menor criticidad del sistema

Si cae, nadie deja de ser atendido. Eso permite darle menos réplicas, menos recursos y una ventana de
mantenimiento distinta — y obliga a que su única llamada síncrona entrante degrade bien: un `503` de
`POST /internal/v1/promociones/validar` significa para ms-facturacion «emita sin descuento», no
«no emita». El pool de Hikari es de 8 conexiones, no de 10 como el de facturación: es la ventaja
concreta de haber separado las bases.

### 1.3 Define una frontera de privacidad explícita — criterio C3

Es la razón más fuerte. La segmentación se evalúa **solo** sobre datos administrativos y
demográficos: edad, sexo, ciudad, última visita, servicios facturados. Ningún diagnóstico CIE-10 ni
campo cifrado del expediente participa jamás, porque cruzar patología con publicidad sería una
filtración de dato sensible de salud.

En el monolito esa promesa se sostenía con un `CHECK (usa_datos_clinicos = 0)` y con disciplina:
marketing compartía base con el historial y **un `JOIN` accidental bastaba para violarla**.

## 2. La promesa de privacidad, hecha código

Aquí la garantía es arquitectónica y descansa en cinco hechos, cada uno con su verificación:

| # | Hecho | Dónde vive | Qué lo verifica |
| --- | --- | --- | --- |
| 1 | `marketing_db` no contiene ni una tabla clínica | `V1__create_schema.sql` | `SchemaConformanceContainerTest#containsNoClinicalTable` (13 nombres) |
| 2 | Ninguna columna nombra un dato clínico | idem | `…#containsNoClinicalColumn` |
| 3 | No hay credenciales ni ruta de red hacia `medical_records_db` | `infra/mysql/init`, `compose.yaml` | `MarketingIsolationTest#doesNotReferenceMedicalRecordsService` |
| 4 | La consulta de segmentación lee **dos** tablas y solo dos | `AudienceQueryRepository.AUDIENCE_FROM` | `MarketingIsolationTest#audienceQueryReadsOnlyTwoTables` |
| 5 | El `CHECK (uses_clinical_data = 0)` lo aplica el motor | `V1__create_schema.sql` | `…#privacyCheckIsEnforcedByTheEngine` — **intenta violarlo**, no lee su definición |

A eso se suma la barrera de entrada. El aislamiento hace *imposible* que marketing **lea** el
expediente; queda la vía de que alguien lo **empuje** hacia aquí. `ClinicalDataGuard` la cierra:

- **Lista blanca de ejes** (`SegmentCriteriaField`, 13 entradas). Un criterio cuyo nombre no esté ahí
  se rechaza, en vez de ignorarse. Una lista negra dejaría pasar cualquier eje nuevo que nadie pensó
  en prohibir.
- **Detección de vocabulario clínico** sobre criterios y sobre cuerpos de evento, recursiva y
  tolerante a acentos, mayúsculas y separadores: `Diagnóstico-CIE 10`, `diagnosticoCie10` y
  `DIAGNOSTICO` caen en la misma raíz. Devuelve `422 CLINICAL_DATA_NOT_ALLOWED` y **registra el
  nombre del campo, nunca su valor** — registrar el valor sería filtrar justo el dato que se rechaza.

```bash
# Un intento de segmentar por patología:
curl -X POST $BASE/api/v1/segmentos -H "X-Roles: ADMINISTRATOR" … \
  -d '{"name":"Asmaticos","criteria":{"diagnosticoCie10":"J45"}}'
# → 422 CLINICAL_DATA_NOT_ALLOWED
```

### 2.1 El detalle de `EncuentroFirmado`

La sección 5.12 lo señala y aquí está implementado literalmente: el evento del expediente transporta
`suggestedNextVisitOn` —la **fecha** del control— y no el motivo clínico. `EventIngestionService`
lee ese único campo, y la firma de `PatientActivity#setSuggestedNextVisit(LocalDate)` no tiene ningún
parámetro donde el motivo pudiera colarse.

**Marketing sabe cuándo volver a llamar, no de qué padece el paciente.**

Si el productor incluye además el motivo, el evento entero se rechaza: no se proyecta a medias.

## 3. Qué guarda — y qué no

Nueve tablas propias, tres réplicas `ref_*` y el par outbox/inbox. El detalle campo por campo, con la
equivalencia con el esquema de referencia en español, está en
[`docs/diccionario-datos.md`](docs/diccionario-datos.md).

**`patient_activity`** es la tabla nueva del sprint y la pieza que hace viable la separación. En el
monolito, segmentar exigía `JOIN` contra `cita` y `factura`; al no existir aquí, se mantiene una
proyección comercial alimentada por eventos: primera y última visita, total de citas, inasistencias,
total facturado, último servicio. Es toda la «historia» que marketing conoce — **cuándo vino y cuánto
gastó, nunca por qué vino**.

**No** guarda: narrativa clínica, diagnósticos, alergias, ninguna tabla del expediente, ni
credenciales de proveedores de mensajería (eso es de ms-notificaciones).

## 4. Imagen Docker

```bash
docker build -t medicalsys/ms-marketing:local services/ms-marketing
```

Al integrarlo hay que inyectar `DB_URL`, `DB_USER` y `DB_PASSWORD`, y MySQL debe crear `marketing_db`
y `marketing_user` con permisos **exclusivos** sobre esa base
(`infra/mysql/init/01-create-databases.sh` ya lo hace). El aislamiento de credenciales es parte de la
frontera de privacidad: ni siquiera un fallo del servicio podría alcanzar `medical_records_db`.

No requiere ninguna bandera especial del servidor MySQL: no crea triggers ni funciones.

### 4.1 Fuera de Docker

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## 5. Variables de entorno

| Variable | Obligatoria | Por defecto | Para qué sirve |
| --- | --- | --- | --- |
| `SERVER_PORT` | no | `8092` | Puerto del contenedor |
| `DB_URL` | **sí** | — | `jdbc:mysql://mysql:3306/marketing_db` |
| `DB_USER` | **sí** | — | `marketing_user` |
| `DB_PASSWORD` | **sí** | — | Viene de `MARKETING_DB_PASSWORD` en `.env` |
| `PATIENTS_MODE` | no | `LOCAL` | `LOCAL` deriva demografía ficticia; `REMOTE` consulta ms-pacientes |
| `PATIENTS_BASE_URL` | no | `http://ms-pacientes:8085` | Solo con `REMOTE` |
| `TENANCY_MODE` | no | `LOCAL` | `LOCAL` deriva la prestación; `REMOTE` consulta ms-tenencia |
| `TENANCY_BASE_URL` | no | `http://ms-tenencia:8082` | Solo con `REMOTE` |
| `ENTITLEMENT_MODE` | no | `REPLICA` | `REPLICA` respeta `includes_marketing`; `ALWAYS_INCLUDED` lo ignora |
| `MARKETING_DEFAULT_INCLUDED` | no | `true` | Qué asumir para un tenant sin plan replicado |
| `MARKETING_EXCLUDED_TENANTS` | no | vacío | Tenants a los que negar el módulo (para demostrar el 403) |
| `MARKETING_AUDIENCE_MAX` | no | `10000` | Tope de destinatarios de una campaña |
| `MARKETING_OUTBOX_MODE` | no | `LOG` | `KAFKA` publica de verdad en `marketing.campana`; `LOG` drena y solo registra; `RETAIN` acumula para un relay externo. Compose usa `KAFKA` |
| `MARKETING_OUTBOX_BATCH` | no | `100` | Eventos por pasada del relay |
| `MARKETING_OUTBOX_INTERVAL_MS` | no | `5000` | Cadencia del relay |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:19092` | Solo con `MARKETING_OUTBOX_MODE=KAFKA` |
| `MARKETING_DEMO_SEED` | no | `true` | Siembra tenant, programa, cupón y 12 pacientes ficticios |
| `MARKETING_DEMO_ORGANIZATION` | no | `…0000a1` | Tenant de demostración |
| `MARKETING_DEMO_PATIENTS` | no | `12` | Cuántos pacientes ficticios sembrar |
| `LOG_LEVEL` | no | `INFO` | Nivel del paquete propio |

La configuración **no contiene** `localhost`, contraseñas ni ninguna propiedad que apunte a
ms-historial. Esa ausencia es deliberada y está comprobada.

## 6. Modos de integración: por qué existen

ms-pacientes y ms-tenencia ya publican rutas internas, pero el servicio debe poder levantarse solo.
Cada dependencia tiene un puerto con implementación local, y **el modelo de datos persistido es el
mismo en ambos modos**:

| Dependencia | Modo por defecto | Qué hace | Cómo se cambia |
| --- | --- | --- | --- |
| Demografía | `PATIENTS_MODE=LOCAL` | Deriva demografía ficticia y determinista del UUID | `PATIENTS_MODE=REMOTE` → `GET /internal/v1/patients/{id}` |
| Prestaciones | `TENANCY_MODE=LOCAL` | Deriva la prestación del UUID | `TENANCY_MODE=REMOTE` → `GET /internal/v1/prestaciones/{id}` |

Ambos emiten un `WARN` al arrancar. En modo `REMOTE`, la respuesta se vincula a
`PatientDemographics`, un record deliberadamente estrecho: aunque ms-pacientes devuelva la ficha
completa, aquí **solo entran cinco campos demográficos**.

La réplica se alimenta normalmente por eventos; el cliente HTTP cubre el hueco de que llegue un
evento de facturación de un paciente cuyo evento de alta todavía no llegó. El orden entre productores
distintos no lo garantiza nadie.

## 7. Contrato HTTP

El detalle completo con ejemplos está en [`docs/contracts.md`](docs/contracts.md). En resumen:

Desde RBAC-03 cada endpoint exige un permiso del catálogo de ms-identidad (`@RequiresPermission`), no un rol. La columna de roles indica quién lo tiene en la asignación inicial; el permiso exacto de cada endpoint está en [`docs/seguridad/rbac-01-endpoints-permisos.csv`](../../docs/seguridad/rbac-01-endpoints-permisos.csv).

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `GET /api/v1/marketing/ping` | público | Verificar el servicio y el enrutamiento |
| `GET /api/v1/marketing/criterios-permitidos` | público | **Publica los 13 ejes de segmentación admitidos** |
| `POST /api/v1/segmentos` · `POST /api/v1/segmentos/vista-previa` | gestor, admin | Definir y previsualizar audiencias |
| `POST /api/v1/campanas` · `…/audiencia` · `…/lanzamiento` | gestor, admin | Ciclo de la campaña |
| `GET /api/v1/campanas/{id}/rendimiento` | gestor, admin, recepción | Tasas de envío, lectura y conversión |
| `POST /api/v1/promociones/validar` · `…/canjes` | caja, recepción | Cupones (contrato con ms-facturacion) |
| `GET /api/v1/anuncios/vigentes` | **paciente** | Anuncios del portal |
| `GET /api/v1/fidelidad/saldo?patientId=` | paciente, caja | Saldo de puntos |
| `PUT /api/v1/pacientes/{id}/opt-out` | **paciente** | Exclusión de comunicaciones comerciales |
| `POST /internal/v1/eventos` | otro microservicio | Entrada de eventos (sustituye al broker) |
| `POST /internal/v1/promociones/validar` | ms-facturacion | Validación de cupón |
| `GET /internal/v1/campanas/{id}/destinatarios` | ms-notificaciones | Audiencia a entregar |

Rutas en español, campos JSON en inglés `camelCase`. El cuerpo **no acepta** `organizationId`: lo
impone el gateway desde el JWT.

## 8. Decisiones de diseño que conviene conocer

### 8.1 El ciclo de la campaña son cuatro pasos, no uno

`crear → materializar audiencia → lanzar → medir`. Entre materializar y lanzar cabe la revisión
humana de a cuánta gente va a llegar el mensaje. Es la única protección real contra un criterio mal
escrito, y una campaña lanzada ya no se recoge.

### 8.2 La audiencia se congela, no se reevalúa

Seis meses después hay que poder responder a quién se le mandó una campaña y por qué quedó fuera
quien quedó fuera. Un criterio reevaluado daría una respuesta distinta cada vez.

Los excluidos por opt-out se guardan **como filas con su motivo**, no se omiten: una fila ausente no
prueba que la exclusión se evaluara.

### 8.3 El evento no lleva la audiencia (*claim check*)

`marketing.campana` transporta el hecho y el tamaño; los destinatarios se piden por
`GET /internal/v1/campanas/{id}/destinatarios`. Meter 5 000 teléfonos en un mensaje sería un cuerpo
enorme, difícil de reintentar y, sobre todo, publicaría datos de contacto en un canal que otros
servicios pueden leer. Quien necesita la lista es exactamente uno, y la pide.

### 8.4 El outbox existe aunque no haya broker

El evento se escribe en `outbox_event` **en la misma transacción** que el cambio de estado. Sin eso,
una caída entre ambas escrituras dejaría una campaña `RUNNING` de la que ms-notificaciones nunca se
entera. El MVP no tiene broker: `OutboxRelay` es la costura donde irá, y el resto del servicio ya
está escrito contra el outbox y no contra el transporte.

### 8.5 El libro mayor de puntos es *append-only*

El saldo se deriva sumando asientos; no hay columna de saldo que alguien pueda corregir a mano.
Cuando un paciente reclama que le faltan puntos, la respuesta es la lista de movimientos con su
procedencia. La acumulación **no tiene ruta HTTP**: llega por evento con su clave de idempotencia.

### 8.6 El opt-out vive aquí

Por disponibilidad —el paciente debe poder ejercerlo contra el servicio que le manda la publicidad,
aunque ms-pacientes esté caído— y por momento de aplicación: la exclusión se evalúa al materializar
la audiencia, antes de que exista mensaje alguno.

### 8.7 La audiencia se acota por tenant a través de `patient_activity`

Un paciente al que esta clínica nunca atendió no es alcanzable desde sus campañas, aunque exista en
la réplica porque otra clínica de la plataforma sí lo atendió. Es aislamiento entre tenants.

## 9. Ejecutar y comprobar

### 9.1 Con Compose

```bash
docker compose up -d mysql ms-marketing
docker compose logs -f ms-marketing
```

Con `MARKETING_DEMO_SEED=true` (por defecto en local) el servicio siembra al arrancar el tenant
replicado, un programa de fidelidad, un cupón `DEMO10` y 12 pacientes ficticios con actividad
repartida — de modo que un `docker compose up` deja un servicio **usable**, no solo sano.

### 9.2 Recorrido de humo

```bash
cd services/ms-marketing
BASE=http://localhost:8092 ./docs/smoke.sh
```

Los 20 pasos recorren el camino completo y comprueban el `422` ante un criterio clínico, la exclusión
por opt-out registrada con su motivo, la idempotencia del canje y de la acumulación, la publicación
de `marketing.campana`, y los `401`/`403`/`404`.

Va **directo al servicio** y no por el gateway, a propósito: fabrica cuatro identidades distintas
—admin, caja, paciente y otra organización— para poder comprobar el `403` por rol y el `404` entre
tenants. El gateway, correctamente, borra cualquier `X-Organization-Id` / `X-Roles` / `X-Permissions` que mande el
cliente y los recrea desde el JWT.

### 9.3 Camino autenticado completo, por el gateway

```bash
docker compose up -d mysql redis ms-identidad ms-marketing ms-gateway

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/identity/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@medicalsys.demo","password":"Demo1234!"}' | jq -r .accessToken)

curl -s http://localhost:8080/api/v1/campanas -H "Authorization: Bearer $TOKEN"

# La frontera de privacidad sigue en pie con un JWT válido: 422 CLINICAL_DATA_NOT_ALLOWED
curl -s -X POST http://localhost:8080/api/v1/segmentos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Diabeticos","criteria":{"diagnosticoCie10":"E11"}}'
```

El tenant del JWT (`organizationId`) coincide con el que siembra este servicio porque ambos leen
`IDENTITY_DEMO_ORGANIZATION` de `compose.override.yaml`.

### 9.4 Pruebas

```bash
./mvnw test        # 107 pruebas; las de contenedor se omiten si Docker está apagado
```

| Clase | Qué prueba |
| --- | --- |
| `ClinicalDataGuardTest` | 23 casos de la barrera de entrada, sin Spring |
| `MarketingIsolationTest` | Que no existe referencia alguna a ms-historial y que el `FROM` de la segmentación toca dos tablas |
| `SchemaConformanceContainerTest` | 49 casos contra MySQL real: tablas, tipos, restricciones, vistas y el `CHECK` de privacidad |
| `MarketingFlowContainerTest` | 16 casos del recorrido funcional completo |
| `EntitlementTest` | El criterio C5 en tiempo de ejecución |
| `PromotionDiscountTest` | Aritmética del descuento al céntimo |
| `MarketingPingControllerTest` | Rutas públicas |

## 10. Lo que este servicio no hace

- **No envía mensajes.** Decide a quién y con qué plantilla, y publica el evento. Quien tiene
  credenciales del proveedor —y la responsabilidad de la ventana de 24 h de WhatsApp— es
  ms-notificaciones. Que Meta esté caído no impide lanzar una campaña aquí, ni al revés.
- **No factura.** Valida y registra el canje del cupón; el importe lo cobra ms-facturacion.
- **No lee el expediente.** No puede.

---

Parte de **MedicalSys — Descomposición en Microservicios**.
El razonamiento completo está en `Microservicios/documentacion/MedicalSys_Microservicios.pdf`, § 5.12.
