# Contratos de ms-marketing

Rutas en español, campos JSON en inglés `camelCase`, fechas ISO-8601 con zona, dinero en `DECIMAL`.
Es la misma convención que usa ms-facturacion.

El cuerpo **nunca** acepta `organizationId`: lo impone el gateway desde claims firmados y lo publica
como `X-Organization-Id`. Un recurso de otra organización responde `404`, no `403`: revelar su
existencia rompería el aislamiento multi-tenant.

## 1. Formato de error

El mismo del MVP (guía Sprint 2, § 4.4):

```json
{
  "timestamp": "2026-09-06T14:22:31.118Z",
  "status": 422,
  "code": "CLINICAL_DATA_NOT_ALLOWED",
  "message": "El campo 'diagnosticoCie10' es un dato clinico y ms-marketing no lo acepta...",
  "correlationId": "smoke-marketing-1757168551",
  "path": "/api/v1/segmentos"
}
```

Reaccione al `code`, no al `message`: el código es estable, la frase puede cambiar o traducirse.

### Códigos propios de este servicio

| `code` | HTTP | Cuándo |
| --- | --- | --- |
| `CLINICAL_DATA_NOT_ALLOWED` | 422 | Un criterio o un evento traen un campo clínico. **Criterio C3** |
| `SEGMENT_CRITERIA_INVALID` | 422 | Eje desconocido, criterio vacío o rango incoherente |
| `MARKETING_NOT_INCLUDED_IN_PLAN` | 403 | El plan del tenant tiene `includes_marketing = 0`. **Criterio C5** |
| `CAMPAIGN_STATE_NOT_ALLOWED` | 422 | Transición imposible del ciclo de vida |
| `CAMPAIGN_WITHOUT_AUDIENCE` | 422 | Lanzar sin audiencia materializada, o campaña sin segmento |
| `PROMOTION_NOT_APPLICABLE` | 422 | Cupón vencido, agotado, de otra prestación o ya usado por el paciente |
| `LOYALTY_BALANCE_INSUFFICIENT` | 422 | Canje mayor que el saldo |
| `LOYALTY_PROGRAM_NOT_ACTIVE` | 422 | Programa desactivado |
| `EVENT_NOT_SUPPORTED` | 422 | Tipo de evento que marketing no consume, o campo obligatorio ausente |
| `SEGMENT_NOT_FOUND`, `CAMPAIGN_NOT_FOUND`, `PROMOTION_NOT_FOUND`, `ADVERTISEMENT_NOT_FOUND`, `LOYALTY_PROGRAM_NOT_FOUND`, `PATIENT_NOT_KNOWN` | 404 | No existe, o pertenece a otro tenant |
| `SEGMENT_ALREADY_EXISTS`, `PROMOTION_CODE_ALREADY_EXISTS`, `LOYALTY_PROGRAM_ALREADY_EXISTS` | 409 | Nombre o código repetido en la organización |
| `DEPENDENCY_UNAVAILABLE` | 503 | ms-pacientes o ms-tenencia no responden |

## 2. Rutas públicas (a través del gateway)

### 2.1 Comprobación

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `GET /api/v1/marketing/ping` | público | Verificar el servicio y el enrutamiento |
| `GET /api/v1/marketing/criterios-permitidos` | público | **Publica la frontera de privacidad**: los 13 ejes de segmentación admitidos |

La segunda no es adorno. Quien integre puede comprobar desde la API, sin leer el código, que
marketing no admite un criterio clínico.

### 2.2 Segmentos — `/api/v1/segmentos`

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `POST /` | gestor, admin | Crear un segmento; devuelve ya su tamaño evaluado |
| `GET /` | gestor, admin, recepción | Listar (`?active=`) |
| `GET /{id}` | gestor, admin, recepción | Ver el segmento y su criterio |
| `PATCH /{id}` | gestor, admin | Cambiar descripción o criterio |
| `POST /vista-previa` | gestor, admin | **Evaluar un criterio sin guardarlo** |
| `POST /{id}/evaluacion` | gestor, admin | Reevaluar y devolver muestra |
| `POST /{id}/desactivacion` | gestor, admin | Desactivar sin borrar |
| `DELETE /{id}` | gestor, admin | Borrar; `409` si alguna campaña lo referencia |

