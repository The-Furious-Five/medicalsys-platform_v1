# RBAC-02 — API de gestión de roles

**Issue:** SINS-25 · **Sección del informe:** 9.2 · **Migración:** `services/ms-identidad/src/main/resources/db/migration/V4__role_management.sql`
**Evidencia:** colección [`postman/RBAC-02_gestion_de_roles.postman_collection.json`](postman/RBAC-02_gestion_de_roles.postman_collection.json)

Los roles dejan de estar fijos en el código. Un rol se crea, se edita, recibe permisos del catálogo de RBAC-01, define qué otros roles puede asignar y se desactiva desde la API. El acceso real se recalcula en el siguiente inicio de sesión, sin desplegar ni tocar la base de datos.

## Endpoints

Todos viven en ms-identidad bajo `/api/v1/identity` y se publican por el gateway.

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| GET | `/roles`, `/roles/{code}` | `identity.role.read` | Rol con `active`, `permissions` y `assignableRoles` |
| POST | `/roles` | `identity.role.manage` | `201`; el rol nace activo, sin permisos y sin delegación |
| PATCH | `/roles/{code}` | `identity.role.manage` | `name`, `description` y `active` (`false` desactiva, `true` reactiva) |
| DELETE | `/roles/{code}` | `identity.role.manage` | Desactiva el rol (no lo borra) y devuelve el rol |
| PUT | `/roles/{code}/permissions` | `identity.role.manage` | Reemplaza el conjunto completo de permisos |
| PUT | `/roles/{code}/assignable-roles` | `identity.role.manage` | Reemplaza los roles que quien tiene este rol puede asignar |
| GET | `/role-incompatibilities` | `identity.role.read` | Pares de roles incompatibles |
| POST | `/role-incompatibilities` | `identity.role.manage` | `201`; `{roleA, roleB, reason}` en cualquier orden |
| DELETE | `/role-incompatibilities/{roleA}/{roleB}` | `identity.role.manage` | `204` |

Cualquier otro método sobre `/roles/**` o `/role-incompatibilities/**` responde `403`: nada queda abierto por omisión.

```json
POST /api/v1/identity/roles
{ "code": "AUDITOR", "name": "Auditor", "description": "Revisa la bitácora", "scope": "ORGANIZATION" }

PUT /api/v1/identity/roles/AUDITOR/permissions
{ "permissions": ["audit.log.read", "audit.log.verify"] }

PUT /api/v1/identity/roles/ADMINISTRATOR/assignable-roles
{ "roles": ["ADMINISTRATOR", "PHYSICIAN", "RECEPTIONIST", "BILLING_CLERK", "PATIENT", "AUDITOR"] }

POST /api/v1/identity/users/{publicId}/roles
{ "role": "AUDITOR" }
```

El código del rol es inmutable porque viaja en el JWT y en `X-Roles`. Debe tener de 3 a 30 caracteres en mayúsculas, dígitos o `_`, y empezar con una letra. Lo valida la API y también un `CHECK` sensible a mayúsculas en la tabla.

## Qué significa desactivar

Un rol desactivado:
- no se puede asignar (`409`);
- no aporta roles ni permisos al token, aunque la asignación siga vigente;
- conserva sus asignaciones, para la trazabilidad y para poder reactivarlo.

Los roles de sistema (los seis del seed) no se desactivan (`409`). Si a uno le sobra un permiso, se le quita con `PUT /permissions`.

## Delegación: quién puede asignar qué

Asignar o revocar un rol exige `identity.role.assign` y, además, que la asignación esté delegada:

- **Rol de plataforma** (alcance `PLATFORM`, como PLATFORM_ADMIN): gestiona cualquier rol en cualquier organización.
- **Rol de organización** (como ADMINISTRATOR): solo asigna los roles que figuran en su `assignableRoles`. V4 le delega a ADMINISTRATOR los roles operativos del seed: ADMINISTRATOR, PHYSICIAN, RECEPTIONIST, BILLING_CLERK y PATIENT.

