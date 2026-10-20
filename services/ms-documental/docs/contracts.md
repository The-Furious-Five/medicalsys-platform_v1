# Contrato de ms-documental

Este documento define el contrato HTTP de `ms-documental` para el MVP del Sprint 2. El servicio es
la unica autoridad sobre los archivos del sistema: MySQL conserva metadatos y relaciones, mientras
MinIO conserva los bytes en un bucket privado.

> Solo se permiten archivos y datos claramente ficticios en el entorno local. Nunca se cargan
> estudios, documentos de identidad ni informacion medica reales durante la demostracion.

## 1. Alcance del MVP

Incluido:

- cargar documentos desde recepcion o consulta medica;
- clasificarlos usando los tipos controlados del diccionario de datos;
- asociarlos a un paciente y, opcionalmente, a una orden, resultado, encuentro u otra entidad;
- consultar metadatos y listar documentos por paciente;
- descargar el contenido despues de verificar su SHA-256;
- entregar a `ms-historial` una proyeccion interna con identificador, hash y estado;
- aislamiento por `organizationId` y propagacion de `X-Correlation-Id`.

Fuera del incremento:

- antivirus real: los archivos quedan en `NOT_APPLICABLE` durante la demo;
- carga directa navegador-S3 mediante URL prefirmada;
- OCR, miniaturas, procesamiento DICOM y generacion automatica de PDF;
- firma electronica y ciclo de vida de consentimientos. Sus tablas ya estan migradas, pero requieren
  un contrato propio con Identidad, Criptografia, Pacientes, Profesionales e Historial;
- acceso del rol `PATIENT`, porque aun no existe un vinculo verificable entre la cuenta autenticada y
  el `patientId`. Habilitarlo antes permitiria consultar documentos de terceros.

## 2. Convenciones y seguridad

Rutas y campos JSON estan en ingles. Los identificadores expuestos son UUID y las fechas usan
ISO-8601. Ninguna respuesta publica expone `bucket` ni `key_object`.

Todos los endpoints publicos, excepto `ping`, reciben:

```http
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
X-User-Id: 00000000-0000-4000-8000-0000000000f1
X-Roles: RECEPTIONIST
X-Correlation-Id: 2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90
```

Roles permitidos: `PHYSICIAN`, `RECEPTIONIST`, `ADMINISTRATOR` y `PLATFORM_ADMIN`.

Una consulta con otra organizacion responde `404`, aunque el UUID exista. Asi no revela la
existencia de datos pertenecientes a otro tenant.

## 3. Tipos controlados

`X_RAY`, `CT_SCAN`, `MRI`, `ULTRASOUND`, `DICOM_STUDY`, `LAB_RESULT`, `MEDICAL_REPORT`,
`INFORMED_CONSENT`, `PRESCRIPTION_PDF`, `INVOICE_PDF`, `INVOICE_XML`, `CLINICAL_PHOTO`,
`IDENTITY_DOCUMENT`, `SIGNATURE_IMAGE`, `OTHER`.

Vinculos permitidos: `MEDICAL_RECORD`, `ENCOUNTER`, `STUDY_ORDER`, `STUDY_RESULT`, `CONSENT`,
`PRESCRIPTION`, `INVOICE`, `APPOINTMENT`, `PROFESSIONAL`.

El limite local predeterminado es 100 MiB. Los PDF se aceptan para resultados, reportes,
consentimientos, recetas y facturas; los tipos de imagen admiten JPEG, PNG, TIFF y DICOM; las
facturas XML admiten `application/xml` y `text/xml`.

## 4. Errores

```json
{
  "timestamp": "2026-08-25T18:03:51Z",
  "status": 422,
  "code": "FILE_TYPE_NOT_ALLOWED",
  "message": "El tipo de contenido no esta permitido para la clasificacion LAB_RESULT",
  "correlationId": "2da15fa9-9f9c-4b62-b642-ef9bfb9fcf90",
  "path": "/api/v1/documents"
}
```

Codigos estables: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`,
`METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `DOCUMENT_NOT_FOUND`, `RESOURCE_NOT_FOUND`,
`DOCUMENT_LINK_ALREADY_EXISTS`, `DATA_INTEGRITY_CONFLICT`, `EMPTY_FILE`, `FILE_TOO_LARGE`,
`FILE_TYPE_NOT_ALLOWED`, `INVALID_RETENTION_DATE`, `DOCUMENT_NOT_AVAILABLE`,
`INTEGRITY_MISMATCH`, `STORAGE_UNAVAILABLE`, `INTERNAL_ERROR`.

## 5. Ping

```http
GET /api/v1/documents/ping
```

```json
{ "service": "ms-documental", "status": "ok" }
```

## 6. Cargar un documento

