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
(`role.is_active`), la tabla `role_incompatibility` de pares de roles incompatibles y `role_delegation`,
que dice qué roles puede asignar quien tiene cada rol de organización.

Dos decisiones que conviene conocer al leer la migración:

- Los `ENUM` del diccionario se implementan como `VARCHAR` + `CHECK`. Es la misma restricción de
  dominio, y evita que la validación de esquema de Hibernate choque contra el tipo `ENUM` de MySQL.
- `organization_public_id`, `branch_public_id` y `encryption_key_public_id` son referencias externas:
  se guardan como `BINARY(16)` **sin clave foránea**, tal como exige el modelo distribuido.

Desde IAM-03 las contraseñas viven en Keycloak (realm `medicalsys`, hash Argon2id) y no en esta base: ver
[Login delegado a Keycloak](#login-delegado-a-keycloak-iam-03). `V6__keycloak_account_link.sql` agrega
`keycloak_user_id` y vuelve opcional `password_hash`, que solo existe hasta enlazar la cuenta.

## Endpoints

### Públicos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/identity/ping` | Prueba de vida del servicio |
| GET | `/actuator/health` | Salud, incluye MySQL y Redis |
| POST | `/api/v1/identity/auth/login` | Devuelve el JWT y el perfil del usuario |
| POST | `/api/v1/identity/auth/change-password` | Sin sesión: primer ingreso con temporal o contraseña vencida (`email`, `currentPassword`, `newPassword`) |

### Autenticados (cabecera `Authorization: Bearer <token>`)

| Método | Ruta | Permiso requerido |
|---|---|---|
| GET | `/api/v1/identity/auth/me` | solo estar autenticado |
| POST | `/api/v1/identity/auth/logout` | solo estar autenticado |
| POST | `/api/v1/identity/auth/change-password` | con sesión: solo estar autenticado (sin sesión, ver arriba) |
| GET | `/api/v1/identity/roles`, `/roles/{code}`, `/permissions`, `/role-incompatibilities` | `identity.role.read` |
| POST | `/api/v1/identity/roles` | `identity.role.manage` |
| PATCH | `/api/v1/identity/roles/{code}` | `identity.role.manage` |
| DELETE | `/api/v1/identity/roles/{code}` (desactiva) | `identity.role.manage` |
| PUT | `/api/v1/identity/roles/{code}/permissions` | `identity.role.manage` |
| PUT | `/api/v1/identity/roles/{code}/assignable-roles` | `identity.role.manage` |
| POST | `/api/v1/identity/role-incompatibilities` | `identity.role.manage` |
| DELETE | `/api/v1/identity/role-incompatibilities/{roleA}/{roleB}` | `identity.role.manage` |
| GET | `/api/v1/identity/users`, `/users/{publicId}` | `identity.user.read` |
| POST | `/api/v1/identity/users` | `identity.user.create` |
| PATCH | `/api/v1/identity/users/{publicId}` | `identity.user.update` |
| PATCH | `/api/v1/identity/users/{publicId}/status` | `identity.user.change_status` |
| POST | `/api/v1/identity/users/{publicId}/unlock` | `identity.user.unlock` |
| POST | `/api/v1/identity/users/{publicId}/reset-password` | `identity.user.reset` |
| POST | `/api/v1/identity/users/{publicId}/roles` | `identity.role.assign` |
| DELETE | `/api/v1/identity/users/{publicId}/roles/{role}` | `identity.role.assign` |

Cualquier otra operación bajo `/users/**`, `/roles/**` o `/role-incompatibilities/**` responde 403 hasta
que se le asigne su permiso.

Gestionar cuentas y roles pasa además por las reglas de `RoleGovernance`:
- un administrador de organización solo ve y gestiona cuentas de su organización;
- nadie modifica cuentas ni roles con permisos de gestión que no tiene;
- nadie modifica sus propios roles ni un rol que tiene asignado;
- solo se asignan roles delegados explícitamente;
- los pares incompatibles se respetan;
- ninguna operación deja a la plataforma sin quien administre roles.

El detalle está en
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

| Caso | Respuesta de `/auth/login` |
|---|---|
| Contraseña válida | `200` con el JWT propio de MedicalSys (el contrato no cambia) |
| Contraseña incorrecta o correo inexistente | `401` con el mismo mensaje: no se revela si el correo existe |
| Cuenta bloqueada (3er intento fallido) | `423`, `code: ACCOUNT_LOCKED`; la cuenta local pasa a `LOCKED` |
| Contraseña temporal o vencida (90 días) | `403`, `code: PASSWORD_CHANGE_REQUIRED` |
| Cuenta `PENDING_ACTIVATION` o `INACTIVE` | `403`, sin consultar Keycloak (no suma intentos fallidos) |
| Keycloak no responde | `503`, `code: IDENTITY_PROVIDER_UNAVAILABLE`; no hay credenciales locales de respaldo |

`PATCH /users/{id}/status` con `ACTIVE` también levanta el bloqueo en Keycloak. `/auth/change-password`
valida la contraseña actual en Keycloak y fija la nueva con la política completa; si no la cumple responde
`400`, `code: PASSWORD_POLICY_VIOLATION`, con el motivo en español.

## Login delegado a Keycloak (IAM-03)

`AuthService.login` valida la contraseña con el token endpoint del realm `medicalsys` (cliente confidencial
`ms-identidad`, Direct Access Grants). Keycloak aplica la política de IAM-02 y cuenta los intentos fallidos.
Como responde lo mismo a una contraseña incorrecta y a una cuenta bloqueada, ms-identidad distingue el
bloqueo con la service account (`/attack-detection/brute-force/users/{id}`). La sesión que abre el password
grant se cierra en el acto: el único token que circula es el de MedicalSys.

En Keycloak el username de cada cuenta es su `public_id`: inmutable y sin datos personales. Con
`editUsernameAllowed: false` Keycloak no deja cambiar el username ni al administrador. Se inicia sesión con
el correo (`loginWithEmailAllowed`), que sí se puede modificar.

**Migración de cuentas.** Al arrancar, `KeycloakAccountMigration` enlaza las cuentas sin
`keycloak_user_id`. Si Keycloak ya tiene ese correo, guarda el id. Si no, crea el usuario importando el hash
Argon2id de `password_hash` tal cual (`LegacyArgon2Hash`), sin conocer la contraseña, y borra el hash
local. La política rige para toda contraseña nueva o cambiada, y la heredada vence a los 90 días.

**Cuentas sembradas.** Las cuentas demo y las dos de arranque (PLATFORM_ADMIN y OSI) se crean directo en
Keycloak, así que la política también rige para ellas. Las demo no comparten contraseña: cada una nace con
una temporal aleatoria propia (`TemporaryPasswordGenerator`) que no se devuelve ni se registra, con cambio
obligatorio. Para usar una, el OSI la reinicia y la persona elige la suya (PR-02). Las de arranque usan la
contraseña de `PLATFORM_ADMIN_PASSWORD` y `OSI_PASSWORD`; si no cumple la política, la cuenta no se crea y
el motivo queda en el log. `IDENTITY_DEMO_ROTATE_PASSWORDS=true` da una temporal nueva a las cuentas demo
que ya existen y cierra sus sesiones: se usa un solo arranque, para retirar la contraseña común de
versiones anteriores.

Si una cuenta enlazada ya no existe en Keycloak (por ejemplo, se recreó su base), se desenlaza al arrancar.
Las cuentas demo se reponen con una temporal nueva y las de arranque con su contraseña configurada; las
demás necesitan un reinicio de contraseña. Una cuenta que se crea después del arranque se enlaza en su
primer login.

Comprobación de punta a punta: `scripts/login-keycloak.sh` recorre los cuatro casos con curl (salida de
referencia en `scripts/evidencia-iam-03.txt`).

## Gestión de usuarios sincronizada con Keycloak (IAM-04)

**User ID estándar.** Cada cuenta tiene un `userCode` `MS-AA-NNNNN` (C-01): `MS` de MedicalSys, `AA` los
dos últimos dígitos del año de alta y `NNNNN` un correlativo de `user_code_sequence`. Es único, no cambia
nunca y no se reutiliza aunque la cuenta se dé de baja; no incluye rol ni datos personales. La V7 se lo
asigna a las cuentas existentes por orden de alta. Aparece en `UserResponse` y en el contrato interno, y la
búsqueda de usuarios también filtra por él. La inmutabilidad la garantiza la entidad
(`updatable = false`); un trigger en la base requiere `--log-bin-trust-function-creators=ON` en el MySQL
compartido y queda pendiente de confirmar en Aiven.

| Operación | MedicalSys | Keycloak |
|---|---|---|
| Alta (`POST /users`) | Cuenta con `userCode` y roles | Usuario con username = `public_id`, contraseña **temporal** y `UPDATE_PASSWORD` |
| Modificación (`PATCH /users/{id}`) | Nombre, correo, teléfono, idioma | Nombre y correo (el username no cambia) |
| Baja (`PATCH /users/{id}/status` → `INACTIVE`) | Estado `INACTIVE`, la cuenta se conserva | `enabled: false`, nunca se borra |
| Reactivación (`ACTIVE`) | Estado `ACTIVE` | Habilita y levanta el bloqueo |

**Contraseña temporal.** En el alta, `password` es opcional. Sin ella, el sistema genera una de 19
caracteres que siempre cumple la política (`Kq7m-Xw3p-Rt9v-Hn4c`) y la devuelve **una sola vez** en
`temporaryPassword`. El administrador la entrega por un canal seguro y nunca conoce la definitiva. Con
ella, Keycloak aplica la política (`400 PASSWORD_POLICY_VIOLATION`). En ambos casos el usuario debe
cambiarla en su primer ingreso (`403 PASSWORD_CHANGE_REQUIRED` hasta hacerlo).

Primero se crea el usuario en Keycloak y después se asigna el `userCode`, así que un rechazo por la
política no gasta un número. Si la transacción local falla después de crear el usuario en Keycloak, el
alta se deshace allí.

Comprobación: `scripts/abm-keycloak.sh` compara MedicalSys y Keycloak en cada paso (salida de referencia
en `scripts/evidencia-iam-04.txt`).

## Desbloqueo, reinicio y cambio de contraseña (IAM-05)

| Operación | Quién | Qué hace |
|---|---|---|
| `POST /users/{id}/unlock` | `identity.user.unlock` | Levanta el bloqueo al 3er intento en Keycloak (borra los intentos y vuelve a habilitar al usuario); `LOCKED` pasa a `ACTIVE`. Antes, el administrador verifica la identidad por otro canal |
| `POST /users/{id}/reset-password` | `identity.user.reset` | Emite una contraseña temporal (`TemporaryPasswordResponse`, se muestra una sola vez), exige cambiarla y cierra las sesiones. Si la cuenta ya no existía en Keycloak, la vuelve a crear |
| `POST /auth/change-password` sin sesión | El titular | Para la temporal o la vencida: `{email, currentPassword, newPassword}`. Keycloak valida la actual (los fallos cuentan para el bloqueo) y aplica la política a la nueva, incluido el histórico de 5 |

Una cuenta dada de baja (`INACTIVE`) no se desbloquea ni se reinicia (`409`): primero se reactiva.

Demostración: `scripts/desbloqueo-reinicio.sh` recorre el criterio de aceptación (salida de referencia en
`scripts/evidencia-iam-05.txt`): 3 intentos bloquean, se desbloquea desde la app, reinicio, cambio
forzado y reutilizar una contraseña anterior se rechaza.

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
| `KEYCLOAK_URL` | no | `http://keycloak:8080` | Keycloak en la red de Compose |
| `KEYCLOAK_REALM` | no | `medicalsys` | Realm |
| `KEYCLOAK_CLIENT_ID` | no | `ms-identidad` | Cliente confidencial |
| `KEYCLOAK_CLIENT_SECRET` | sí | — | Desde `KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET` |
| `KEYCLOAK_TIMEOUT` | no | `PT5S` | Conexión y lectura |
| `IDENTITY_DEMO_USERS_ENABLED` | no | `true` | Crea las cuentas de demostración al arrancar |
| `IDENTITY_DEMO_ORGANIZATION` | no | `11111111-…-111111111111` | Organización de la demo |
| `IDENTITY_DEMO_ROTATE_PASSWORDS` | no | `false` | Un solo arranque: temporal nueva para las cuentas demo existentes |

## Cuentas de demostración

Se crean al arrancar si no existen, con la contraseña hasheada. Desactivables con
`IDENTITY_DEMO_USERS_ENABLED=false`.

Una cuenta por rol (ISO-01), cada una con el nivel MAC que su rol necesita. Ese nivel solo se fija
al crearla: después lo decide el OSI y un reinicio no lo pisa.

| Correo | Rol | Nivel MAC |
|---|---|---|
| `osi@medicalsys.demo` | `OSI` | `RESTRICTED` |
| `auditor@medicalsys.demo` | `AUDITOR` | `CONFIDENTIAL` |
| `admin@medicalsys.demo` | `ADMINISTRATOR` | `CONFIDENTIAL` |
| `doctor@medicalsys.demo` (y `pediatria@`, `ginecologia@`, …) | `PHYSICIAN` | `RESTRICTED` |
| `enfermeria@medicalsys.demo` | `NURSE` | `CONFIDENTIAL` |
| `laboratorio@medicalsys.demo` | `LAB_TECHNICIAN` | `CONFIDENTIAL` |
| `recepcion@medicalsys.demo` | `RECEPTIONIST` | `CONFIDENTIAL` |
| `caja@medicalsys.demo` | `BILLING_CLERK` | `CONFIDENTIAL` |
| `marketing@medicalsys.demo` | `MARKETING_MANAGER` | `CONFIDENTIAL` |

Fuera de la demo, el primer OSI se crea con `OSI_SEED_ENABLED=true` y `OSI_PASSWORD` (ver
`docs/seguridad/iso-01-roles-mac-dac.md`).

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
