# MedicalSys — plataforma de microservicios

Backend del MVP de MedicalSys: doce microservicios Spring Boot detrás de un gateway, con una
base de datos por servicio. Todo el entorno local se levanta con Docker Compose.

El frontend vive en un repositorio aparte (`medicalsys_frontend`) y consume **únicamente** el
gateway, en `http://localhost:8080`. Compose solo publica el gateway; microservicios y dependencias
de datos permanecen en la red Docker `medicalsys-interna`.

## Servicios

| Servicio           | Puerto | Base de datos        | Responsabilidad                                          |
| ------------------ | ------ | -------------------- | -------------------------------------------------------- |
| `ms-gateway`       | 8080   | —                    | Enrutado y validación del JWT; propaga tenant y roles     |
| `ms-identidad`     | 8081   | `identity_db`        | Cuentas, roles, permisos y emisión de JWT                 |
| `ms-tenencia`      | 8082   | `tenancy_db`         | Organización, sucursales, prestaciones, plan y consumo    |
| `ms-catalogos`     | 8083   | `catalogs_db`        | CIE-10, especialidades, medicamentos, tipos de estudio    |
| `ms-criptografia`  | 8084   | `cryptography_db`    | Claves de cifrado por organización                        |
| `ms-pacientes`     | 8085   | `patients_db`        | Padrón administrativo de pacientes                        |
| `ms-profesionales` | 8086   | `professionals_db`   | Profesionales de salud y su vínculo con cada centro       |
| `ms-agenda`        | 8087   | `scheduling_db` (PG) | Citas, disponibilidad, bloqueos y recursos físicos        |
| `ms-historial`     | 8088   | `medical_records_db` | Expediente clínico; la narrativa se persiste cifrada      |
| `ms-documental`    | 8089   | `documents_db`       | Documentos en MinIO con verificación SHA-256              |
| `ms-facturacion`   | 8090   | `billing_db`         | Emisión de comprobantes contra un SIN simulado            |
| `ms-marketing`     | 8092   | `marketing_db`       | Campañas, segmentación, cupones y fidelización            |
| `ms-notificaciones`| 8091   | `notifications_db`   | Notificaciones y webhook de WhatsApp                     |

`ms-notificaciones` expone el webhook detrás del gateway.

## Levantar el entorno

```bash
cp .env.example .env      # los valores por defecto sirven tal cual en local
docker compose up -d --build
```

El primer arranque tarda varios minutos porque cada servicio se compila dentro de su imagen.
Compose espera a que cada dependencia responda `/actuator/health` antes de arrancar a quien la
consume, así que cuando `docker compose ps` muestre todo `healthy` el sistema está listo.

Comprobación rápida: `bash scripts/test/e2e.sh`. Requiere `IDENTITY_DEMO_USERS_ENABLED=true` (las
cuentas demo vienen desactivadas por defecto) y la cuenta OSI del `.env` (`OSI_SEED_ENABLED`). Las
cuentas demo no tienen contraseña conocida: cada una nace con una temporal aleatoria propia. Para usar
una, el OSI la reinicia («Reiniciar contraseña») y la persona elige la suya en el primer ingreso.

Para empezar de cero —por ejemplo tras cambiar una migración— hay que borrar los volúmenes:

```bash
docker compose down -v && docker compose up -d --build
```

Si vienes de una versión anterior a la red `medicalsys-interna`, ejecuta `docker compose down` una
vez antes de levantar: los contenedores de inicialización creados en la red vieja (`kafka-init`,
`minio-init`) no se recrean solos y fallan al no resolver `kafka` ni `minio`.

## Datos de demostración

El entorno local arranca con el padrón de un policlínico ficticio de La Paz: cuentas para los 15 roles de la matriz, 3
sucursales, 13 prestaciones, 14 pacientes, 7 profesionales, 16 citas repartidas alrededor del
día de hoy, 5 expedientes con narrativa clínica, 5 facturas y el módulo de marketing con sus
campañas y cupones.

Ningún dato corresponde a una persona real: los CI no son cédulas emitidas, los correos usan el
TLD reservado `.invalid` y todo comprobante lleva la leyenda de que no tiene validez fiscal.

Las cuentas demo de identidad están desactivadas por defecto. Activa `IDENTITY_DEMO_USERS_ENABLED=true`
solo para un entorno de demostración controlado. Cada siembra adicional se puede desactivar por variable:

| Variable                       | Servicio           | Efecto                                      |
| ------------------------------ | ------------------ | ------------------------------------------- |
| `IDENTITY_DEMO_USERS_ENABLED`  | `ms-identidad`     | Cuentas del personal                        |
| `PACIENTES_FLYWAY_LOCATIONS`   | `ms-pacientes`     | Quitar `classpath:db/demo` desactiva el padrón |
| `PROFESIONALES_FLYWAY_LOCATIONS` | `ms-profesionales` | Ídem para la plantilla médica             |
| `AGENDA_FLYWAY_LOCATIONS`      | `ms-agenda`        | Ídem para la agenda                         |
| `MEDICAL_RECORDS_DEMO_SEED`    | `ms-historial`     | Expedientes con narrativa                   |
| `BILLING_DEMO_INVOICES`        | `ms-facturacion`   | Facturas de ejemplo                         |
| `MARKETING_DEMO_SEED`          | `ms-marketing`     | Campañas, segmentos, cupón y anuncios       |

