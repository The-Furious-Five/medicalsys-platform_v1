# Sección 11 — Evaluación OWASP Top 10

**Tarea:** INF-06 (SINS-40) · **Responsable:** Juan Jose Claros Suntura · **Fecha de las pruebas:** 12/10/2026

Se evaluó la plataforma contra el OWASP Top 10:2021 y se documentan las cuatro categorías en las que se encontró y corrigió una vulnerabilidad. Para cada una se describe el problema, cómo se explotaba, la corrección y la prueba posterior.

## Cómo se obtuvo la evidencia

Las mismas solicitudes se ejecutaron dos veces con el script `docs/seguridad/evidencias/capturar-evidencia.sh`:

- **Antes:** rama `develop` en el commit `baef748`, levantada con Docker Compose tal como estaba (todos los puertos publicados, CORS `*`).
- **Después:** versión corregida, con las tareas HARD-01 a HARD-04 integradas.

En ambos casos la base de datos fue un MySQL desechable (`docs/seguridad/evidencias/compose.evidencia.yaml`), para no usar credenciales reales ni tocar datos compartidos. Las cuentas demo se activaron de forma explícita para poder iniciar sesión. La salida completa de cada prueba está en `docs/seguridad/evidencias/antes/` y `docs/seguridad/evidencias/despues/`; los tokens no se guardan.

| Categoría | Vulnerabilidad | Tarea | Resultado antes | Resultado después |
|---|---|---|---|---|
| A01 Broken Access Control | Acceso directo a los servicios saltándose el gateway | HARD-01 | HTTP 200 con datos clínicos | Sin conexión |
| A07 Identification and Authentication Failures | El token seguía valiendo después de cerrar sesión | HARD-02 | HTTP 200 | HTTP 401 |
| A05 Security Misconfiguration | CORS abierto, rutas internas y diagnóstico públicos, cuentas demo activas | HARD-03 | Origen ajeno aceptado | HTTP 403 |
| A08 Software and Data Integrity Failures | Webhook de WhatsApp aceptaba mensajes sin firma | HARD-03 | HTTP 200 | HTTP 401 |

## 11.1 A01 — Broken Access Control: acceso directo saltándose el gateway

### Descripción

El gateway valida el JWT y entrega a cada servicio la identidad en las cabeceras `X-User-Id`, `X-Organization-Id`, `X-Roles` y `X-Permissions`. Los servicios confían en esas cabeceras y no vuelven a validar el token. El modelo es correcto solo si nadie más que el gateway puede hablar con los servicios, pero `compose.yaml` publicaba en el host los puertos de los 13 microservicios y de PostgreSQL, Redis, Kafka y MinIO.

### Explotación antes de la corrección

Cualquiera con acceso de red al host podía escribir las cabeceras a mano y pedir datos clínicos sin token:

```
$ curl http://localhost:8088/api/v1/encuentros -H "X-Organization-Id: 00000000-0000-4000-8000-0000000000a1" -H "X-User-Id: 00000000-0000-4000-8000-000000000903" -H "X-Roles: PHYSICIAN" -H "X-Permissions: clinical.encounter.read"
HTTP/1.1 200
{"content":[{"id":"658cc155-...","medicalRecordId":"d2584002-...","patientId":"10000000-0000-4000-8000-000000000001", ...
```

La misma solicitud a través del gateway era rechazada con 401, lo que confirma que la única barrera era el gateway y que se podía rodear. Había nueve servicios con puerto publicado en el entorno de prueba, incluido Redis (`16379`), donde viven las sesiones.

### Corrección aplicada

- Se eliminaron las declaraciones `ports:` de los 13 microservicios y de PostgreSQL, Redis, Kafka y MinIO en `compose.yaml`. Solo `ms-gateway` publica el puerto 8080.
- Los servicios se comunican por nombre en la red `medicalsys-interna`.
- El control por permisos en cada servicio (RBAC-03 y RBAC-04) se mantiene como segunda barrera.

### Prueba posterior

```
$ docker compose ps --format '{{.Service}} {{.Ports}}'
ms-gateway 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp

$ curl http://localhost:8088/api/v1/encuentros -H "X-Roles: PHYSICIAN" ...
SIN RESPUESTA (curl exit 7): Failed to connect to localhost port 8088
```

El sistema sigue funcionando por el gateway: el smoke test (`evidencias/despues/00-smoke-test.txt`) obtiene respuesta correcta de los 13 servicios con una sesión real.

Evidencia: `01-hard01-acceso-directo.txt` en ambas carpetas.

## 11.2 A07 — Identification and Authentication Failures: revocación de sesión

### Descripción

