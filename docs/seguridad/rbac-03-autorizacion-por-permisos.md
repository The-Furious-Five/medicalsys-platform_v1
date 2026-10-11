# RBAC-03 — Autorización por permisos en los servicios

**Issue:** SINS-26 · **Sección del informe:** 9.2, 10 · **Evidencia:** [`rbac-03-permisos-en-vivo.sh`](rbac-03-permisos-en-vivo.sh)

Los servicios dejan de decidir el acceso con roles fijos en el código. Cada endpoint declara el permiso del catálogo (RBAC-01) que exige, y quién lo tiene se decide en ms-identidad desde la API de roles (RBAC-02). Cambiar los permisos de un rol cambia el acceso real en el siguiente inicio de sesión, sin redesplegar.

## Cómo funciona

1. ms-identidad emite el JWT con el claim `permissions`: la unión de los permisos de los roles activos y vigentes de la cuenta.
2. El gateway valida el JWT y propaga `X-Permissions` (separados por coma) junto con `X-Organization-Id`, `X-User-Id` y `X-Roles`. Los trata como headers de confianza: descarta cualquiera que mande el cliente y los recrea desde el token.
3. Cada servicio lee `X-Permissions` en su `RequestContext`. `PermissionAuthorizationInterceptor` aplica `@RequiresPermission("modulo.recurso.accion")` antes del controlador:
   - sin identidad del gateway responde `401`;
   - con identidad, pero sin el permiso, responde `403` y el mensaje nombra el permiso que falta.

```java
@GetMapping("/{patientId}")
@RequiresPermission("patients.patient.read")
public PatientResponse get(@PathVariable UUID patientId) { ... }
```

## Alcance

| Servicio | Endpoints que pasan de rol a permiso |
|---|---:|
| ms-historial | 39 |
| ms-marketing | 43 |
| ms-tenencia | 21 |
| ms-documental | 16 |
| ms-pacientes | 13 |
| ms-auditoria | 5 |
| ms-facturacion | 3 |
| **Total** | **140** |

- **Permisos:** el de cada endpoint salió de la hoja endpoint → permiso de RBAC-01, no se eligió a mano. La asignación inicial de RBAC-01 replica los `@RequiresRole` anteriores, así que hoy cada rol de sistema conserva exactamente el mismo acceso.
- **Código retirado:** se eliminan `@RequiresRole` y su interceptor. El enum `Role` y `X-Roles` se conservan solo para lo informativo (bitácora de pacientes, exportación de auditoría).
- **Nota clínica:** ms-historial descifraba antecedentes y resultados de estudios solo para una lista fija de roles. Ahora lo decide el permiso nuevo `clinical.narrative.read` (migración V5 de ms-identidad), asignado a los mismos tres roles: PHYSICIAN, ADMINISTRATOR y PLATFORM_ADMIN.
- **ms-identidad:** ya autorizaba por permiso con `SecurityConfig`, así que no cambia.

## Control en el CI

`scripts/ci/check_permissions.py` corre en el CI del backend y falla si:
- un `@RequiresPermission` exige un permiso distinto del que la hoja de RBAC-01 asigna a su endpoint, o el endpoint no figura en la hoja;
- queda algún `@RequiresRole`.

Desde RBAC-04 también falla si un endpoint no declara su control ([`rbac-04-denegar-por-defecto.md`](rbac-04-denegar-por-defecto.md)).

Así el código y la matriz de roles y accesos (RBAC-06) no se separan.

## Evidencia: antes y después

Con la plataforma local (identidad, gateway, pacientes, historial y facturación construidos desde esta rama):

```text
Rol RECEPTIONIST · permisos patients.patient.read clinical.record.read billing.invoice.read
Endpoints: /api/v1/patients/search?q=a /api/v1/expedientes /api/v1/facturas
1. ANTES, con los permisos:              200 200 200
2. PLATFORM_ADMIN los quita por la API:   HTTP 200
3. DESPUES, nuevo login, mismo despliegue: 403 403 403
4. PLATFORM_ADMIN los devuelve:            HTTP 200
5. OTRA VEZ, nuevo login:                 200 200 200
OK: el acceso sigue a los permisos del rol, sin redesplegar.
```

A través del gateway, un `X-Permissions` inyectado por el cliente no da acceso:
- con token responde `403`, porque se reemplaza por los permisos reales del token;
- sin token responde `401`.

```bash
BASE=http://localhost:8080 PA_PASSWORD=... DEMO_PASSWORD=... bash docs/seguridad/rbac-03-permisos-en-vivo.sh
```

## Pruebas

- **Interceptor:** cada servicio tiene `PermissionAuthorizationInterceptorTest`, que pasa por su `TenantContextFilter` real y cubre:
  - permite con el permiso;
  - da `403` sin el permiso, aunque el rol sea de administración;
  - da `401` sin identidad;
  - ignora los endpoints sin anotación;
  - en pacientes y auditoría, exige `X-User-Id`.
- **Gateway:** `JwtPropagationFilterTest` comprueba que propaga los permisos del token y descarta los que inyecta el cliente.
- **Controladores:** las pruebas de historial, pacientes y documental envían ahora el permiso exacto de cada endpoint.

## Al desplegar

Un token emitido antes del despliegue no trae `clinical.narrative.read`. Hasta que el médico vuelva a iniciar sesión (como máximo, lo que dura el token: 1 h), ve los antecedentes y resultados sin el texto descifrado. Los demás permisos ya viajaban en el token desde RBAC-01.

## Completado en RBAC-04

Quedaban 54 endpoints con permiso en la hoja y sin control en el código: los de ms-agenda, ms-profesionales, ms-notificaciones y ms-catalogos, y `GET /api/v1/marketing/criterios-permitidos`. RBAC-04 los anota y hace que todo servicio deniegue por defecto lo que no declara su control ([`rbac-04-denegar-por-defecto.md`](rbac-04-denegar-por-defecto.md)).
