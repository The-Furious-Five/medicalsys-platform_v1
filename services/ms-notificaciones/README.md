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
