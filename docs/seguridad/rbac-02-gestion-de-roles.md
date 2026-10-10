# RBAC-02 — API de gestión de roles

**Issue:** SINS-25 · **Sección del informe:** 9.2 · **Migración:** `services/ms-identidad/src/main/resources/db/migration/V4__role_management.sql`
**Evidencia:** colección [`postman/RBAC-02_gestion_de_roles.postman_collection.json`](postman/RBAC-02_gestion_de_roles.postman_collection.json)

Los roles dejan de estar fijos en el código. Un rol se crea, se edita, recibe permisos del catálogo de RBAC-01 y se desactiva desde la API. El acceso real se recalcula en el siguiente inicio de sesión, sin desplegar ni tocar la base de datos.

## Endpoints

Todos viven en ms-identidad bajo `/api/v1/identity` y se publican por el gateway.

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| GET | `/roles`, `/roles/{code}` | `identity.role.read` | Rol con `active` y sus permisos |
| POST | `/roles` | `identity.role.manage` | `201`; el rol nace activo y sin permisos |
| PATCH | `/roles/{code}` | `identity.role.manage` | `name`, `description` y `active` (`false` desactiva, `true` reactiva) |
| DELETE | `/roles/{code}` | `identity.role.manage` | Desactiva el rol (no lo borra) y devuelve el rol |
| PUT | `/roles/{code}/permissions` | `identity.role.manage` | Reemplaza el conjunto completo de permisos |
| GET | `/role-incompatibilities` | `identity.role.read` | Pares de roles incompatibles |
| POST | `/role-incompatibilities` | `identity.role.manage` | `201`; `{roleA, roleB, reason}` en cualquier orden |
| DELETE | `/role-incompatibilities/{roleA}/{roleB}` | `identity.role.manage` | `204` |

Cualquier otro método sobre `/roles/**` o `/role-incompatibilities/**` responde `403`: nada queda abierto por omisión.

```json
POST /api/v1/identity/roles
{ "code": "AUDITOR", "name": "Auditor", "description": "Revisa la bitácora", "scope": "ORGANIZATION" }

PUT /api/v1/identity/roles/AUDITOR/permissions
{ "permissions": ["audit.log.read", "audit.log.verify"] }

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

## Segregación de funciones y no escalada

Las reglas están en `RoleGovernance`, el único lugar del código que respalda la política de control de acceso (PO-02).

| Regla | Dónde aplica | Respuesta |
|---|---|---|
| Nadie se asigna ni se revoca roles a sí mismo | asignar, revocar | `403` |
| Nadie edita, desactiva ni cambia los permisos de un rol que tiene asignado | `PATCH`, `DELETE`, `PUT /permissions` | `403` |
| Al editar un rol solo se agregan permisos que quien edita ya tiene; quitar nunca escala | `PUT /permissions` | `403` |
| Un rol con alcance `PLATFORM` solo lo asigna o revoca quien tiene un rol de plataforma | asignar, revocar | `403` |
| Quien asigna no puede otorgar permisos de gestión (módulos `IDENTITY` y `TENANCY`) que no tenga | asignar, revocar | `403` |
| Un administrador de organización solo gestiona roles de cuentas de su organización | asignar, revocar | `403` |
| Crear una cuenta con roles exige también `identity.role.assign` | `POST /users` con `roles` | `403` |
| Un par de roles declarados incompatibles no puede coincidir en una misma cuenta | asignar | `409` |
| No se declara un par que alguna cuenta ya viola: primero se revoca uno de los dos | `POST /role-incompatibilities` | `409` |
| `PLATFORM_ADMIN` conserva siempre el catálogo completo | `PUT /roles/PLATFORM_ADMIN/permissions` | `409` |

Por qué la regla de delegación se limita a `IDENTITY` y `TENANCY`: ADMINISTRATOR no tiene la firma clínica de PHYSICIAN. Si tuviera que poseer todos los permisos del rol que asigna, no podría dar de alta a un médico ni a un recepcionista, que es justamente su trabajo. Los permisos operativos (clínicos, agenda, caja) se delegan. Los que deciden el acceso de otras personas o de la organización no pueden superar los de quien asigna; si no, un administrador de centro podría fabricar un administrador de plataforma.

Las incompatibilidades cuentan cualquier asignación no vencida, aunque el rol esté desactivado o la asignación empiece en el futuro. Así el conflicto no aparece solo cuando se reactiva el rol o llega la fecha.

Los permisos del actor salen de la base en el momento de la operación, no de su token. Si alguien perdió un permiso después de iniciar sesión, la regla ya lo sabe.

## Sesiones

Toda operación que quita acceso cierra las sesiones de las cuentas afectadas: revocar un rol, desactivarlo o quitarle permisos. Agregar permisos no cierra sesiones; el acceso nuevo llega en el siguiente inicio de sesión.

El cierre de sesión hoy solo lo respeta ms-identidad. El gateway valida la firma del JWT pero no consulta las sesiones (HARD-02), así que en los demás servicios el token sigue sirviendo hasta que expira.

## Para las siguientes issues

- **RBAC-03:**
  - Un rol personalizado viaja en `X-Roles` y los servicios lo ignoran sin fallar (`Role.parse` devuelve vacío).
  - Hasta que exista `@RequiresPermission`, el acceso fuera de ms-identidad sigue decidiéndose por los roles de sistema.
  - Crear por la API el rol `MARKETING_MANAGER` ya lo hace reconocible para los `@RequiresRole` de ms-marketing, que lo esperan. `AUDITOR` (LOG-04) no lo conoce ningún servicio: dependerá de `@RequiresPermission`.
- **RBAC-05:**
  - `GET /roles` devuelve todos los roles, incluidos los inactivos, con `active` y sus permisos.
  - `GET /permissions` da el catálogo para armar la matriz.
- **LOG-02:** `RoleManagementService` y `UserService` reciben al actor en cada operación, así que los eventos (`ROLE_CREATED`, `ROLE_DISABLED`, `ROLE_PERMISSIONS_CHANGED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`) se pueden emitir ahí con actor y objetivo.
- **IAM-04:** la próxima migración de ms-identidad es `V5`.

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
2. Se crea el rol, se le asignan permisos y se asigna a la cuenta.
3. La cuenta vuelve a iniciar sesión y recibe `200`.

Después prueba cada regla de segregación y la baja del rol. Al final deja el entorno limpio: sin el par incompatible, con el rol desactivado y la cuenta de prueba inactiva.