Un rol nuevo no se delega a nadie por omisión. Para que un administrador de centro pueda asignarlo, alguien con `identity.role.manage` lo agrega a la lista de ADMINISTRATOR. Así la delegación es una decisión explícita y visible en la matriz (RBAC-06), no un efecto de qué permisos tiene cada uno.

La delegación nunca escala. Aunque un rol esté delegado, nadie otorga permisos de gestión de accesos (módulos `IDENTITY` y `TENANCY`) que no tenga. Si se delegara por error a ADMINISTRATOR un rol con `identity.role.manage`, igual no podría asignarlo.

## Reglas sobre la administración de accesos

Están todas en `RoleGovernance`, el único lugar del código que respalda la política de control de acceso ([PO-02](politicas/PO-02.md), lineamientos 6.3 a 6.6).

| Regla | Dónde aplica | Respuesta |
|---|---|---|
| Nadie se asigna ni se revoca roles a sí mismo | asignar, revocar | `403` |
| Solo se asignan o revocan roles delegados (salvo un rol de plataforma) | asignar, revocar | `403` |
| Un rol con alcance `PLATFORM` solo lo asigna o revoca quien tiene un rol de plataforma | asignar, revocar | `403` |
| Nadie otorga permisos de gestión (`IDENTITY`, `TENANCY`) que no tiene | asignar, revocar | `403` |
| Crear una cuenta con roles exige también `identity.role.assign` | `POST /users` con `roles` | `403` |
| Un par de roles declarados incompatibles no puede coincidir en una misma cuenta | asignar | `409` |
| No se declara un par que alguna cuenta ya viola: primero se revoca uno de los dos | `POST /role-incompatibilities` | `409` |
| Nadie edita un rol que tiene asignado, ni sus permisos, su delegación o sus incompatibilidades | `PATCH`, `DELETE`, `PUT`, pares | `403` |
| Nadie edita un rol con permisos de gestión que no tiene: los roles por encima de uno no se tocan | `PATCH`, `DELETE`, `PUT`, pares | `403` |
| Al editar un rol solo se agregan permisos que quien edita ya tiene; quitar nunca escala | `PUT /permissions` | `403` |
| Un rol de organización no delega roles de plataforma | `PUT /assignable-roles` | `400` |

Por qué la delegación es explícita y no se exige "tener todos los permisos del rol": ADMINISTRATOR no tiene la firma clínica de PHYSICIAN. Con esa exigencia no podría dar de alta a un médico, que es justamente su trabajo. La delegación permite que lo haga porque así se decidió, y sin darle la firma a él.

Los permisos y la delegación del actor salen de la base en el momento de la operación, no de su token. Si alguien perdió un permiso después de iniciar sesión, la regla ya lo sabe.

Las incompatibilidades cuentan cualquier asignación no vencida, aunque el rol esté desactivado o la asignación empiece en el futuro. Así el conflicto no aparece solo cuando se reactiva el rol o llega la fecha.

## Aislamiento entre organizaciones

Un actor de organización (sin ningún rol de plataforma) solo trabaja con cuentas de su organización:

| Operación | Si la cuenta u organización es otra |
|---|---|
| `GET /users` | Se limita a su organización aunque no la pida; pedir otra da `403` |
| `GET /users/{id}`, `PATCH`, `PATCH /status`, roles | `404`: para él la cuenta no existe |
| `POST /users` | La cuenta nueva queda en su organización; pedir otra da `403` |
| Asignar un rol con alcance de otra organización | `403` |

Además, para modificar una cuenta (datos, estado o roles) hay que tener todos los permisos de gestión que ella tiene. Un administrador de centro ve la cuenta de PLATFORM_ADMIN, que comparte su organización como contexto, pero no puede editarla, desactivarla ni quitarle roles (`403`).

## Continuidad: siempre queda quien administre roles

Ninguna operación puede dejar a la plataforma sin una cuenta activa que tenga `identity.role.manage` por un rol activo y vigente (`409`). Esto vale para:
- desactivar o bloquear cuentas;
- revocar roles;
- quitar permisos;
- desactivar roles.

