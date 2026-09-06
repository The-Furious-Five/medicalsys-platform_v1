# Diccionario de datos — `marketing_db`

Fuente normativa: `MedicalSys_Microservicios.pdf`, sección 5.12, y el esquema de referencia
`Microservicios/servicios/12-ms-marketing/sql/V1__esquema.sql`.

> El diccionario oficial (`MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf`) **excluye
> explícitamente** a marketing: «Notificaciones, marketing y auditoría quedan fuera de este documento
> y del MVP del sprint» (§ introducción). Por eso el esquema de este servicio se deriva de la
> sección 5.12 del documento de descomposición y del esquema de referencia, y se traduce a las
> convenciones del capítulo 13 del diccionario, que sí aplican a toda la plataforma.

## 1. Equivalencia con el esquema de referencia

El esquema de referencia nombra las tablas en español y comparte claves `BIGINT` entre servicios.
Este repositorio usa **identificadores físicos en inglés y `snake_case`**, y referencia lo ajeno por
**UUID público en `BINARY(16)` sin FK** (capítulo 13 del diccionario; es lo que ya hacen
`billing_db`, `patients_db` y las demás). La equivalencia es uno a uno:

| Esquema de referencia (5.12) | `marketing_db` (implementado) | Nota |
| --- | --- | --- |
| `segmento_audiencia` | `audience_segment` | |
| `campana` | `campaign` | |
| `promocion` | `promotion` | |
| `campana_destinatario` | `campaign_recipient` | |
| `promocion_uso` | `promotion_redemption` | |
| `anuncio` | `advertisement` | |
| `programa_fidelidad` | `loyalty_program` | |
| `movimiento_puntos` | `loyalty_points_movement` | |
| `actividad_paciente` (nueva) | `patient_activity` | Tabla nueva del sprint |
| `ref_organizacion` | `ref_organization` | Réplica |
| `ref_paciente` | `ref_patient` | Réplica, solo demografía |
| `ref_servicio` | `ref_service_offering` | Réplica |
| `outbox_evento` | `outbox_event` | Común a todos los servicios |
| `evento_procesado` | `processed_event` | Común a todos los servicios |
| `v_saldo_puntos` | `v_loyalty_balance` | Vista |
| `v_rendimiento_campana` | `v_campaign_performance` | Vista |

Son **9 tablas propias + 3 réplicas + 2 de infraestructura**, exactamente las que declara la sección
5.12. `SchemaConformanceContainerTest` falla si aparece una tabla de más o de menos.

## 2. Decisiones registradas

Cada una se decidió al implementar y queda anotada aquí porque no se deduce del documento.

### 2.1 Las réplicas se identifican por UUID, no por `BIGINT` compartido

El esquema de referencia da a `ref_paciente` un `id BIGINT` que es el mismo que en `pacientes`. En
este repositorio **ningún servicio comparte claves numéricas**: `patients_db.patient` tiene un `id`
técnico local y un `public_id BINARY(16)`, y es el `public_id` el que viaja. Replicar el `BIGINT`
ajeno crearía un acoplamiento a la clave interna de otra base. Por eso `ref_patient`,
`ref_organization` y `ref_service_offering` tienen el `public_id` como clave primaria.

### 2.2 `ref_patient` no lleva organización; `patient_activity` sí

En este repositorio el paciente es global (`patients_db.patient` no tiene `organization_public_id`),
mientras que la relación **comercial** sí es por tenant. Es la misma clave que declara el esquema de
referencia para `actividad_paciente`: `PRIMARY KEY (paciente_id, organizacion_id)`.

La consecuencia práctica importa: **la audiencia se acota siempre por `patient_activity`**. Un
paciente al que esta clínica nunca atendió no es alcanzable desde sus campañas, aunque exista en la
réplica porque otra clínica de la plataforma sí lo atendió. Es aislamiento entre tenants, no una
limitación.

### 2.3 `ref_organization.includes_marketing` — columna añadida

