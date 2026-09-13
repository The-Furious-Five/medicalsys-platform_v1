# Contratos REST v1 — Catálogos y Criptografía

Esta matriz congela las rutas que consume el flujo clínico integrado. Las rutas internas se usan
solo entre servicios dentro de la red Compose; las rutas públicas pasan por el Gateway.

| Método | Ruta | Productor | Consumidor | Rol/canal | Request | Respuesta mínima |
|---|---|---|---|---|---|---|
| GET | `/api/v1/catalogs/cie10?q=` | ms-catalogos | frontend | Personal autenticado | `q` opcional | lista de `code`, `description` |
| GET | `/internal/v1/cie10/{code}` | ms-catalogos | ms-historial | servicio interno | código CIE-10 | `code`, `description` |
| GET | `/internal/v1/tipos-estudio/{code}` | ms-catalogos | ms-historial | servicio interno | código de estudio | `code`, `name`, `category` |
| GET | `/internal/v1/medicamentos/{code}` | ms-catalogos | ms-historial | servicio interno | código de medicamento | `code`, `genericName` |
| POST | `/internal/v1/cifrado` | ms-criptografia | ms-historial | servicio interno | `organizationId`, `plaintext` | `keyPublicId`, `ciphertext` |
| POST | `/internal/v1/descifrado` | ms-criptografia | ms-historial | servicio interno | `keyPublicId`, `ciphertext` | `plaintext` |

## Compatibilidad temporal

Los aliases `/internal/v1/icd10/{code}`, `/internal/v1/study-types/{code}` y
`/internal/v1/medications/{code}` siguen respondiendo durante este Sprint para no romper
consumidores previos. Todo código nuevo debe usar las rutas canónicas de la tabla.

## Reglas de integración

- Los UUID se transmiten como cadenas RFC 4122; no se comparte ningún ID técnico ni entidad JPA.
- `X-Correlation-Id` se acepta en la petición, se normaliza si es vacío o supera 128 caracteres,
  y se devuelve siempre en la respuesta.
- Catálogos devuelve `404` si el código es inexistente o inactivo.
- Criptografía devuelve `422 DECRYPT_FAILED` si el ciphertext no es válido o fue cifrado para otra
  clave/tenant. Nunca devuelve material de clave.
- `keyPublicId` identifica la clave que produjo el ciphertext y es obligatorio al descifrar. El
  servicio deriva la DEK con el tenant, propósito y versión de esa clave; por ello un ciphertext
  de un tenant no puede abrirse con la clave de otro tenant.
