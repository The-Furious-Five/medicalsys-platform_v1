# RBAC-04 — Denegar por defecto y cubrir los servicios sin control

**Issue:** SINS-42 · **Sección del informe:** 10, 11 · **Evidencia:** [`rbac-04-denegar-por-defecto.sh`](rbac-04-denegar-por-defecto.sh)

Ningún endpoint queda abierto por omisión. Cada uno declara cómo se controla su acceso, y lo que no lo declara responde `403` aunque la solicitud traiga una sesión válida. Los cuatro servicios que nunca tuvieron control (agenda, profesionales, notificaciones y catálogos) ahora exigen el permiso del catálogo, como el resto desde RBAC-03.

## La regla

Cada endpoint lleva exactamente una de estas tres anotaciones:

| Anotación | Significa | Quién la puede usar |
|---|---|---|
| `@RequiresPermission("modulo.recurso.accion")` | Exige identidad (`401` sin ella) y ese permiso del token (`403` sin él) | Endpoints `PERMISO` de la hoja de RBAC-01 |
| `@PublicEndpoint` | No exige identidad ni permiso | Solo los `PUBLICO` de la hoja: comprobación de estado y webhooks firmados por el proveedor |
| `@InternalEndpoint` | Llamada entre servicios, sin permiso de usuario | Solo rutas `/internal/**`, que el gateway no publica. Si la anotación cae en otra ruta, el interceptor responde `403` |

Sin anotación, el interceptor responde `403`: *El endpoint no declara el permiso que exige y se deniega por defecto*. Se aplica a todas las rutas del servicio, no solo a `/api` y `/internal`, para que un controlador nuevo en otra ruta tampoco quede abierto. Solo quedan fuera `/error` y `/actuator`, que atiende Spring.

## Alcance

| Servicio | Con permiso | Públicos | Internos |
|---|---:|---:|---:|
| ms-agenda | 28 | 1 | 1 |
| ms-auditoria | 5 | 1 | 2 |
| ms-catalogos | 7 | 1 | 7 |
| ms-criptografia | 0 | 1 | 5 |
| ms-documental | 16 | 1 | 1 |
| ms-facturacion | 3 | 1 | 1 |
| ms-historial | 39 | 1 | 5 |
| ms-marketing | 44 | 1 | 6 |
| ms-notificaciones | 13 | 3 | 4 |
| ms-pacientes | 13 | 1 | 7 |
| ms-profesionales | 5 | 1 | 1 |
| ms-tenencia | 21 | 1 | 4 |
| **Total** | **194** | **14** | **44** |

- **Endpoints nuevos protegidos:** 54, con el permiso que les asigna la hoja de RBAC-01.
  - ms-agenda (28), ms-notificaciones (13), ms-catalogos (7) y ms-profesionales (5).
  - Incluye la consola manual de WhatsApp, `POST /api/v1/notifications/whatsapp/send`, que exige `notifications.whatsapp.send`.
  - `GET /api/v1/marketing/criterios-permitidos`, que exige `marketing.segment.read`.
- **Asignación de permisos:** la de RBAC-01 replicó para estos servicios las reglas del frontend, así que cada rol de sistema conserva el acceso que ya tenía desde la interfaz.
- **ms-identidad:** autoriza con Spring Security. La última regla pasa de `anyRequest().authenticated()` a `anyRequest().denyAll()`.
  - `GET /auth/me`, `POST /auth/logout` y `POST /auth/change-password` se declaran explícitamente como autenticados.
  - Una ruta nueva responde `401` sin sesión y `403` con sesión.
- **ms-criptografia:** no tiene operaciones para el usuario final.
  - Además del secreto compartido entre servicios, su nuevo `EndpointPolicyInterceptor` solo atiende lo declarado interno o público.
- **ms-notificaciones:** es reactivo (WebFlux), así que la regla la aplica `PermissionAuthorizationWebFilter`. Resuelve el controlador con el mismo `RequestMappingHandlerMapping` que usa el dispatcher y responde `401` o `403` con el error común del servicio.

### El OTP de WhatsApp pasa a `/internal`

ms-documental, para el OTP de los consentimientos, y ms-pacientes, para el de las solicitudes de acceso a una ficha, enviaban el código por la consola manual de `/api` solo con `X-Organization-Id`. Al protegerla, ese envío habría recibido `401`. El código lo pide el sistema, no una persona con `notifications.whatsapp.send`, así que ahora va por `POST /internal/v1/notifications/whatsapp/send`.

- **Implementación:** es el mismo servicio, con el mismo flag `WHATSAPP_MANUAL_SEND_ENABLED`.
- **Despliegue:** ms-notificaciones debe actualizarse junto con documental y pacientes.

La página de prueba `whatsapp-test.html`, que tampoco enviaba identidad, ahora pide el token de una sesión y envía a través del gateway.

## Control en el CI

`scripts/ci/check_permissions.py` falla si:
- un endpoint no declara exactamente una de las tres anotaciones;
- la anotación no coincide con la hoja:
  - el permiso de `@RequiresPermission`;
  - `PUBLICO` para `@PublicEndpoint`;
  - `INTERNO` y ruta `/internal/` para `@InternalEndpoint`;