`ms-identidad` guarda en Redis el identificador `jti` de cada token emitido y lo borra al cerrar sesión. Pero solo `ms-identidad` consultaba Redis: el gateway únicamente verificaba la firma y la expiración del JWT. Para el resto de la plataforma, cerrar sesión no tenía efecto hasta que el token expirara (una hora).

### Explotación antes de la corrección

Un token robado, o el de un usuario al que se acaba de retirar el acceso, seguía sirviendo después del cierre de sesión:

```
$ curl -X POST http://localhost:8080/api/v1/identity/auth/logout -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 200
{"status":"session-closed"}

$ curl http://localhost:8080/api/v1/encuentros -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 200
{"content":[{"id":"658cc155-...

$ curl "http://localhost:8080/api/v1/patients/search?q=a" -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 200
{"content":[{"patientId":"10000000-0000-4000-8000-000000000001", ... "givenNames":"Maria Elena", ...
```

### Corrección aplicada

- `JwtPropagationFilter` del gateway consulta en Redis la clave `identity:session:{jti}`, la misma que escribe el `SessionStore` de identidad, en cada solicitud con token. Si no existe, responde 401.
- Si Redis no responde, el gateway falla cerrado con 503 en lugar de dejar pasar el token.
- El gateway depende de Redis en `compose.yaml`.
- Pruebas unitarias en `JwtPropagationFilterTest`: sesión ausente → 401; Redis caído → 503.

### Prueba posterior

Mismo flujo, mismo token:

```
$ curl -X POST http://localhost:8080/api/v1/identity/auth/logout -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 200
{"status":"session-closed"}

$ curl http://localhost:8080/api/v1/encuentros -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 401
{"title":"Sesion revocada","detail":"La sesion ya no esta activa"}

$ curl "http://localhost:8080/api/v1/patients/search?q=a" -H "Authorization: Bearer <TOKEN>"
HTTP/1.1 401
{"title":"Sesion revocada","detail":"La sesion ya no esta activa"}
```

Evidencia: `02-hard02-revocacion-sesion.txt` en ambas carpetas.

### Bloqueo de cuenta

El bloqueo por intentos fallidos ya existía en `ms-identidad` (5 intentos, 15 minutos) y se verificó sobre la versión corregida: tras cinco contraseñas incorrectas la cuenta responde `423 Cuenta bloqueada`, también con la contraseña correcta. Evidencia: `despues/08-a07-bloqueo-de-cuenta.txt`.

## 11.3 A05 — Security Misconfiguration: valores por defecto inseguros

### Descripción

Varios valores por defecto eran los cómodos para una demo y no los seguros:

- `GATEWAY_ALLOWED_ORIGINS` valía `*`.
- Las rutas `/internal/v1/**` de identidad tenían `permitAll`.
- Actuator mostraba el detalle de salud (`show-details: always`) sin autenticación.
- Las cuentas demo, con contraseña conocida, se sembraban por defecto (`IDENTITY_DEMO_USERS_ENABLED:true`).
- El gateway no enviaba cabeceras de seguridad.

### Explotación antes de la corrección

Cualquier sitio web podía llamar a la API desde el navegador de un usuario:

```
$ curl -X OPTIONS http://localhost:8080/api/v1/patients/search -H "Origin: https://malicioso.example" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"
HTTP/1.1 200
Access-Control-Allow-Origin: *
```

La ruta interna de identidad entregaba datos de cualquier usuario sin autenticación, y el diagnóstico revelaba la infraestructura:

```
$ curl http://localhost:8081/internal/v1/users/00000000-0000-4000-8000-000000000903
HTTP/1.1 200
{"publicId":"00000000-...903","email":"doctor@medicalsys.demo","fullName":"Ricardo Salazar Peredo","status":"ACTIVE","roles":["PHYSICIAN"]}

$ curl http://ms-identidad:8081/actuator/health
{"components":{"db":{"details":{"database":"MySQL", ...},"redis":{"details":{"version":"7.4.11"}, ...
```

### Corrección aplicada

- CORS acepta solo la lista exacta de `GATEWAY_ALLOWED_ORIGINS`; el valor por defecto es `http://localhost:4200`.
- `/internal/v1/**` de identidad pasa a `denyAll`. Ningún otro servicio consumía esa ruta.
- `show-details: when-authorized` en los 12 servicios que lo tenían en `always`.
- `IDENTITY_DEMO_USERS_ENABLED` vale `false` por defecto en `application.yaml`, `compose.yaml` y `.env.example`.
- El gateway agrega `Strict-Transport-Security`, `X-Content-Type-Options`, `Content-Security-Policy: frame-ancestors 'none'`, `Referrer-Policy`, `X-Frame-Options` y `Cross-Origin-Resource-Policy`, y vacía la cabecera `Server`, también en sus respuestas 401 y 503.

