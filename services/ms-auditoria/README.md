# ms-auditoria

Bitácora append-only de MedicalSys. Responde una sola pregunta, pero la responde de forma
demostrable: **quién hizo qué, sobre qué recurso y cuándo**, dentro de una organización.

- Puerto interno: `8091`
- Base de datos: `audit_db` (MySQL 8), usuario `audit_user`
- Prefijo público: `/api/v1/audit/**`
- Prefijo interno: `/internal/v1/audit-events` (no publicado por el gateway)

## Por qué existe

Un sistema clínico multi-tenant guarda datos que la ley y la ética obligan a proteger. El resto de
microservicios ya limita *quién puede* acceder; ninguno registra *quién accedió realmente*. Sin ese
registro no se puede investigar una filtración, ni demostrar que una consulta no ocurrió, ni
detectar que una recepcionista abrió cincuenta expedientes en una tarde.

## Decisiones de diseño

### 1. Append-only, en el código y en el motor

No existe ninguna ruta de modificación ni de borrado. La entidad marca todas sus columnas como
`updatable = false` y el usuario `audit_user` **no recibe `DELETE` ni `DROP`** sobre `audit_db`
(ver `infra/mysql/init/01-create-databases.sh`). Una corrección se asienta como un evento nuevo.

### 2. Encadenamiento por hash

Cada evento guarda `previous_hash` y `record_hash`, donde
`record_hash = SHA-256(previous_hash ‖ contenido canónico del evento)`. La cadena es por
organización y lleva un `sequence_number` consecutivo.

Esto cubre lo que el punto anterior no puede: alguien con acceso directo a MySQL sí podría editar
una fila. Si lo hace, el evento deja de reproducir su hash y todos los posteriores dejan de
encadenar. `GET /api/v1/audit/integrity` recalcula el tramo y señala la primera posición rota.

La punta de cada cadena vive en `audit_chain_head` y se toma con `SELECT ... FOR UPDATE`, para que
dos asientos simultáneos de la misma organización no se encadenen al mismo predecesor. El bloqueo
es por organización: dos clínicas distintas siguen escribiendo en paralelo.

### 3. La bitácora no guarda contenido clínico

Se guarda el *hecho*: acción, tipo de recurso, identificador público, actor, resultado. Nunca el
texto del diagnóstico, la dirección del paciente ni ciphertext. Duplicar el dato sensible en una
segunda base duplicaría el riesgo que ms-criptografía y ms-historial se esfuerzan en contener.

`metadata` acepta pares clave/valor cortos para contexto (`{"documentType": "CI"}`), con un tope de
20 claves y 2000 caracteres serializados.

### 4. Solo se escribe desde dentro

`/internal/v1/audit-events` no está publicado en el gateway. Si el navegador pudiera asentar
eventos, cualquiera podría fabricar la prueba de que no accedió a un expediente.

### 5. Solo administración puede leer

`/api/v1/audit/**` exige rol `ADMINISTRATOR` o `PLATFORM_ADMIN`. Un médico o una recepcionista no
necesitan ver quién más abrió un expediente; darles esa vista convertiría la bitácora en una fuente
nueva de exposición en vez de un control.

### 6. La exportación se audita a sí misma

`GET /api/v1/audit/events/export` asienta un evento `AUDIT_LOG_EXPORTED` de categoría `SECURITY`
**antes** de escribir la primera fila. Llevarse la bitácora es el movimiento más sensible que
permite esta pantalla; si la descarga se corta a la mitad, el intento igual quedó registrado.