El caso típico es el último administrador desactivando su propia cuenta. La regla solo protege lo que existe: en un entorno sin PLATFORM_ADMIN sembrado no bloquea nada.

Gracias a esto PLATFORM_ADMIN no necesita ser intocable. Sus permisos se pueden corregir, por ejemplo quitarle los clínicos como pide RBAC-06, siempre que lo haga alguien a su altura que no lo tenga. Un rol de oficial de seguridad, con los permisos de gestión y asignado por la plataforma, puede quitárselos. El propio PLATFORM_ADMIN no puede, por segregación de funciones.

## Sesiones

Toda operación que quita acceso cierra las sesiones de las cuentas afectadas: revocar un rol, desactivarlo o quitarle permisos. Agregar permisos o cambiar la delegación no cierra sesiones; lo nuevo llega en el siguiente inicio de sesión.

El cierre de sesión hoy solo lo respeta ms-identidad. El gateway valida la firma del JWT pero no consulta las sesiones (HARD-02), así que en los demás servicios el token sigue sirviendo hasta que expira.

## Para las siguientes issues

- **RBAC-03:**
  - Un rol personalizado viaja en `X-Roles` y los servicios lo ignoran sin fallar (`Role.parse` devuelve vacío).
  - Hasta que exista `@RequiresPermission`, el acceso fuera de ms-identidad sigue decidiéndose por los roles de sistema.
  - Crear por la API el rol `MARKETING_MANAGER` ya lo hace reconocible para los `@RequiresRole` de ms-marketing, que lo esperan. `AUDITOR` (LOG-04) no lo conoce ningún servicio: dependerá de `@RequiresPermission`.
- **RBAC-05:**
  - `GET /roles` devuelve todos los roles, incluidos los inactivos, con `active`, sus permisos y `assignableRoles`.
  - `GET /permissions` da el catálogo para armar la matriz.
- **RBAC-06:** la matriz puede incluir la delegación (quién asigna qué) junto a los permisos.
- **LOG-02:** `RoleManagementService` y `UserService` reciben al actor en cada operación, así que los eventos (`ROLE_CREATED`, `ROLE_DISABLED`, `ROLE_PERMISSIONS_CHANGED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`) se pueden emitir ahí con actor y objetivo.
- **IAM-04:**
  - El ABM de cuentas ya aplica el aislamiento por organización y la regla de no gestionar cuentas por encima de uno; sincronizar con Keycloak debe mantenerlo.
  - La próxima migración de ms-identidad es `V5`.

## Cómo reproducir la evidencia

Con la plataforma levantada y `PLATFORM_ADMIN_SEED_ENABLED=true`:

```bash
newman run docs/seguridad/postman/RBAC-02_gestion_de_roles.postman_collection.json \
  --env-var baseUrl=http://localhost:8080 \
  --env-var paPassword="$PLATFORM_ADMIN_PASSWORD" \
  --env-var adminPassword=... \
  --env-var testPassword=...
```

La colección recorre el criterio de aceptación:
1. La cuenta de prueba recibe `403`.
2. Se crea el rol y se le asignan permisos.
3. Sin delegación, ADMINISTRATOR no puede asignarlo.
4. Se le delega y lo asigna.
5. La cuenta vuelve a iniciar sesión y recibe `200`.

Después prueba la segregación, el aislamiento entre organizaciones y la baja del rol. Al final deja el entorno como estaba: restaura la delegación de ADMINISTRATOR, quita el par y desactiva el rol y las cuentas de prueba.

La colección no prueba la regla de continuidad, porque en un entorno compartido podría desactivar al único PLATFORM_ADMIN si la regla fallara. Se verificó aparte, en un entorno desechable.

`services/ms-identidad/scripts/smoke.sh` cubre la regresión del servicio, incluido el aislamiento. Funciona con el bash 3.2 de macOS y con `python3` sin alias.