### Prueba posterior

```
$ curl -X OPTIONS http://localhost:8080/api/v1/patients/search -H "Origin: https://malicioso.example" ...
HTTP/1.1 403
Invalid CORS request

$ curl -X OPTIONS http://localhost:8080/api/v1/patients/search -H "Origin: http://localhost:4200" ...
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:4200

$ docker compose exec -T ms-gateway curl http://ms-identidad:8081/internal/v1/users/00000000-0000-4000-8000-000000000903
{"detail":"Se requiere un token de acceso valido", ... "status":401}

$ docker compose exec -T ms-gateway curl http://ms-identidad:8081/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
```

Las dos últimas se piden desde dentro de la red de Compose, porque desde el host el puerto 8081 ya no existe.

Evidencia: `04-hard03-cors.txt`, `05-hard03-internas-actuator.txt` y `06-hard04-cabeceras.txt` en ambas carpetas.

## 11.4 A08 — Software and Data Integrity Failures: firma del webhook

### Descripción

El webhook de WhatsApp recibe de Meta los estados de entrega y los mensajes de los pacientes, y con ellos actualiza notificaciones, abre conversaciones y registra bajas de comunicaciones. La autenticidad del mensaje se comprueba con la firma HMAC-SHA256 de la cabecera `X-Hub-Signature-256`. El servicio omitía esa comprobación cuando `WHATSAPP_APP_SECRET` estaba vacío, que era la configuración por defecto.

### Explotación antes de la corrección

El webhook es público por diseño, así que cualquiera podía inyectar eventos falsos:

```
$ curl -X POST http://localhost:8080/api/v1/notifications/webhooks/whatsapp -H "Content-Type: application/json" -d '{"object":"whatsapp_business_account","entry":[]}'
HTTP/1.1 200
```

Con un payload elaborado se podía marcar como leída una notificación que no lo fue, o dar de baja a un paciente de las comunicaciones enviando un "STOP" en su nombre.

### Corrección aplicada

- `WhatsappWebhookService` rechaza con 401 todo payload sin firma válida. Sin secreto configurado rechaza siempre, en vez de aceptar siempre.
- Con `NOTIFICATION_PROVIDER=meta` y `WHATSAPP_APP_SECRET` vacío, el servicio no arranca.
- 17 pruebas unitarias en `WhatsappWebhookServiceTest` cubren firma ausente, inválida, válida, secreto ausente y el fallo de arranque.

### Prueba posterior

Configuración por defecto (sin secreto):

```
$ curl -X POST http://localhost:8080/api/v1/notifications/webhooks/whatsapp -H "Content-Type: application/json" -d '{"object":"whatsapp_business_account","entry":[]}'
HTTP/1.1 401
{"status":401,"code":"INVALID_WEBHOOK_SIGNATURE","message":"El webhook no tiene un secreto configurado para validar la firma", ...
```

Con secreto configurado: sin firma → 401 (`El webhook no contiene una firma valida`); firma incorrecta → 401 (`La firma del webhook no coincide`); firma HMAC-SHA256 correcta → 200.

Arranque con proveedor `meta` y sin secreto:

```
$ docker compose run --rm --no-deps -e NOTIFICATION_PROVIDER=meta -e WHATSAPP_APP_SECRET= ms-notificaciones
ERROR ... Application run failed
Caused by: java.lang.IllegalStateException: WHATSAPP_APP_SECRET es obligatorio cuando NOTIFICATION_PROVIDER=meta
codigo de salida del contenedor: 1
```

Evidencia: `03-hard03-webhook.txt` en ambas carpetas, y `03-hard03-webhook-con-secreto.txt` y `07-hard03-arranque-meta-sin-secreto.txt` en `despues/`.

### Hash de documentos

La integridad de los documentos clínicos es un control que ya existía y no se modificó: `ms-documental` calcula el SHA-256 de cada archivo al subirlo (`HashingService`), lo guarda junto al documento y lo vuelve a calcular en cada descarga; si no coincide, la descarga falla (`DocumentService`). Está cubierto por `DocumentServiceTest`. No se repitió una prueba manual de manipulación en este ciclo.

## Limitaciones

- Las pruebas se hicieron en un entorno local con base de datos desechable, no sobre el despliegue público.
- El análisis dinámico automatizado (ZAP) cubre solo las rutas que la araña descubre sin autenticación; el resto de esta sección es prueba manual dirigida. Ver la sección 13.
- No se evaluaron en esta sección las demás categorías del Top 10. A06 (componentes vulnerables) se trata en la sección 13.