- el endpoint no figura en la hoja, o la columna `control_actual` quedó desactualizada;
- queda algún `@RequiresRole`;
- ms-identidad no termina en `denyAll()` o deja de exigir algún permiso de la hoja.

```text
252 endpoints declaran su control y coinciden con la hoja de RBAC-01: 194 con permiso, 14 publicos y 44 internos. ms-identidad deniega por defecto.
```

Es el «comprobado con grep» del criterio de aceptación: ningún endpoint de negocio queda sin anotación, y cada anotación es la que dice la matriz.

## Evidencia

```bash
BASE=http://localhost:8080 PA_PASSWORD=... DEMO_PASSWORD=... bash docs/seguridad/rbac-04-denegar-por-defecto.sh
```

Recorre, a través del gateway, un endpoint de cada servicio recién protegido:
- `401` sin token;
- `403` con un rol que no tiene el permiso;
- `200` con uno que sí lo tiene.

Además prueba el `denyAll` de ms-identidad y que el ping siga siendo público. Como todos los roles de personal leen catálogos, para el caso de ms-catalogos le quita `catalogs.icd10.read` a BILLING_CLERK por la API y se lo devuelve al terminar.

Salida contra la plataforma local, con identidad, gateway, agenda (PostgreSQL), profesionales, catálogos, notificaciones, marketing y pacientes construidos desde esta rama:

```text
SERVICIO        ENDPOINT                                      SIN TOKEN  SIN PERMISO          CON PERMISO
agenda          GET /api/v1/scheduling/resources              401        403 (BILLING_CLERK)  200 (RECEPTIONIST)
profesionales   GET /api/v1/professionals                     401        403 (BILLING_CLERK)  200 (RECEPTIONIST)
notificaciones  GET /api/v1/notifications/rules               401        403 (RECEPTIONIST)   200 (ADMINISTRATOR)
marketing       GET /api/v1/marketing/criterios-permitidos    401        403 (BILLING_CLERK)  200 (RECEPTIONIST)
catalogos       GET /api/v1/catalogs/cie10?q=J00              401        403 (BILLING_CLERK*) 200 (RECEPTIONIST)
identidad       GET /api/v1/identity/no-declarada             401        403 (ADMINISTRATOR)  -
publico         GET /api/v1/scheduling/ping                   200 (@PublicEndpoint)
* BILLING_CLERK sin catalogs.icd10.read durante la prueba; el permiso ya fue restaurado.
OK: 401 sin token, 403 sin el permiso y 200 con el permiso en cada servicio.
```

El OTP también se verificó de punta a punta:
1. Una solicitud de acceso a la ficha de un paciente demo, hecha desde otra organización, respondió `201`. ms-pacientes solo responde así si ms-notificaciones aceptó el envío.
2. En `notifications_db` quedó la notificación de WhatsApp de esa organización, en estado `SENT`.
3. Las rutas de envío responden:

| Llamada | Respuesta |
|---|---|
| Consola de `/api`, sin identidad | `401` |
| `/internal`, para los servicios | `200` |
| `/internal` a través del gateway | `404`: no la publica |

## Pruebas

- **Interceptor:** cada servicio prueba su interceptor (o el filtro, en notificaciones) pasando por su lectura real de la identidad:
  - permite con el permiso;
  - `403` sin él;
  - `401` sin identidad;
  - deniega por defecto lo no declarado;
  - deja pasar lo público, y lo interno solo bajo `/internal/`.
- **ms-catalogos:** la prueba de contrato comprueba el `403` de punta a punta, con el código `FORBIDDEN` y el permiso en el mensaje.
- **ms-notificaciones:** se prueba el nuevo `InternalWhatsappController`.

`mvn verify` (Java 21, con Testcontainers donde corresponde) pasa en los 13 servicios afectados, sin pruebas omitidas:

| Servicio | Pruebas |
|---|---:|
| ms-notificaciones (con el umbral de JaCoCo) | 125 |
| ms-tenencia | 120 |
| ms-marketing | 116 |
| ms-historial | 108 |
| ms-facturacion | 100 |
| ms-documental | 34 |
| ms-pacientes | 29 |
| ms-identidad | 27 |
| ms-auditoria | 25 |
| ms-catalogos | 13 |
| ms-agenda | 12 |
| ms-criptografia | 9 |
| ms-profesionales | 8 |

En ms-marketing, la prueba que esperaba `GET /criterios-permitidos` sin identidad ahora envía el permiso, y se agregó el caso del `401`.

## Al desplegar

- **Llamadas sin gateway:** quien llame a agenda, profesionales, catálogos o notificaciones sin pasar por el gateway necesita ahora la identidad y los permisos que este propaga. `services/ms-agenda/scripts/smoke.sh` ya los envía.
- **Despliegue conjunto:** ms-notificaciones va con ms-documental y ms-pacientes, por la ruta interna del OTP.

## Pendiente fuera de esta issue

- **HARD-01:** las rutas `/internal/**` siguen protegidas solo por la red. Hasta HARD-01, un puerto de servicio publicado en el host permite llamarlas, o inventar los headers del gateway.
- **PO-02:** el Anexo D figura con RBAC-03 y RBAC-04 «En curso»; puede actualizarse en la próxima versión de la política.
