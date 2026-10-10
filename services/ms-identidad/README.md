# ms-identidad

Microservicio de identidad de MedicalSys: cuentas de acceso, roles, permisos y emisión del JWT
que el resto de la plataforma consume. Backend puro con Spring Boot, sin frontend.

| Dato | Valor |
|---|---|
| Puerto interno | `8081` |
| Base de datos | `identity_db` (MySQL 8) |
| Usuario de base | `identity_user` |
| Variable de contraseña | `IDENTIDAD_DB_PASSWORD` |
| Sesiones | Redis 7 (no hay tabla de sesiones) |
| Ruta del gateway | `/api/v1/identity/**` |
| Endpoint de prueba | `GET /api/v1/identity/ping` |

## Modelo de datos

`V1__create_schema.sql` crea las cinco tablas del diccionario: `user_account`, `role`, `permission`,
`role_permission` y `user_role`. `V2__seed_roles_and_permissions.sql` siembra el catálogo de roles y
permisos del MVP, y `V3__granular_permission_catalog.sql` lo reemplaza por el catálogo granular de
permisos por funcionalidad (`modulo.recurso.accion`) con su asignación inicial a roles.
`V4__role_management.sql` abre la lista de roles (el código solo exige formato), agrega la baja lógica
(`role.is_active`) y la tabla `role_incompatibility` de pares de roles incompatibles.

Dos decisiones que conviene conocer al leer la migración:

- Los `ENUM` del diccionario se implementan como `VARCHAR` + `CHECK`. Es la misma restricción de
  dominio, y evita que la validación de esquema de Hibernate choque contra el tipo `ENUM` de MySQL.
- `organization_public_id`, `branch_public_id` y `encryption_key_public_id` son referencias externas:
  se guardan como `BINARY(16)` **sin clave foránea**, tal como exige el modelo distribuido.

Las contraseñas se almacenan con Argon2id (`hash_algorithm = 'argon2id'`); nunca en texto plano.

## Endpoints

### Públicos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/identity/ping` | Prueba de vida del servicio |
| GET | `/actuator/health` | Salud, incluye MySQL y Redis |
| POST | `/api/v1/identity/auth/login` | Devuelve el JWT y el perfil del usuario |

### Autenticados (cabecera `Authorization: Bearer <token>`)

| Método | Ruta | Permiso requerido |
|---|---|---|
| GET | `/api/v1/identity/auth/me` | solo estar autenticado |
| POST | `/api/v1/identity/auth/logout` | solo estar autenticado |
| POST | `/api/v1/identity/auth/change-password` | solo estar autenticado |
| GET | `/api/v1/identity/roles`, `/roles/{code}`, `/permissions`, `/role-incompatibilities` | `identity.role.read` |
| POST | `/api/v1/identity/roles` | `identity.role.manage` |
| PATCH | `/api/v1/identity/roles/{code}` | `identity.role.manage` |
| DELETE | `/api/v1/identity/roles/{code}` (desactiva) | `identity.role.manage` |
| PUT | `/api/v1/identity/roles/{code}/permissions` | `identity.role.manage` |
| POST | `/api/v1/identity/role-incompatibilities` | `identity.role.manage` |
| DELETE | `/api/v1/identity/role-incompatibilities/{roleA}/{roleB}` | `identity.role.manage` |
| GET | `/api/v1/identity/users`, `/users/{publicId}` | `identity.user.read` |
| POST | `/api/v1/identity/users` | `identity.user.create` |
| PATCH | `/api/v1/identity/users/{publicId}` | `identity.user.update` |
| PATCH | `/api/v1/identity/users/{publicId}/status` | `identity.user.change_status` |
| POST | `/api/v1/identity/users/{publicId}/roles` | `identity.role.assign` |
| DELETE | `/api/v1/identity/users/{publicId}/roles/{role}` | `identity.role.assign` |

Cualquier otra operación bajo `/users/**`, `/roles/**` o `/role-incompatibilities/**` responde 403 hasta
que se le asigne su permiso.