No está en el esquema de referencia. Proyecta `plan_subscription.includes_marketing`, que la sección
5.12 usa como justificación del criterio C5. Sin ella, la opcionalidad sería un argumento de diseño
sin ninguna consecuencia en tiempo de ejecución: el servicio atendería igual a un tenant que no
contrató el módulo. Con ella, ese tenant recibe `403 MARKETING_NOT_INCLUDED_IN_PLAN`.

### 2.4 `ref_patient.marketing_opt_out` y `opted_out_at` — columnas añadidas

Tampoco están en el esquema de referencia, que da por hecho que el opt-in vive en
`notificaciones.preferencia_notificacion`. Se replican aquí por dos razones:

1. **Momento de aplicación.** La exclusión debe evaluarse *al materializar la audiencia*, antes de
   que exista mensaje alguno. Si viviera solo en notificaciones, marketing generaría destinatarios
   que otro servicio descartaría después, y el registro de a quién se excluyó —que es lo que se
   pregunta cuando alguien reclama— no existiría en ninguna parte.
2. **Disponibilidad.** El paciente debe poder ejercer el opt-out contra el servicio que le manda la
   publicidad, aunque ms-pacientes o ms-notificaciones estén caídos.

### 2.5 Contadores `INT UNSIGNED` mapeados a `long` en Java

`INT UNSIGNED` llega a 4 294 967 295, más que `Integer.MAX_VALUE`; `TINYINT UNSIGNED` llega a 255,
más que `Byte.MAX_VALUE`. El campo Java usa el tipo inmediatamente mayor y declara el tipo físico
con `columnDefinition`, de modo que `ddl-auto: validate` sigue comprobando la correspondencia.

### 2.6 `outbox_event.payload` es JSON y el evento no lleva la audiencia

El evento `marketing.campana` transporta el hecho y el tamaño; los destinatarios se piden por
`GET /internal/v1/campanas/{id}/destinatarios` (patrón *claim check*). Meter 5 000 teléfonos en un
mensaje sería un cuerpo enorme, difícil de reintentar y, sobre todo, publicaría datos de contacto en
un canal que otros servicios pueden leer.

### 2.7 El MVP de la plataforma no tiene broker

El esquema de referencia asume Kafka (`application.yml` del spec lo configura). Este repositorio no
tiene broker en ningún servicio. Las tablas `outbox_event` y `processed_event` existen igual y el
transporte es una ruta interna con la forma de un mensaje. El día que aparezca el broker, cambia
`OutboxRelay` y ningún caso de uso se entera.

## 3. Tablas

### 3.1 Réplicas de referencia

Copias locales de solo lectura, alimentadas por eventos y por hidratación bajo demanda. **Ningún caso
de uso de marketing las escribe como dueño.**

#### `ref_organization`

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `public_id` | `BINARY(16)` | no | PK. UUID de `tenancy_db.organization` |
| `commercial_name` | `VARCHAR(180)` | no | Nombre comercial |
| `includes_marketing` | `TINYINT(1)` | no | Proyección de `plan_subscription.includes_marketing` |
| `source_version` | `BIGINT UNSIGNED` | no | Versión aplicada; un evento anterior se ignora |
| `synced_at` | `TIMESTAMP` | no | Última sincronización |

#### `ref_patient` — **solo demografía**

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `public_id` | `BINARY(16)` | no | PK. UUID de `patients_db.patient` |
| `full_name` | `VARCHAR(290)` | no | Nombre completo |
| `birth_date` | `DATE` | sí | Base del eje de edad |
| `sex` | `ENUM` | no | `M`, `F`, `INTERSEX`, `UNDECLARED` |
| `city` | `VARCHAR(80)` | sí | Ciudad de residencia |
| `phone_e164` | `VARCHAR(20)` | sí | Teléfono de entrega |
| `status` | `ENUM` | no | `ACTIVE`, `INACTIVE`, `DECEASED` |
| `marketing_opt_out` | `TINYINT(1)` | no | Exclusión comercial |
| `opted_out_at` | `TIMESTAMP` | sí | Instante de la exclusión |
| `source_version` | `BIGINT UNSIGNED` | no | Versión aplicada |
| `synced_at` | `TIMESTAMP` | no | Última sincronización |