**Ejes de segmentación admitidos** — y solo estos. Cualquier otro nombre se rechaza:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `minAge` / `maxAge` | entero 0–130 | Edad en años cumplidos |
| `sex` | lista de `M`,`F`,`INTERSEX`,`UNDECLARED` | Sexos aceptados |
| `cities` | lista de texto | Ciudades de residencia |
| `lastVisitBefore` / `lastVisitAfter` | fecha | Última visita; eje de reactivación |
| `minAppointments` | entero | Citas atendidas mínimas |
| `maxNoShows` | entero | Inasistencias toleradas |
| `minTotalBilledBob` | decimal | Gasto acumulado mínimo |
| `lastServiceOfferingId` | UUID | Última prestación facturada |
| `birthdayMonth` | 1–12 | Mes de cumpleaños |
| `controlDueBefore` | fecha | Control sugerido anterior a esa fecha (**la fecha, no el motivo**) |
| `excludeCampaignId` | UUID | Excluye a los destinatarios de otra campaña |

Todos se combinan con AND. La lista está en `SegmentCriteriaField`; es cerrada por diseño: una lista
negra dejaría pasar cualquier eje nuevo que nadie pensó en prohibir.

```bash
# Crear un segmento de reactivación
curl -X POST "$BASE/api/v1/segmentos" \
  -H "X-Organization-Id: $ORG" -H "X-User-Id: $USER" -H "X-Roles: ADMINISTRATOR" \
  -H 'Content-Type: application/json' -d '{
    "name": "Adultos que no vuelven hace un año",
    "criteria": { "minAge": 18, "lastVisitBefore": "2025-09-06", "maxNoShows": 2 }
  }'
```

```jsonc
// 422 — el intento queda registrado en el log del servicio
{ "code": "CLINICAL_DATA_NOT_ALLOWED",
  "message": "El campo 'diagnosticoCie10' es un dato clinico y ms-marketing no lo acepta. Cruzar patologia con publicidad seria una filtracion de dato sensible de salud: la segmentacion solo usa datos administrativos y demograficos" }
```

### 2.3 Campañas — `/api/v1/campanas`

El ciclo son cuatro pasos separados, no un «enviar campaña»:

```
crear (DRAFT) → materializar audiencia → lanzar (RUNNING) → medir
```

Entre materializar y lanzar cabe la revisión humana de a cuánta gente va a llegar el mensaje. Es la
única protección real contra un criterio mal escrito, y por eso son dos llamadas.

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `POST /` | gestor, admin | Crear en `DRAFT` |
| `GET /` | gestor, admin, recepción | Listar (`?status=`) |
| `GET /{id}` | gestor, admin, recepción | Ver con contadores |
| `POST /{id}/audiencia` | gestor, admin | **Congelar** la audiencia |
| `POST /{id}/programacion` | gestor, admin | `DRAFT` → `SCHEDULED` |
| `POST /{id}/lanzamiento` | gestor, admin | → `RUNNING` y publica `marketing.campana` |
| `POST /{id}/pausa` | gestor, admin | → `PAUSED` |
| `POST /{id}/finalizacion` | gestor, admin | → `FINISHED` |
| `POST /{id}/cancelacion` | gestor, admin | → `CANCELLED` |
| `GET /{id}/destinatarios` | gestor, admin | Audiencia con su resultado (`?status=`) |
| `GET /{id}/rendimiento` | gestor, admin, recepción | Tasas calculadas por la vista |

`POST /{id}/audiencia` responde:

```json
{ "campaignId": "…", "matched": 12, "reachable": 11, "excludedOptOut": 1,
  "truncated": false, "materializedAt": "2026-09-06T14:22:31Z" }
```

Los excluidos por opt-out se guardan como filas con su motivo, no se omiten. Consultables en
`GET /{id}/destinatarios?status=EXCLUDED_OPT_OUT`.

### 2.4 Promociones — `/api/v1/promociones`

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `POST /` | gestor, admin | Crear cupón |
| `GET /` `GET /{id}` | gestor, admin, recepción, caja | Consultar |
| `POST /validar` | caja, recepción, gestor, admin | **Validar sin consumir** |
| `POST /canjes` | caja, recepción, gestor, admin | Canjear (idempotente por factura) |
| `POST /{id}/desactivacion` | gestor, admin | Desactivar |

`validar` y `canjes` están separados porque la caja necesita **mostrar** el descuento antes de
cobrar, y mostrar no puede gastar el cupón.

### 2.5 Anuncios — `/api/v1/anuncios`

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `POST /` | gestor, admin | Crear en `DRAFT` |
| `GET /` | gestor, admin, recepción | Listar los visibles |
| `GET /vigentes?ubicacion=` | **paciente**, recepción, gestor, admin | Lo que el portal debe mostrar ahora |
| `GET /{id}` | paciente, recepción, gestor, admin | Ver uno |
| `POST /{id}/publicacion` `…/pausa` `…/finalizacion` | gestor, admin | Ciclo de vida |
| `POST /{id}/impresiones` `…/clics` | paciente, recepción, gestor, admin | Métricas; las cuenta el servidor |

`ubicacion`: `PATIENT_PORTAL` (por defecto), `APP_HOME`, `WAITING_ROOM`, `EMAIL_FOOTER`.

