# Contratos HTTP de ms-notificaciones

Ruta base: `/api/v1/notifications`

Salvo `/ping`, las solicitudes deben recibir del gateway el encabezado:

```http
X-Organization-Id: 10000000-0000-0000-0000-000000000001
```

Todas las respuestas incluyen `X-Correlation-Id`.

## Comprobar disponibilidad

```http
GET /api/v1/notifications/ping
```

## Encolar una notificación

```http
POST /api/v1/notifications
Content-Type: application/json
X-Organization-Id: 10000000-0000-0000-0000-000000000001
```

```json
{
  "originEventId": "30000000-0000-0000-0000-000000000001",
  "patientId": "20000000-0000-0000-0000-000000000001",
  "channel": "WHATSAPP",
  "category": "UTILITY",
  "eventType": "APPOINTMENT_CREATED",
  "entityType": "APPOINTMENT",
  "entityId": "40000000-0000-0000-0000-000000000001",
  "destination": "+59170000000",
  "variables": {
    "patientName": "Paciente Demo"
  },
  "renderedBody": "Su cita fue registrada correctamente"
}
```

El teléfono debe utilizar formato E.164. `originEventId` hace idempotente la operación: repetir
el mismo evento devuelve `409 DUPLICATE_ORIGIN_EVENT` en vez de crear dos mensajes.

## Consultar notificaciones

```http
GET /api/v1/notifications
GET /api/v1/notifications?status=FAILED
GET /api/v1/notifications/{notificationId}
```

La organización se obtiene siempre del encabezado; no se acepta como parámetro del cliente.

## Despachar con el proveedor configurado

```http
POST /api/v1/notifications/{notificationId}/dispatch
```

En desarrollo el proveedor es un simulador. Solo acepta elementos `QUEUED` o `SCHEDULED` cuya
fecha programada ya haya llegado.

## Reintentar un envío fallido

```http
POST /api/v1/notifications/{notificationId}/retry
```

Solo permite reintentar el estado `FAILED` y respeta `NOTIFICATION_MAX_ATTEMPTS`.

## Formato de error

```json
{
  "timestamp": "2026-09-07T21:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "DUPLICATE_ORIGIN_EVENT",
  "message": "Ya existe una notificacion para el evento de origen",
  "path": "/api/v1/notifications",
  "correlationId": "50000000-0000-0000-0000-000000000001",
  "details": []
}
```