> **Lo que no tiene** es más revelador que lo que tiene: ni diagnóstico, ni alergia, ni antecedente,
> ni dirección cifrada, ni clave de cifrado, ni referencia al expediente. Los cinco campos
> demográficos son exactamente los ejes que la sección 5.12 autoriza.

#### `ref_service_offering`

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `public_id` | `BINARY(16)` | no | PK. UUID de `tenancy_db.service_offering` |
| `organization_public_id` | `BINARY(16)` | no | FK a `ref_organization` |
| `name` | `VARCHAR(160)` | no | Descripción visible |
| `base_price_bob` | `DECIMAL(12,2)` | no | Precio unitario |
| `active` | `TINYINT(1)` | no | Vigencia |
| `source_version` / `synced_at` | | | Igual que las demás réplicas |

### 3.2 `patient_activity` — tabla nueva del sprint

Proyección comercial por paciente y organización. Sustituye a los `JOIN` contra `cita` y `factura`
del monolito.

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `patient_public_id` | `BINARY(16)` | no | PK (1/2). FK a `ref_patient`, `ON DELETE CASCADE` |
| `organization_public_id` | `BINARY(16)` | no | PK (2/2). FK a `ref_organization` |
| `first_visit_on` | `DATE` | sí | Primera visita registrada |
| `last_visit_on` | `DATE` | sí | Última visita; eje de reactivación |
| `total_appointments` | `INT UNSIGNED` | no | Citas atendidas |
| `total_no_shows` | `INT UNSIGNED` | no | Inasistencias |
| `total_billed_bob` | `DECIMAL(14,2)` | no | Gasto acumulado |
| `last_service_offering_public_id` | `BINARY(16)` | sí | FK a `ref_service_offering` |
| `suggested_next_visit_on` | `DATE` | sí | **Fecha** de control del evento `EncuentroFirmado` |
| `updated_at` | `TIMESTAMP` | no | Última proyección |

> `suggested_next_visit_on` es el detalle que cita la sección 5.12: el evento transporta la fecha de
> control, **no el motivo clínico**. Marketing sabe cuándo volver a llamar, no de qué padece el
> paciente. La firma de `PatientActivity#setSuggestedNextVisit(LocalDate)` no tiene ningún parámetro
> donde el motivo pudiera colarse.

### 3.3 Segmentación y campañas

#### `audience_segment`

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | no | PK técnica |
| `public_id` | `BINARY(16)` | no | Identificador del contrato |
| `organization_public_id` | `BINARY(16)` | no | FK a `ref_organization` |
| `name` | `VARCHAR(120)` | no | Único por organización |
| `description` | `VARCHAR(300)` | sí | |
| `criteria` | `JSON` | no | DSL declarativo; solo ejes de la lista blanca |
| `uses_clinical_data` | `TINYINT(1)` | no | **`CHECK (= 0)`**. Segunda barrera declarativa |
| `estimated_size` | `INT UNSIGNED` | sí | Tamaño de la última evaluación |
| `last_evaluated_at` | `TIMESTAMP` | sí | |
| `active` | `TINYINT(1)` | no | |
| `created_by_user_public_id` | `BINARY(16)` | sí | Trazabilidad |
| `created_at` / `updated_at` | `TIMESTAMP` | no | |

**Restricciones**: `uq_audience_segment_public_id`,
`uq_audience_segment_organization_public_id_name`, `ck_audience_segment_privacy`.

#### `promotion`

| Columna | Tipo | Nulo | Descripción |
| --- | --- | --- | --- |
| `id` / `public_id` | | no | PK técnica / del contrato |
| `organization_public_id` | `BINARY(16)` | no | FK a `ref_organization` |
| `coupon_code` | `VARCHAR(30)` | no | Normalizado a mayúsculas; único por organización |
| `name` / `description` | | | |
| `discount_type` | `ENUM` | no | `PERCENTAGE`, `FIXED_AMOUNT`, `FREE_SERVICE` |
| `value` | `DECIMAL(12,2)` | no | `CHECK (>= 0)` |
| `service_offering_public_id` | `BINARY(16)` | sí | `NULL` = cualquier prestación |
| `max_redemptions` | `INT UNSIGNED` | sí | `NULL` = ilimitado |
| `redemptions_per_patient` | `TINYINT UNSIGNED` | no | Por defecto 1 |
| `current_redemptions` | `INT UNSIGNED` | no | `CHECK (<= max_redemptions)` |
| `valid_from` / `valid_until` | `DATE` | no | `CHECK (until >= from)`; inclusivas |
| `active` | `TINYINT(1)` | no | |