Asignar, revocar y editar roles pasa además por las reglas de segregación de funciones de
`RoleGovernance`. Nadie modifica sus propios roles ni los permisos de un rol que tiene, nadie otorga
permisos de gestión que no tiene, y un administrador de organización solo gestiona cuentas de su
organización. Los pares incompatibles se respetan. El detalle está en
[`docs/seguridad/rbac-02-gestion-de-roles.md`](../../docs/seguridad/rbac-02-gestion-de-roles.md).
`GET /permissions` devuelve el catálogo granular completo de la plataforma (RBAC-01, migración V3);
la hoja endpoint → permiso y la asignación inicial por rol están en
[`docs/seguridad/rbac-01-catalogo-permisos.md`](../../docs/seguridad/rbac-01-catalogo-permisos.md).

### Interno (red de Compose, no expuesto por el gateway)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/internal/v1/users/{publicId}` | DTO mínimo: identidad, organización, estado y roles |

## Contrato del JWT

Firmado con HS256 usando `JWT_SECRET`. Claims acordados con el gateway:

```json
{
  "sub": "uuid publico del usuario",
  "organizationId": "uuid de la organizacion",
  "roles": ["ADMINISTRATOR"],
  "permissions": ["identity.user.read", "..."],
  "iss": "medicalsys-ms-identidad",
  "iat": 1787447596,
  "exp": 1787451196,
  "jti": "uuid de la sesion"
}
```

El `jti` se guarda en Redis mientras la sesión vive. Cerrar sesión, cambiar la contraseña o
desactivar la cuenta borra esa clave, así que el token deja de ser aceptado **antes** de expirar.
El `organizationId` que envía el cliente nunca se usa como fuente de verdad: sale del token.

## Reglas de acceso

- Cinco intentos fallidos bloquean la cuenta 15 minutos (`status = LOCKED`, respuesta `423`).
  El contador se confirma en su propia transacción, porque la del login se revierte al responder 401.
- Una cuenta `PENDING_ACTIVATION` o `INACTIVE` no puede entrar (`403`).
- El mensaje de error no distingue entre correo inexistente y contraseña incorrecta.

## Variables de entorno

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `SERVER_PORT` | no | `8081` | Puerto HTTP |
| `DB_URL` | sí | — | JDBC de `identity_db` |
| `DB_USER` | sí | — | Usuario de base |
| `DB_PASSWORD` | sí | — | Desde `IDENTIDAD_DB_PASSWORD` |
| `REDIS_HOST` / `REDIS_PORT` | no | `localhost` / `6379` | Almacén de sesiones |
| `JWT_SECRET` | sí | — | Mínimo 32 bytes; el servicio no arranca si es menor |
| `JWT_ISSUER` | no | `medicalsys-ms-identidad` | Claim `iss` |
| `JWT_ACCESS_TTL` | no | `PT1H` | Vigencia del token |
| `IDENTITY_MAX_FAILED_ATTEMPTS` | no | `5` | Intentos antes del bloqueo |
| `IDENTITY_LOCK_DURATION` | no | `PT15M` | Duración del bloqueo |
| `IDENTITY_DEMO_USERS_ENABLED` | no | `true` | Crea las cuentas de demostración al arrancar |
| `IDENTITY_DEMO_ORGANIZATION` | no | `11111111-…-111111111111` | Organización de la demo |
| `IDENTITY_DEMO_PASSWORD` | no | `Demo1234!` | Contraseña de las cuentas de demostración |

## Cuentas de demostración

Se crean al arrancar si no existen, con la contraseña hasheada. Desactivables con
`IDENTITY_DEMO_USERS_ENABLED=false`.

| Correo | Rol |
|---|---|
| `admin@medicalsys.demo` | `ADMINISTRATOR` |
| `recepcion@medicalsys.demo` | `RECEPTIONIST` |
| `doctor@medicalsys.demo` | `PHYSICIAN` |

## Construir y ejecutar