```http
POST /api/v1/documents
Content-Type: multipart/form-data
```

El formulario contiene dos partes:

- `file`: bytes y `Content-Type` real del archivo;
- `metadata`: JSON con `Content-Type: application/json`.

Ejemplo de `metadata`:

```json
{
  "type": "X_RAY",
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "dicom": false,
  "metadata": {
    "study": "chest-xray",
    "projection": "PA"
  },
  "retentionUntil": "2036-08-25",
  "link": {
    "entityType": "STUDY_RESULT",
    "entityId": "00000000-0000-4000-8000-0000000000e1",
    "description": "Resultado final del estudio"
  }
}
```

Ejemplo local:

```bash
curl -X POST http://localhost:18080/api/v1/documents \
  -H 'X-Organization-Id: 00000000-0000-4000-8000-0000000000a1' \
  -H 'X-User-Id: 00000000-0000-4000-8000-0000000000f1' \
  -H 'X-Roles: RECEPTIONIST' \
  -F 'file=@resultado-demo.pdf;type=application/pdf' \
  -F 'metadata={"type":"LAB_RESULT","patientId":"00000000-0000-4000-8000-0000000000b1"};type=application/json'
```

Respuesta `201 Created` y cabecera `Location: /api/v1/documents/{documentId}`:

```json
{
  "documentId": "00000000-0000-4000-8000-0000000000d1",
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "type": "LAB_RESULT",
  "originalFilename": "resultado-demo.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 1024,
  "sha256Hash": "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
  "dicom": false,
  "status": "AVAILABLE",
  "antivirusStatus": "NOT_APPLICABLE",
  "uploadedByUserId": "00000000-0000-4000-8000-0000000000f1",
  "uploadedAt": "2026-08-25T18:00:00Z",
  "links": []
}
```

La clave de MinIO se genera como:

```text
{organizationId}/{documentId}/v1/{safeFilename}
```

El cliente nunca decide esa ruta. Si MySQL rechaza la operacion despues de cargar el objeto, el
servicio intenta eliminarlo como compensacion.

## 7. Consultar y listar

```http
GET /api/v1/documents/{documentId}
GET /api/v1/documents?patientId={uuid}&type=X_RAY&status=AVAILABLE&page=0&size=20
```

El primer endpoint devuelve el cuerpo completo de la carga. El segundo devuelve:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`patientId`, `type` y `status` son opcionales. `size` debe estar entre 1 y 100.

## 8. Clasificar y vincular

```http
PATCH /api/v1/documents/{documentId}/classification
Content-Type: application/json
```

```json
{
  "type": "X_RAY",
  "dicom": false,
  "metadata": { "projection": "PA" }
}
```

No cambia los bytes ni su hash. El nuevo tipo debe ser compatible con el MIME almacenado.

```http
POST /api/v1/documents/{documentId}/links
Content-Type: application/json
```

```json
{
  "entityType": "STUDY_RESULT",
  "entityId": "00000000-0000-4000-8000-0000000000e1",
  "description": "Resultado final"
}
```

Crear dos veces el mismo vinculo devuelve `409 DOCUMENT_LINK_ALREADY_EXISTS`.

## 9. Descargar

```http
GET /api/v1/documents/{documentId}/content
```

Antes de responder, el servicio descarga el objeto a un archivo temporal, recalcula SHA-256 y
compara hash y tamano con MySQL. Si coinciden devuelve los bytes con `Content-Disposition:
attachment` y `X-Content-SHA256`. Si no coinciden devuelve `409 INTEGRITY_MISMATCH`, registra una
advertencia estructurada en el log (documentId, organizationId, objectKey y ambos hashes) y no
entrega el archivo.

```http
GET /api/v1/documents/{documentId}/integrity
```

Recalcula el SHA-256 del objeto almacenado sin descargarlo al cliente y lo compara contra el
registrado al subirlo. Siempre responde `200 OK`, incluso si no coinciden (el caller decide que
hacer; la descarga en el punto 9 es la que bloquea):

```json
{
  "documentId": "00000000-0000-4000-8000-0000000000d1",
  "expectedHash": "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
  "actualHash": "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
  "matches": true
}
```

## 10. Contrato interno para Historial

Esta ruta no se publica en el gateway:

```http
GET /internal/v1/documents/{documentId}
X-Organization-Id: 00000000-0000-4000-8000-0000000000a1
```

```json
{
  "documentId": "00000000-0000-4000-8000-0000000000d1",
  "patientId": "00000000-0000-4000-8000-0000000000b1",
  "type": "X_RAY",
  "mimeType": "image/jpeg",
  "sizeBytes": 2048,
  "sha256Hash": "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
  "status": "AVAILABLE"
}
```

Historial conserva solamente `documentId` y no accede a las tablas ni al bucket de Documental.
