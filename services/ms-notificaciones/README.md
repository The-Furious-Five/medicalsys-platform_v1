# ms-notificaciones

Microservicio reactivo responsable de las comunicaciones salientes de MedicalSys. Implementa
plantillas, reglas por evento, programación, consentimiento, reintentos, webhooks de estado,
mensajes entrantes y la ventana de conversación de WhatsApp. Incluye un proveedor simulado para
desarrollo y un adaptador para WhatsApp Cloud API de Meta.

## Tecnología

- Java 21 y Spring Boot 4.1.1.
- Spring WebFlux.
- Spring Data R2DBC para las operaciones de negocio.
- Flyway mediante JDBC únicamente durante el arranque.
- MySQL 8.4.
- Kafka para consumir organizaciones, pacientes, preferencias, citas, resultados y facturas.

## Ejecución con Docker

Desde la raíz de `medicalsys-platform_v1`:

```bash
docker compose up -d --build kafka kafka-init ms-notificaciones
curl http://localhost:8091/api/v1/notifications/ping
curl http://localhost:8091/actuator/health
```

El servicio utiliza el puerto interno `8091` y la base `notifications_db`. Las credenciales se
reciben por variables de entorno; nunca deben escribirse tokens de WhatsApp dentro del código.

## Autorización

El gateway valida el JWT y propaga la identidad en `X-Organization-Id`, `X-User-Id` y
`X-Permissions`. Como el servicio es reactivo, `PermissionAuthorizationWebFilter` resuelve el
controlador y aplica la misma regla que el resto: cada endpoint declara su permiso con
`@RequiresPermission` y lo que no lo declara se deniega por defecto (RBAC-04). Sin identidad
responde `401` y sin el permiso, `403`.

- Los webhooks de WhatsApp y el ping son `@PublicEndpoint`; el `POST` del webhook se verifica con la
  firma de Meta.
- La consola manual (`POST /api/v1/notifications/whatsapp/send` y `whatsapp-test.html`) exige
  `notifications.whatsapp.send`; la página pide el token de una sesión y envía por el gateway.
- El OTP de consentimientos (ms-documental) y de acceso a fichas (ms-pacientes) usa
  `POST /internal/v1/notifications/whatsapp/send`, que el gateway no publica. Depende del mismo
  flag `WHATSAPP_MANUAL_SEND_ENABLED` que la consola.

## Envío simulado

`NOTIFICATION_PROVIDER=mock` activa el adaptador local. El endpoint `dispatch` genera un
identificador con prefijo `mock-wamid-` y registra la notificación como enviada. Para usar Meta,
se selecciona `NOTIFICATION_PROVIDER=meta` y se configuran las variables `WHATSAPP_*` descritas en
el archivo `.env.example` de la raíz. Nunca se deben versionar tokens reales.

## Pruebas y cobertura

```bash
./mvnw test
./mvnw verify
```

`test` ejecuta las pruebas unitarias y de contrato y genera el informe HTML en
`target/site/jacoco/index.html`. `verify` también exige al menos 80 % de líneas y 70 % de ramas;
si la cobertura cae por debajo, el build falla. La persistencia SQL y las migraciones se validan
adicionalmente levantando MySQL con Docker, porque esas comprobaciones no sustituyen las pruebas
unitarias.

Consulta [CONTRACTS.md](CONTRACTS.md) para conocer las rutas y cuerpos HTTP.