```bash
# pruebas unitarias
./mvnw test

# imagen
docker build -t medicalsys/ms-identidad:local services/ms-identidad
```

Ejemplo de ejecución local contra infraestructura ya levantada:

```bash
docker run -d --name ms-identidad --network medicalsys-dev -p 8081:8081 \
  -e "DB_URL=jdbc:mysql://mysql:3306/identity_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USER=identity_user -e DB_PASSWORD="$IDENTIDAD_DB_PASSWORD" \
  -e REDIS_HOST=redis -e JWT_SECRET="$JWT_SECRET" \
  medicalsys/ms-identidad:local
```

## Verificación

`scripts/smoke.sh` recorre 30 comprobaciones sobre una instancia en ejecución: login, expiración de
sesión, permisos por rol, bloqueo por intentos fallidos y el CRUD de cuentas.

```bash
bash services/ms-identidad/scripts/smoke.sh http://localhost:8081
```

## Integración central (bloques listos para pegar)

`compose.yaml` de la raíz y el ruteo del gateway los administra el equipo de integración, así que
este servicio no los modifica. Todo lo necesario está verificado y resumido aquí.

### 1. Bloque para `compose.yaml` de la raíz

```yaml
  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

  ms-identidad:
    build:
      context: ./services/ms-identidad
      dockerfile: Dockerfile

    image: medicalsys/ms-identidad:local

    environment:
      SERVER_PORT: "8081"

      DB_URL: jdbc:mysql://mysql:3306/identity_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      DB_USER: identity_user
      DB_PASSWORD: ${IDENTIDAD_DB_PASSWORD}

      REDIS_HOST: redis
      REDIS_PORT: "6379"

      JWT_SECRET: ${JWT_SECRET}

    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
```

No lleva `ports:`. En el entorno compartido el único puerto publicado es el `8080` del gateway.

`identity_db` y `identity_user` ya los crea `infra/mysql/init/01-create-databases.sh`, así que la
base no requiere ningún cambio adicional.

### 2. Variable a añadir al servicio `ms-gateway` en `compose.yaml`

```yaml
      IDENTITY_SERVICE_URL: http://ms-identidad:8081
```

Y en `depends_on` del gateway:

```yaml
      ms-identidad:
        condition: service_started
```

### 3. Ruta para `services/ms-gateway/src/main/resources/application.yaml`

```yaml
            - id: identity-service
              uri: ${IDENTITY_SERVICE_URL:http://localhost:8081}
              predicates:
                - Path=/api/v1/identity/**
```

### 4. Requisito sobre `JWT_SECRET`

El valor actual de `.env.example` (`medicalsys-jwt-local`, 21 bytes) **no sirve**: HS256 exige un
secreto de al menos 32 bytes y el servicio se niega a arrancar con uno más corto. En el `.env`
compartido debe usarse un valor de 32 bytes o más, por ejemplo:

```dotenv
JWT_SECRET=medicalsys-jwt-local-secreto-de-al-menos-32-bytes
```

El mismo secreto debe compartirse con el gateway cuando este empiece a validar el token.

### 5. Verificación realizada

Levantando el stack de la raíz con los bloques anteriores:

```text
GET  http://localhost:8080/api/v1/identity/ping          200  {"service":"ms-identidad","status":"ok"}
POST http://localhost:8080/api/v1/identity/auth/login    200  devuelve accessToken
GET  http://localhost:8080/api/v1/identity/auth/me       200  perfil, roles y permisos
```

Flyway aplicó `V1` y `V2` sobre `identity_db`, e Hibernate validó el esquema sin diferencias.

```text
Servicio: ms-identidad
Puerto interno: 8081
Base de datos: identity_db (MySQL 8) + Redis 7 para sesiones
Usuario de base: identity_user
Variable de contraseña: IDENTIDAD_DB_PASSWORD
Ruta del gateway: /api/v1/identity/**
Endpoint de prueba: GET /api/v1/identity/ping
Resultado de docker build: exitoso
```