### 2.6 Fidelización — `/api/v1/fidelidad`

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `POST /programas` | gestor, admin | Crear programa |
| `GET /programas` | gestor, admin, recepción | Listar |
| `POST /programas/{id}/desactivacion` | gestor, admin | Desactivar |
| `GET /saldo?patientId=[&programId=]` | paciente, recepción, caja, gestor, admin | Saldo (vista `v_loyalty_balance`) |
| `GET /movimientos?patientId=` | paciente, recepción, caja, gestor, admin | Libro mayor paginado |
| `POST /movimientos` | recepción, caja, gestor, admin | Canje, ajuste manual o bonificación |

**No hay ruta para acumular.** La acumulación llega por evento desde ms-facturacion con su clave de
idempotencia; permitirla a mano abriría la puerta a otorgar dos veces los puntos de una factura.
`POST /movimientos` con `type: "ACCRUAL"` responde `400 VALIDATION_ERROR` explicando por qué.

`points` se pide siempre positivo y el `type` decide el signo: pedir un número negativo para canjear
invita a equivocarse justo en la operación que resta saldo.

### 2.7 Actividad del paciente — `/api/v1/pacientes`

| Método y ruta | Rol | Para qué |
| --- | --- | --- |
| `GET /actividad` | gestor, admin, recepción | Listar la actividad comercial del tenant |
| `GET /{patientId}/actividad` | gestor, admin, recepción | Proyección comercial de un paciente |
| `PUT /{patientId}/opt-out` | **paciente**, recepción, gestor, admin | Registrar o revocar la exclusión |

El opt-out admite el rol `PATIENT` y vive en este servicio: quien manda la publicidad es este, y el
paciente debe poder ejercerlo aunque ms-pacientes esté caído.

## 3. Rutas internas (red `medicalsys-interna`, sin pasar por el gateway)

### 3.1 `POST /internal/v1/promociones/validar` — **contrato congelado con ms-facturacion**

Petición:

```json
{ "couponCode": "VERANO25",
  "patientId": "00000000-0000-4000-8000-000000000701",
  "serviceOfferingId": null,
  "baseAmountBob": "250.00" }
```

Respuesta `200`:

```json
{ "valid": true, "promotionId": "…", "couponCode": "VERANO25",
  "discountType": "PERCENTAGE", "discountAmountBob": "62.50", "netAmountBob": "187.50" }
```

Cupón que no aplica — **también `200`**:

```json
{ "valid": false,
  "reason": "El cupon esta fuera de su ventana de vigencia (2026-08-01 a 2026-08-31)",
  "couponCode": "VERANO25", "discountAmountBob": "0.00", "netAmountBob": "250.00" }
```

> **Por qué es síncrona.** El documento de descomposición (§ «Dónde sí se usa REST síncrono») lo
> justifica así: «No se puede emitir una factura con un descuento cuya validez aún se desconoce». La
> respuesta forma parte de la decisión de cobro, de modo que no puede llegar por evento.
>
> **Por qué 200 y no un 4xx.** Para ms-facturacion, «este cupón no sirve» es una respuesta esperada
> del flujo de cobro, no un fallo de la petición. Devolverle un error le obligaría a interpretar
> códigos para decidir si puede seguir cobrando.
>
> **Fallback explícito.** El documento pide que esta llamada vaya protegida por circuit breaker con
> fallback explícito; eso corresponde al **llamante**. El fallback que este contrato define es: ante
> `503` o timeout, **emitir sin descuento**. No contradice la frase anterior — precisamente evita
> aplicar un descuento cuya validez se desconoce — y respeta que marketing sea el servicio de menor
> criticidad del sistema: su caída no debe impedir facturar.
>
> El descuento nunca supera la base, así que `netAmountBob` jamás es negativo.

### 3.2 `POST /internal/v1/promociones` → canje

Se hace por la ruta pública `POST /api/v1/promociones/canjes` (misma red, mismo contrato).
`invoiceId` es obligatorio y es la clave de idempotencia: reintentar devuelve el canje existente con
`alreadyRedeemed: true` en vez de consumir otro uso.

### 3.3 `POST /internal/v1/eventos` — entrada de eventos

Sustituye al broker que el MVP todavía no tiene. Sobre común:

```json
{ "eventId": "uuid",           // clave del inbox de idempotencia
  "eventType": "facturacion.factura.emitida",
  "occurredAt": "2026-09-06T14:22:31Z",
  "organizationId": "uuid",
  "payload": { … } }
```

Respuesta `200`:

```json
{ "eventId": "…", "eventType": "…", "duplicate": false,
  "detail": "Gasto de 200.00 BOB proyectado y se acumularon 200 puntos" }
```

