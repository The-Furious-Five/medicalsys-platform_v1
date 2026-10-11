# ms-agenda

Microservicio de agenda de MedicalSys: recursos físicos, disponibilidad de profesionales, bloqueos,
citas y reservas de consultorio. Backend puro con Spring Boot, sin frontend.

| Dato | Valor |
|---|---|
| Puerto interno | `8087` |
| Base de datos | `scheduling_db` (PostgreSQL 16) |
| Usuario de base | `scheduling_user` |
| Variable de contraseña | `AGENDA_DB_PASSWORD` |
| Ruta del gateway | `/api/v1/scheduling/**` |
| Endpoint de prueba | `GET /api/v1/scheduling/ping` |

## Por qué PostgreSQL

El reto de este servicio no es guardar una cita: es impedir que dos transacciones concurrentes
reserven al mismo profesional o el mismo consultorio en franjas superpuestas. Leer antes de escribir
no alcanza, porque ambas transacciones pasarían esa lectura. La verdad la impone el motor:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointment ADD CONSTRAINT no_professional_overlap
    EXCLUDE USING gist (
        health_professional_public_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELLED','RESCHEDULED'));

ALTER TABLE resource_reservation ADD CONSTRAINT no_resource_overlap
    EXCLUDE USING gist (
        physical_resource_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
```

El rango es semiabierto `[)`: una cita de 09:00 a 09:30 y otra de 09:30 a 10:00 **no** se solapan.
`OverlapTranslator` convierte la violación de la restricción en un `409 Conflict` con un mensaje
que indica si el choque fue del profesional o del recurso.

## Modelo de datos

`V1__create_schema.sql` crea las seis tablas del diccionario: `physical_resource`,
`schedule_availability`, `schedule_block`, `appointment`, `resource_reservation` y
`appointment_status_history`.

Los `VARCHAR + CHECK` del diccionario se implementan tal cual. Todo lo que pertenece a otro
microservicio (`patient_public_id`, `health_professional_public_id`, `organization_public_id`,
`branch_public_id`, `service_offering_public_id`, los `*_user_public_id`) se guarda como `UUID`
**sin clave foránea**: son referencias de contrato, no dependencias físicas.

## Ciclo de vida de una cita

```
REQUESTED ──► CONFIRMED ──► WAITING ──► IN_PROGRESS ──► ATTENDED
    │             │            │             │
    └─────────────┴────────────┴─────────────┴──► CANCELLED
    └─────────────┴────────────┴────────────────► NO_SHOW
    └─────────────┴───────────────────────────► RESCHEDULED
```

`ATTENDED`, `CANCELLED`, `NO_SHOW` y `RESCHEDULED` son terminales. Una transición inválida responde
`409`. Cada cambio deja una fila inmutable en `appointment_status_history`.

Reprogramar no edita la cita original: la marca `RESCHEDULED`, libera sus reservas y crea una cita
nueva cuyo `source_appointment_id` apunta a la anterior.

## Endpoints

Todos bajo `/api/v1/scheduling`.

### Recursos físicos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/resources` | Crea un consultorio, sala o equipo |
| GET | `/resources` | Filtra por organización, sucursal, tipo y estado (paginado) |
| GET | `/resources/{publicId}` | Detalle |
| PATCH | `/resources/{publicId}` | Actualiza datos descriptivos |
| PATCH | `/resources/{publicId}/status` | Cambia el estado operativo |

### Disponibilidad y huecos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/availabilities` | Regla semanal de atención de un profesional |
| GET | `/availabilities?professionalOrganizationPublicId=…` | Lista sus reglas |
| DELETE | `/availabilities/{id}` | Desactiva la regla (no la borra) |
| GET | `/availability/slots?…&date=YYYY-MM-DD` | Huecos libres del día |

`/availability/slots` parte de las reglas vigentes, descuenta los bloqueos y las citas que ya ocupan
la agenda, y devuelve el cupo restante de cada hueco. Es solo una consulta: la reserva efectiva la
sigue decidiendo la base de datos.

### Bloqueos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/blocks` | Vacaciones, feriado, mantenimiento, etc. |
| GET | `/blocks?organizationPublicId=…` | Filtra por profesional, sucursal y rango |
| DELETE | `/blocks/{id}` | Elimina el bloqueo |

Un bloqueo debe apuntar al menos a un profesional, un recurso o una sucursal.

### Citas

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/appointments` | Crea la cita y, si se envía `physicalResourcePublicId`, reserva el consultorio en la misma transacción |
| GET | `/appointments?organizationPublicId=…` | Filtra por sucursal, profesional, paciente, estado y rango (paginado) |
| GET | `/appointments/{publicId}` | Detalle, incluye las transiciones válidas |
| GET | `/appointments/{publicId}/history` | Historial de estados |
| POST | `/appointments/{publicId}/confirm` | `CONFIRMED` |
| POST | `/appointments/{publicId}/check-in` | `WAITING`, registra `arrived_at` |
| POST | `/appointments/{publicId}/start` | `IN_PROGRESS` |
| POST | `/appointments/{publicId}/complete` | `ATTENDED` |
| POST | `/appointments/{publicId}/no-show` | `NO_SHOW`, libera el recurso |
| POST | `/appointments/{publicId}/cancel` | `CANCELLED`, libera el recurso |
| POST | `/appointments/{publicId}/reschedule` | Crea la cita sustituta |

### Reservas de recurso

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/reservations` | Reserva administrativa sin cita |
| GET | `/reservations?organizationPublicId=…` | Filtra por recurso y rango |
| POST | `/reservations/{id}/release` | `RELEASED` |
| POST | `/reservations/{id}/in-use` | `IN_USE` |
| DELETE | `/reservations/{id}` | `CANCELLED` |

La reserva de una cita incluye los `preparation_minutes` del recurso: el consultorio no queda libre
en el instante en que termina la atención.

### Interno (red de Compose, no expuesto por el gateway)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/internal/v1/appointments/{publicId}` | DTO mínimo para ms-historial |

## Autenticación y autorización

El gateway valida el JWT y propaga la identidad en `X-Organization-Id`, `X-User-Id` y
`X-Permissions`; el servicio nunca toma la organización del cuerpo ni de la consulta. Cada endpoint
declara el permiso que exige con `@RequiresPermission` (hoja endpoint → permiso de RBAC-01), y lo que
no lo declara se deniega por defecto (RBAC-04): sin identidad responde `401` y sin el permiso, `403`.
El ping es `@PublicEndpoint` y la ruta interna, `@InternalEndpoint`. **No debe publicarse su puerto
al host en el entorno compartido**: solo el gateway habla con él.

## Variables de entorno

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `SERVER_PORT` | no | `8087` | Puerto HTTP |
| `DB_URL` | sí | — | JDBC de `scheduling_db` (PostgreSQL) |
| `DB_USER` | sí | — | Usuario de base |
| `DB_PASSWORD` | sí | — | Desde `AGENDA_DB_PASSWORD` |
| `SCHEDULING_ZONE` | no | `America/La_Paz` | Zona que traduce los `TIME` locales a instantes |
| `SCHEDULING_VALIDATION_ENABLED` | no | `false` | Valida paciente y profesional por HTTP |
| `PATIENTS_BASE_URL` | no | `http://ms-pacientes:8085` | Contrato de ms-pacientes |
| `PROFESSIONALS_BASE_URL` | no | `http://ms-profesionales:8086` | Contrato de ms-profesionales |
| `SCHEDULING_MAX_DAYS_AHEAD` | no | `120` | Anticipación máxima al consultar huecos |

`SCHEDULING_VALIDATION_ENABLED` viene apagado porque ms-pacientes y ms-profesionales aún no publican
su contrato interno. Al encenderlo, una cita con un paciente inexistente responde `422` y una caída
del servicio dependiente responde `503`.

## Construir y ejecutar

```bash
# pruebas unitarias
./mvnw test

# imagen
docker build -t medicalsys/ms-agenda:local services/ms-agenda
```

Ejemplo de ejecución local contra infraestructura ya levantada:

```bash
docker run -d --name ms-agenda --network medicalsys-dev -p 8087:8087 \
  -e "DB_URL=jdbc:postgresql://postgres-agenda:5432/scheduling_db" \
  -e DB_USER=scheduling_user -e DB_PASSWORD="$AGENDA_DB_PASSWORD" \
  medicalsys/ms-agenda:local
```

## Verificación

```bash
# 53 comprobaciones funcionales sobre una instancia en ejecucion
bash services/ms-agenda/scripts/smoke.sh http://localhost:8087

# 10 solicitudes simultaneas a la misma franja: 1 debe crearse y 9 recibir 409
bash services/ms-agenda/scripts/concurrency.sh http://localhost:8087
```

## Integración central (bloques listos para pegar)

`compose.yaml` de la raíz y el ruteo del gateway los administra el equipo de integración, así que
este servicio no los modifica. Todo lo necesario está verificado y resumido aquí.

### 1. Bloque para `compose.yaml` de la raíz

Agenda **no** usa el MySQL compartido: necesita PostgreSQL 16 por `btree_gist` y
`EXCLUDE USING gist`, que es lo único que garantiza el antisolapamiento bajo concurrencia.

```yaml
  postgres:
    image: postgres:16-alpine

    environment:
      POSTGRES_DB: scheduling_db
      POSTGRES_USER: scheduling_user
      POSTGRES_PASSWORD: ${AGENDA_DB_PASSWORD}

    volumes:
      - postgres_data:/var/lib/postgresql/data

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U scheduling_user -d scheduling_db"]
      interval: 5s
      timeout: 5s
      retries: 20

  ms-agenda:
    build:
      context: ./services/ms-agenda
      dockerfile: Dockerfile

    image: medicalsys/ms-agenda:local

    environment:
      SERVER_PORT: "8087"

      DB_URL: jdbc:postgresql://postgres:5432/scheduling_db
      DB_USER: scheduling_user
      DB_PASSWORD: ${AGENDA_DB_PASSWORD}

      SCHEDULING_ZONE: America/La_Paz

      # ms-pacientes y ms-profesionales todavía no publican su contrato interno.
      SCHEDULING_VALIDATION_ENABLED: "false"

    depends_on:
      postgres:
        condition: service_healthy
```

Y añadir el volumen al final del archivo:

```yaml
volumes:
  mysql_data:
  postgres_data:
```

No lleva `ports:`. En el entorno compartido el único puerto publicado es el `8080` del gateway.

`POSTGRES_USER` crea a `scheduling_user` como dueño de la base, que es lo que permite a la migración
ejecutar `CREATE EXTENSION btree_gist`. No hace falta ningún script en `infra/`.

### 2. Variable a añadir al servicio `ms-gateway` en `compose.yaml`

```yaml
      SCHEDULING_SERVICE_URL: http://ms-agenda:8087
```

Y en `depends_on` del gateway:

```yaml
      ms-agenda:
        condition: service_started
```

### 3. Ruta para `services/ms-gateway/src/main/resources/application.yaml`

```yaml
            - id: scheduling-service
              uri: ${SCHEDULING_SERVICE_URL:http://localhost:8087}
              predicates:
                - Path=/api/v1/scheduling/**
```

### 4. Variable de `.env`

`AGENDA_DB_PASSWORD` ya existe en `.env.example`; no hace falta añadir nada.

### 5. Verificación realizada

Levantando el stack de la raíz con los bloques anteriores:

```text
GET http://localhost:8080/api/v1/scheduling/ping        200  {"service":"ms-agenda","status":"ok"}
GET http://localhost:8080/api/v1/scheduling/resources   200  respuesta paginada
```

Flyway aplicó `V1` sobre `scheduling_db`, Hibernate validó el esquema sin diferencias y las dos
restricciones de exclusión (`no_professional_overlap`, `no_resource_overlap`) quedaron creadas.

```text
Servicio: ms-agenda
Puerto interno: 8087
Base de datos: scheduling_db (PostgreSQL 16, contenedor propio)
Usuario de base: scheduling_user
Variable de contraseña: AGENDA_DB_PASSWORD
Ruta del gateway: /api/v1/scheduling/**
Endpoint de prueba: GET /api/v1/scheduling/ping
Resultado de docker build: exitoso
```