#### `campaign`

Estados: `DRAFT` → `SCHEDULED` → `RUNNING` → `PAUSED` → `FINISHED` / `CANCELLED`.
Tipos: `PROMOTION`, `PREVENTIVE`, `CONTROL_REMINDER`, `BIRTHDAY`, `REACTIVATION`, `SURVEY`.
Canales: `WHATSAPP`, `SMS`, `EMAIL`, `PORTAL`.

Contadores (`total_recipients`, `total_sent`, `total_read`, `total_converted`) son proyecciones
alimentadas por eventos de vuelta: los dos primeros y `total_read` desde ms-notificaciones,
`total_converted` desde ms-agenda.

#### `campaign_recipient`

Audiencia **congelada** con su resultado individual. Estados: `SELECTED`, `EXCLUDED_OPT_OUT`, `SENT`,
`READ`, `CONVERTED`, `FAILED`. Un excluido por opt-out entra igualmente en la tabla, con su motivo:
una fila ausente no prueba que la exclusión se evaluara.

#### `promotion_redemption`

`uq_promotion_redemption_promotion_id_invoice_public_id` es la **clave de idempotencia** del canje:
un reintento de ms-facturacion tras un timeout no consume otro uso del cupón.

#### `advertisement`

`organization_public_id NULL` = anuncio institucional de la plataforma, visible en todos los tenants
y editable por ninguno. `priority` con `CHECK (BETWEEN 1 AND 10)`; 1 es la mayor.

### 3.4 Fidelización

#### `loyalty_program`

`points_per_bob` (cuántos puntos da cada boliviano) y `bob_per_point` (cuánto vale un punto) están
separados a propósito: el margen del programa es la diferencia, y tenerla explícita permite cambiarla
sin tocar código.

#### `loyalty_points_movement`

**Append-only.** El saldo se deriva sumando los asientos; no hay columna de saldo que alguien pueda
corregir a mano. `uq_loyalty_points_movement_source_event_public_id` convierte un transporte
*at-least-once* en un efecto *effectively-once*: si `FacturaEmitida` llega dos veces, el segundo
intento choca contra el motor.

### 3.5 Infraestructura

`outbox_event` (transactional outbox) y `processed_event` (inbox de idempotencia), con la misma forma
que en los demás servicios MySQL de la plataforma.

## 4. Vistas

| Vista | Qué calcula |
| --- | --- |
| `v_loyalty_balance` | Saldo por programa y paciente, con el instante del último asiento |
| `v_campaign_performance` | Tasas de envío, lectura y conversión, y excluidos por opt-out |

No son decorativas: `MarketingViewRepository` las consulta para servir `GET /api/v1/fidelidad/saldo`
y `GET /api/v1/campanas/{id}/rendimiento`. La fórmula de las tasas vive en un único lugar.

## 5. Verificación automatizada

`SchemaConformanceContainerTest` (49 casos, MySQL 8.4 real vía Testcontainers) comprueba:

- Las 14 tablas exactas, ni una más.
- Que **no existe** ninguna tabla clínica (13 nombres probados).
- Que **ninguna columna** nombra un dato clínico (`cie10`, `diagnos`, `allerg`, `treatment`,
  `prescription`, `encrypted`, …).
- Que el motor **rechaza de verdad** un `INSERT` con `uses_clinical_data = 1` (no se lee la
  definición del `CHECK`: se intenta violarlo).
- Que no hay ninguna FK hacia otra base.
- Los tipos físicos de 23 columnas y las 6 restricciones de unicidad que dan idempotencia.
- Que las dos vistas existen y son consultables.