## Rutas

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/audit/ping` | — | Verificación de despliegue |
| `GET` | `/api/v1/audit/events` | `ADMINISTRATOR`, `PLATFORM_ADMIN` | Búsqueda paginada con filtros |
| `GET` | `/api/v1/audit/events/{eventId}` | `ADMINISTRATOR`, `PLATFORM_ADMIN` | Detalle de un evento |
| `GET` | `/api/v1/audit/events/export` | `ADMINISTRATOR`, `PLATFORM_ADMIN` | Exportación CSV (máx. 10 000 filas) |
| `GET` | `/api/v1/audit/summary` | `ADMINISTRATOR`, `PLATFORM_ADMIN` | Cortes agregados de una ventana |
| `GET` | `/api/v1/audit/integrity` | `ADMINISTRATOR`, `PLATFORM_ADMIN` | Recálculo de la cadena de hashes |
| `POST` | `/internal/v1/audit-events` | servicio interno | Asiento de un evento |
| `POST` | `/internal/v1/audit-events/batch` | servicio interno | Asiento de hasta 200 eventos |

Filtros de `GET /api/v1/audit/events`: `from`, `to`, `category`, `outcome`, `severity`,
`serviceName`, `action`, `resourceType`, `actorUserId`, `patientId`, `resourceId`, `correlationId`,
`q`, más `page`, `size` y `sort`. Todos opcionales y combinados con Y lógico. El tenant **no** es un
filtro: sale del contexto propagado por el gateway.

## Cómo reporta un microservicio

Ejemplo mínimo del cuerpo de `POST /internal/v1/audit-events`:

```json
{
  "eventId": "6f1c2f6e-2f0a-4a5f-9a3f-6c9a1b2c3d4e",
  "organizationId": "00000000-0000-4000-8000-0000000000a1",
  "occurredAt": "2026-09-07T14:05:12Z",
  "serviceName": "ms-pacientes",
  "action": "PATIENT_REGISTERED",
  "category": "PATIENT_DATA",
  "severity": "NOTICE",
  "outcome": "SUCCESS",
  "actorUserId": "20000000-0000-4000-8000-0000000000f3",
  "actorRoles": ["RECEPTIONIST"],
  "resourceType": "PATIENT",
  "resourcePublicId": "10000000-0000-4000-8000-000000000001",
  "patientPublicId": "10000000-0000-4000-8000-000000000001",
  "requestMethod": "POST",
  "requestPath": "/api/v1/patients",
  "responseStatus": 201,
  "metadata": { "documentType": "CI" }
}
```

`eventId` lo elige el productor: permite reintentar sin duplicar. Un reintento del mismo hecho
devuelve el acuse original; el mismo `eventId` describiendo un hecho distinto se rechaza con
`409 AUDIT_EVENT_ALREADY_RECORDED`.

`organizationId` solo se usa cuando la llamada no trae `X-Organization-Id`. Ese caso existe de
verdad: un intento de login fallido ocurre antes de que haya sesión, y perderlo dejaría ciego
justamente el ataque que interesa ver.

La implementación de referencia del lado productor está en
`services/ms-pacientes/src/main/java/com/medicalsys/pacientes/common/audit/`. Publica **después del
commit** —un evento que anuncia un alta revertida contradice la base— y fuera del hilo de la
solicitud, con cola acotada y política `CallerRuns`.

## Errores

Formato común del MVP (`docs/contracts/error-v1.md`). Códigos propios:

| Código | HTTP | Situación |
|---|---:|---|
| `AUDIT_EVENT_NOT_FOUND` | 404 | El evento no existe en esta organización |
| `AUDIT_EVENT_ALREADY_RECORDED` | 409 | `eventId` reutilizado para otro hecho |
| `INVALID_AUDIT_RANGE` | 422 | Rango o ventana incoherente |
| `INVALID_AUDIT_PAYLOAD` | 422 | Fecha adelantada o metadatos excesivos |

## Datos de demostración

`AUDIT_DEMO_SEED=true` siembra una jornada de ejemplo para la organización demo de `tenancy_db`
(`00000000-0000-4000-8000-0000000000a1`), incluyendo un login fallido, una denegación por rol y una
exportación. La siembra pasa por el mismo servicio que usan los microservicios reales, así que la
cadena sembrada verifica igual que la de producción; reimplementar el hash en SQL habría creado una
segunda versión de la regla, condenada a desincronizarse.

## Limitaciones conocidas

- El envío desde los productores es best-effort: si ms-auditoria está caído, esos eventos se
  pierden. La versión estricta necesita una bandeja de salida persistente en cada productor.
- Solo ms-pacientes reporta hoy. El resto de microservicios debe adoptar el mismo cliente; el
  contrato ya está congelado para que puedan hacerlo sin coordinar cambios.
- No hay política de retención ni archivado. La tabla crece sin límite.
