# Contrato de ms-facturación

Congelado en la reunión de contratos. Un cambio incompatible crea `/api/v2`; un campo nuevo opcional
no rompe a nadie y no exige versión nueva.

> **Aviso permanente.** Toda factura emitida por este servicio proviene de un adaptador fiscal
> **simulado**: sale con estado `DEMO_ACCEPTED`, con `demo: true` y con la leyenda
> `NO VALIDA TRIBUTARIAMENTE`. **No tiene validez tributaria** y no se envía a ningún sistema real.

## Convenciones

| Elemento | Acuerdo | Ejemplo |
| --- | --- | --- |
| Rutas | Español, como el resto de la plataforma | `/api/v1/facturas` |
| Campos JSON | Inglés camelCase | `patientId`, `serviceOfferingId` |
| Identificadores | UUID como texto de 36 caracteres | `7d1f8c2e-...` |
| Fechas y horas | ISO-8601 con zona; se almacena UTC | `2026-08-24T14:00:00Z` |
| Dinero | Numero JSON con la escala exacta del `DECIMAL`; `BigDecimal`, nunca `float` | `150.00` |
| Autenticación | El gateway propaga `X-Organization-Id`, `X-User-Id`, `X-Roles` | — |
| Trazabilidad | `X-Correlation-Id` de entrada y de salida | — |
| Campos nulos | No se serializan | — |

## Códigos HTTP

| Código | Cuándo |
| --- | --- |
| 200 | Consulta correcta |
| 201 | Factura emitida |
| 400 | Request inválido o mal formado |
| 401 | Identidad ausente o no propagada por el gateway |
| 403 | Identidad válida sin el rol necesario |
| 404 | Factura o punto de venta inexistente, o de otro tenant |
| 409 | Correlativo ya utilizado |
| 422 | Regla de negocio: paciente o prestación no facturables, configuración fiscal incompleta |
| 503 | ms-pacientes o ms-tenencia no disponibles |