Las siembras del historial, la facturación y el marketing están escritas en Java y no en SQL,
porque no producen filas sino operaciones: la narrativa clínica va cifrada por el mismo
`NarrativeCipher` que usa el servicio, y una factura es una cadena de correlativo, CUFD y CUF
que solo el propio servicio sabe construir.

## Integración entre servicios

Los servicios que dependen de otro lo hacen por HTTP sobre rutas internas (`/internal/v1/...`)
que el gateway **no** publica. Todos los modos remotos están activos en Compose:

| Consumidor         | Proveedor          | Ruta interna                                   |
| ------------------ | ------------------ | ---------------------------------------------- |
| `ms-agenda`        | `ms-pacientes`     | `/internal/v1/patients/{id}/scheduling-summary` |
| `ms-agenda`        | `ms-profesionales` | `/internal/v1/professionals/{id}`               |
| `ms-facturacion`   | `ms-pacientes`     | `/internal/v1/patients/{id}/fiscal`             |
| `ms-facturacion`   | `ms-tenencia`      | `/internal/v1/prestaciones/{id}`                |
| `ms-marketing`     | `ms-pacientes`     | `/internal/v1/patients/{id}/demographics`       |
| `ms-marketing`     | `ms-tenencia`      | `/internal/v1/prestaciones/{id}`                |
| `ms-historial`     | `ms-catalogos`     | `/internal/v1/cie10/{code}` y equivalentes      |

Cada proyección de `ms-pacientes` es distinta a propósito. Agenda recibe nombre y estado;
facturación recibe además el documento y el correo; marketing recibe demografía **sin**
documento. La frontera de privacidad está escrita como tipo, no como convención.

`ms-marketing` no tiene credenciales ni ruta de red hacia `medical_records_db`: no existe
configuración con la que pueda segmentar por un dato clínico.

### Exposición de puertos

No publiques puertos de los microservicios ni de PostgreSQL, Redis, Kafka o MinIO. El frontend
debe usar el gateway publicado en `8080`; los servicios se comunican por nombre dentro de
`medicalsys-interna`. CORS se limita con `GATEWAY_ALLOWED_ORIGINS` a una lista de orígenes exactos.

El gateway comprueba en Redis que la sesión del token siga abierta: tras `POST /auth/logout` el mismo
token recibe 401 en toda la plataforma. El webhook de WhatsApp responde 401 a todo payload sin firma
HMAC válida, así que sin `WHATSAPP_APP_SECRET` no acepta ninguno, y con `NOTIFICATION_PROVIDER=meta`
el servicio no arranca si falta ese secreto.

### Publicación por túnel de Cloudflare

El servicio `cloudflared` está detrás del perfil `publico` y no arranca por defecto. Con el token del
túnel en `CLOUDFLARE_TUNNEL_TOKEN` y el origen público del frontend en `GATEWAY_ALLOWED_ORIGINS`:

```bash
docker compose --profile publico up -d
```

El túnel sale desde dentro de `medicalsys-interna` hacia `http://ms-gateway:8080`, de modo que no hace
falta publicar ningún puerto adicional. Los pasos completos están en `docs/seguridad/DEP-01-publicacion.md`.

### Escaneos y evidencia de seguridad

```bash
bash docs/seguridad/escaneos/escanear.sh despues          # Trivy (pom.xml e imágenes) y OWASP ZAP
python docs/seguridad/escaneos/resumen.py docs/seguridad/escaneos/despues
bash docs/seguridad/evidencias/capturar-evidencia.sh despues   # pruebas antes/después de HARD-01..04
bash docs/seguridad/evidencias/smoke-test.sh              # una lectura por servicio a través del gateway
```

Para correrlos sin la base de Aiven, `docs/seguridad/evidencias/compose.evidencia.yaml` añade un MySQL
desechable. El procedimiento completo es `docs/seguridad/politicas/PR-04.md`.

## Pruebas

```bash
cd services/<servicio> && ./mvnw clean test
```

Ejecutar siempre con `clean`. Sin él, una clase de prueba compilada en otra rama puede quedarse
en `target/` y fallar con `Unresolved compilation problem` sobre código que sí compila.

Varias suites usan Testcontainers y se saltan solas si Docker no está disponible.

## Documentación

- `docs/CORRECCIONES_POST_DEMO.md` — revisión técnica por microservicio.
- `docs/contracts` y `docs/demo` — contratos y guion de la demostración.
- `services/<servicio>/README.md` y `services/<servicio>/docs/` — detalle de cada servicio.
- [`docs/seguridad/matriz-roles/README.md`](docs/seguridad/matriz-roles/README.md) — matriz aprobada de 15 roles, permisos y aplicación de V11.
- `docs/seguridad/` — OWASP Top 10 (sección 11), reporte de escaneo (sección 13), reportes y evidencia.
- `docs/seguridad/politicas/` — políticas y procedimientos (PO-02, PO-07, PR-04).