Un evento ya aplicado responde `duplicate: true` y **no** `409`: un reintento tras un timeout es el
comportamiento correcto de un productor.

El encaminamiento es **por prefijo**, así que un productor puede afinar el tipo
(`agenda.cita` → `agenda.cita.atendida`) sin romper a este consumidor.

| Prefijo | Productor | Campos de `payload` que se leen | Qué proyecta |
| --- | --- | --- | --- |
| `tenencia.organizacion` | ms-tenencia | `commercialName`, `includesMarketing`, `version` | `ref_organization` |
| `tenencia.servicio` / `tenencia.prestacion` | ms-tenencia | `serviceOfferingId`\*, `name`, `basePriceBob`, `active`, `version` | `ref_service_offering` |
| `pacientes.paciente` | ms-pacientes | `patientId`\*, `fullName`, `birthDate`, `sex`, `city`, `phoneE164`, `status`, `marketingOptOut`, `version` | `ref_patient` + abre `patient_activity` |
| `agenda.cita` | ms-agenda | `patientId`\*, `appointmentId`, `status`, `scheduledOn`, `campaignId` | Visitas / inasistencias, y conversión si viene `campaignId` |
| `facturacion.factura` | ms-facturacion | `patientId`\*, `invoiceId`\*, `totalAmountBob`, `issuedOn`, `serviceOfferingId` | Gasto acumulado + acumulación de puntos |
| `historial.encuentro` | ms-historial | `patientId`\*, **`suggestedNextVisitOn`** | **Solo la fecha de control** |
| `notificaciones.mensaje` | ms-notificaciones | `campaignId`\*, `patientId`\*, `status`, `notificationId`, `reason` | Contadores de la campaña y estado del destinatario |

\* obligatorio. `status` de cita: `ATTENDED`/`COMPLETED` suman visita, `NO_SHOW` suma inasistencia,
cualquier otro no altera la actividad. `status` de mensaje: `SENT`/`DELIVERED`, `READ`, `FAILED`.

> **`historial.encuentro` merece una lectura atenta.** Es un evento del expediente clínico y de él se
> lee **un solo campo**: `suggestedNextVisitOn`. Si el productor incluye además el motivo, el evento
> entero se rechaza con `422 CLINICAL_DATA_NOT_ALLOWED` — no se proyecta a medias. La inspección es
> recursiva: un campo clínico anidado dentro de un objeto o de una lista también se detecta.

### 3.4 `GET /internal/v1/campanas/{id}/destinatarios` — audiencia para ms-notificaciones

Contrapartida del *claim check*: el evento `marketing.campana` no lleva los destinatarios, se piden
por aquí. Devuelve solo los `SELECTED`, paginados.

### 3.5 Outbox

| Método y ruta | Para qué |
| --- | --- |
| `GET /internal/v1/eventos/salientes?estado=PENDING&limite=100` | Drenar el outbox desde un relay externo |
| `GET /internal/v1/campanas/{id}/eventos` | Eventos de una campaña (demostración y depuración) |
| `POST /internal/v1/eventos/salientes/{eventId}/publicado` | Acuse del relay externo |

Con `MARKETING_OUTBOX_MODE=RETAIN` los eventos se acumulan para el relay externo. Con `LOG` (por
defecto) el relay interno los registra y los marca publicados.

## 4. Evento publicado: `marketing.campana`

Es el **único** evento que ms-marketing emite. Se escribe en `outbox_event` dentro de la misma
transacción que el cambio de estado de la campaña.

```json
{
  "campaignId": "…",
  "organizationId": "…",
  "action": "LANZADA",            // LANZADA | PAUSADA | FINALIZADA | CANCELADA
  "name": "Chequeo preventivo",
  "type": "PREVENTIVE",
  "channel": "WHATSAPP",
  "status": "RUNNING",
  "messageTemplateId": null,
  "startsAt": "2026-09-06T14:22:31Z",
  "endsAt": null,
  "totalRecipients": 11,
  "recipientsUri": "/internal/v1/campanas/…/destinatarios",
  "couponCode": "VERANO25",
  "promotionId": "…"
}
```

Cabeceras: `correlationId`, `producer: "ms-marketing"`, `schemaVersion: 1`.
Clave de partición: el `organizationId`, que garantiza el orden de las campañas de una misma clínica.

`action` va dentro del cuerpo y no en el tipo del evento para que el consumidor se suscriba a un
único tópico, como declara la sección 5.12 («Publica `marketing.campana`»).

## 5. Lo que este servicio **no** consume

No hay cliente, credencial ni configuración hacia **ms-historial**. No es un pendiente: es la
frontera de privacidad del criterio C3, y `MarketingIsolationTest` falla si alguien escribe la
primera referencia.