## Formato de error

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 422,
  "code": "SERVICE_OFFERING_NOT_BILLABLE",
  "message": "La prestacion '00000000-...-0509' no existe en el catalogo vigente",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/facturas",
  "details": [{ "field": "items[0].serviceOfferingId", "message": "no debe ser nulo" }]
}
```

`details` solo aparece en errores de validación. El campo `code` es el estable: los clientes y las
pruebas reaccionan a él, nunca al `message`.

### Códigos estables

`VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `METHOD_NOT_ALLOWED`,
`UNSUPPORTED_MEDIA_TYPE`, `INVOICE_NOT_FOUND`, `POINT_OF_SALE_NOT_FOUND`, `RESOURCE_NOT_FOUND`,
`INVOICE_NUMBERING_CONFLICT`, `DATA_INTEGRITY_CONFLICT`, `BILLING_CONFIGURATION_NOT_READY`,
`POINT_OF_SALE_NOT_ACTIVE`, `PATIENT_NOT_BILLABLE`, `SERVICE_OFFERING_NOT_BILLABLE`,
`INVOICE_TOTALS_NOT_CONSISTENT`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`.

---

## 1. Ping

```http
GET /api/v1/billing/ping
```

```json
{ "service": "ms-facturacion", "status": "ok" }
```

Ruta pública: no exige identidad. Es la que el gateway usa para comprobar el enrutamiento.

---

## 2. Emitir una factura

```http
POST /api/v1/facturas
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-User-Id: 00000000-0000-4000-8000-0000000000f1
X-Roles: RECEPTIONIST
Content-Type: application/json
```

```json
{
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "branchId": "00000000-0000-4000-8000-0000000000a2",
  "paymentMethodCode": 1,
  "items": [
    {
      "serviceOfferingId": "00000000-0000-4000-8000-000000000501",
      "quantity": 1,
      "appointmentId": "00000000-0000-4000-8000-000000000a11"
    },
    {
      "serviceOfferingId": "00000000-0000-4000-8000-000000000502",
      "quantity": 2,
      "discountAmount": 50.00
    }
  ]
}
```

Rol: `BILLING_CLERK`, `RECEPTIONIST`, `ADMINISTRATOR` o `PLATFORM_ADMIN`.

| Campo | Obligatorio | Nota |
| --- | --- | --- |
| `patientId` | **sí** | Se resuelve contra ms-pacientes; un paciente inexistente da 422 |
| `branchId` | no | Por defecto la sucursal del punto de venta |
| `pointOfSale` | no | `{ "taxBranchCode": 0, "pointOfSaleCode": 1 }`; por defecto la primera caja activa |
| `paymentMethodCode` | no | Catálogo del SIN; 1 = efectivo |
| `items[].serviceOfferingId` | **sí** | Se resuelve contra ms-tenencia |
| `items[].quantity` | no | Por defecto 1; debe ser mayor que cero |
| `items[].discountAmount` | no | Por defecto 0; no puede superar el importe de la línea |
| `items[].appointmentId` | no | Trazabilidad clínica ↔ fiscal |
| `items[].clinicalEncounterId` | no | Trazabilidad clínica ↔ fiscal |

**Lo que el cuerpo no acepta, por diseño:**

- `organizationId`: lo impone el gateway desde claims firmados.
- Precios, descripciones y totales: los publica ms-tenencia y los calcula el servicio. Quien emite
  elige **qué** se cobra y **cuánta** cantidad, nunca **a qué precio**.
- Datos del cliente: los publica ms-pacientes.

Respuesta `201 Created`, `Location: /api/v1/facturas/{id}`:

```json
{
  "id": "5f0f7d3e-6f52-4a0b-9a3d-2f2c1b7a4c11",
  "invoiceNumber": 1,
  "taxDocumentType": "PURCHASE_SALE_INVOICE",
  "emissionType": "ONLINE",
  "status": "DEMO_ACCEPTED",
  "demo": true,
  "caption": "NO VALIDA TRIBUTARIAMENTE - DOCUMENTO DE DEMOSTRACION SIN VALOR FISCAL",
  "cuf": "DEMO-3F2A...",
  "taxReceiptCode": "DEMO-9C1B4E2A5D6F7081",
  "branchId": "00000000-0000-4000-8000-0000000000a2",
  "pointOfSale": { "taxBranchCode": 0, "pointOfSaleCode": 1 },
  "customer": {
    "patientId": "00000000-0000-4000-8000-0000000000b1",
    "identityDocumentTypeCode": 1,
    "documentNumber": "12345678",
    "legalName": "PACIENTE DEMO 00000000"
  },
  "totalAmount": 600.00,
  "vatTaxableAmount": 600.00,
  "totalDiscountAmount": 50.00,
  "giftCardAmount": 0.00,
  "currencyTotalAmount": 600.00,
  "exchangeRate": 1.00000,
  "paymentMethodCode": 1,
  "issuedAt": "2026-08-24T14:00:00Z",
  "validatedAt": "2026-08-24T14:00:00Z",
  "items": [
    {
      "itemNumber": 1,
      "serviceOfferingId": "00000000-0000-4000-8000-000000000501",
      "description": "Consulta medica general (demostracion)",
      "quantity": 1.0000,
      "unitPrice": 150.00,
      "discountAmount": 0.00,
      "subtotal": 150.00,
      "taxProductCode": "99100",
      "unitOfMeasure": 58,
      "appointmentId": "00000000-0000-4000-8000-000000000a11"
    },
    {
      "itemNumber": 2,
      "serviceOfferingId": "00000000-0000-4000-8000-000000000502",
      "description": "Estudio de rayos X (demostracion)",
      "quantity": 2.0000,
      "unitPrice": 250.00,
      "discountAmount": 50.00,
      "subtotal": 450.00,
      "taxProductCode": "99200",
      "unitOfMeasure": 58
    }
  ]
}
```

**Garantías del contrato:**

- `totalAmount` = Σ `items[].subtotal`, siempre.
- `items[].subtotal` = `quantity` × `unitPrice` − `discountAmount`.
- `invoiceNumber` es único dentro de `pointOfSale` y `taxDocumentType`.
- `qrUrl` **no** aparece: sin emisión real no existe una dirección de verificación que ofrecer.

> Emitir **no es idempotente**: cada llamada consume un correlativo. Un `POST` fallido no debe
> reintentarse automáticamente sin una clave de idempotencia (backlog posterior).

---

## 3. Consultar una factura

```http
GET /api/v1/facturas/{invoiceId}
```

Rol: `BILLING_CLERK`, `RECEPTIONIST`, `ADMINISTRATOR` o `PLATFORM_ADMIN`.
Respuesta `200 OK` con el mismo cuerpo del punto 2.

Una factura de otra organización responde **404**, no 403: revelar su existencia rompería el
aislamiento multi-tenant.

---

## 4. Listar facturas

```http
GET /api/v1/facturas?patientId={uuid}&page=0&size=20
```

```json
{
  "content": [
    {
      "id": "5f0f7d3e-6f52-4a0b-9a3d-2f2c1b7a4c11",
      "invoiceNumber": 1,
      "patientId": "00000000-0000-4000-8000-0000000000b1",
      "status": "DEMO_ACCEPTED",
      "demo": true,
      "totalAmount": 600.00,
      "issuedAt": "2026-08-24T14:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 5. Ruta interna

```http
GET /internal/v1/facturas/{invoiceId}
```

Devuelve la misma proyección mínima del listado. Solo red interna; no expone detalle ni datos del
cliente. Ningún consumidor recibe más de lo necesario.

---

## 6. Contratos que ms-facturación **consume**

Ambos están detrás de un puerto con dos implementaciones. Por defecto se usa el modo `LOCAL`, que
resuelve dentro del proceso con datos ficticios: así el flujo funciona hoy sin bloquear a nadie.
Al activar `REMOTE` se consumen estas rutas y no cambia nada más.

### 6.1 ms-pacientes (Nicole, NI-03) — `PATIENTS_MODE=REMOTE`

```http
GET /internal/v1/pacientes/{patientId}/fiscal
X-Organization-Id: {uuid}
```

```json
{
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "documentType": "CI",
  "documentNumber": "12345678",
  "documentComplement": null,
  "legalName": "APELLIDO APELLIDO NOMBRES",
  "email": null
}
```

- `404` si el paciente no existe o no es visible para el tenant → se traduce a `422 PATIENT_NOT_BILLABLE`.
- Es el **DTO fiscal mínimo** de la matriz del diccionario: identificador, documento y nombre.
  ms-facturación no recibe ni necesita teléfono, dirección ni ningún dato clínico.

### 6.2 ms-tenencia (FR-03) — `TENANCY_MODE=REMOTE`

```http
GET /internal/v1/prestaciones/{serviceOfferingId}
X-Organization-Id: {uuid}
```

```json
{
  "id": "00000000-0000-4000-8000-000000000501",
  "code": "CONSULTATION",
  "name": "Consulta medica general",
  "basePriceBob": 150.00,
  "taxProductCode": "99100",
  "taxUnitCode": 58,
  "active": true
}
```

- `404` si la prestación no existe → se traduce a `422 SERVICE_OFFERING_NOT_BILLABLE`.
- `active: false` también se traduce a `422`.
- Es el **snapshot** de código, descripción y precio que la factura congela.

> Si el propietario del servicio elige otra ruta, el cambio es de una línea en
> `RemotePatientsClient` / `RemoteServiceCatalogClient` y no toca el dominio.
