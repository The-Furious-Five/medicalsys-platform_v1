# Despliegue en el VPS (CI/CD)

Workflow: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml). Cada push a `main`:

1. **test**: `mvn test` de los 14 microservicios en paralelo. Los que usan Testcontainers
   levantan MySQL con el Docker del runner.
2. **build**: construye las 14 imagenes y las publica en GHCR como
   `ghcr.io/<owner>/medicalsys/<servicio>:<sha>` (y `:latest`). El VPS no compila Java.
3. **deploy**: por SSH copia `compose.yaml`, `compose.prod.yaml` e `infra/` a
   `/opt/medicalsys`, escribe el `.env` desde el secreto `ENV_FILE`, descarga las imagenes
   del commit y ejecuta `docker compose up -d`. Termina en verde cuando el gateway queda `healthy`.

Tambien se lanza a mano desde **Actions → CI/CD - Deploy al VPS → Run workflow**.

En produccion solo el gateway publica puerto (`8080`). Keycloak escucha en `127.0.0.1:18080`
del VPS; su consola se abre con un tunel:

```bash
ssh -L 18080:127.0.0.1:18080 usuario@vps
```

y luego `http://localhost:18080/admin`.

## Preparar el VPS (una sola vez)

- Docker Engine con el plugin Compose **2.24.4 o superior** (`docker compose version`).
- Un usuario de deploy que pueda usar Docker (`usermod -aG docker deploy`) y sea dueno de
  `/opt/medicalsys` (`sudo mkdir -p /opt/medicalsys && sudo chown deploy: /opt/medicalsys`).
- Una llave SSH solo para GitHub Actions. Se genera en tu computadora y la publica va al VPS:

  ```bash
  ssh-keygen -t ed25519 -C "github-actions-medicalsys" -f medicalsys_deploy -N ""
  ssh-copy-id -i medicalsys_deploy.pub deploy@IP_DEL_VPS
  ```

- Firewall: abrir solo 22 y 8080, o 80/443 si pones un proxy con dominio delante del gateway.
- RAM: el stack completo (14 servicios Java + MySQL, Postgres, Kafka, Redis, MinIO y Keycloak)
  necesita unos 8 GB. Con menos, agrega swap.

## Secretos del repositorio

**Settings → Secrets and variables → Actions**. Conviene crearlos dentro del environment
`production` (Settings → Environments), que es el que usa el job de deploy.

| Secreto | Obligatorio | Contenido |
|---|---|---|
| `VPS_HOST` | si | IP o dominio del VPS |
| `VPS_USER` | si | Usuario de deploy, p. ej. `deploy` |
| `VPS_SSH_KEY` | si | Llave **privada** completa (`medicalsys_deploy`, incluidas las lineas BEGIN/END) |
| `VPS_PORT` | no | Puerto SSH si no es 22 |
| `VPS_KNOWN_HOSTS` | recomendado | Salida de `ssh-keyscan -p 22 IP_DEL_VPS`. Sin el, se confia en la huella del primer contacto |
| `ENV_FILE` | si | Contenido completo del `.env` de produccion (plantilla abajo) |

Variable opcional (pestana **Variables**): `VPS_DEPLOY_PATH`, por defecto `/opt/medicalsys`.

`GITHUB_TOKEN` no se crea: GitHub lo da en cada ejecucion y el workflow lo usa para publicar
y descargar las imagenes de GHCR.

## Plantilla de `ENV_FILE`

Cambia **todos** los valores `CAMBIAR`. Genera cada secreto con `openssl rand -base64 32`.

Las contrasenas de base de datos y `KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET` se fijan en el **primer**
despliegue: `mysql-init` no cambia la contrasena de un usuario que ya existe, y el realm solo
se importa si todavia no existe. Si las cambias despues, tambien hay que cambiarlas a mano en
MySQL o en Keycloak.

```dotenv
# Seguridad
JWT_SECRET=CAMBIAR_minimo_32_bytes
DEMO_MASTER_KEY=CAMBIAR
CRYPTO_SERVICE_SHARED_SECRET=CAMBIAR
IDENTITY_DEMO_ORGANIZATION=00000000-0000-4000-8000-0000000000a1

# MySQL
MYSQL_ROOT_PASSWORD=CAMBIAR
IDENTIDAD_DB_PASSWORD=CAMBIAR
TENENCIA_DB_PASSWORD=CAMBIAR
CATALOGOS_DB_PASSWORD=CAMBIAR
CRIPTOGRAFIA_DB_PASSWORD=CAMBIAR
PACIENTES_DB_PASSWORD=CAMBIAR
PROFESIONALES_DB_PASSWORD=CAMBIAR
HISTORIAL_DB_PASSWORD=CAMBIAR
DOCUMENTAL_DB_PASSWORD=CAMBIAR
FACTURACION_DB_PASSWORD=CAMBIAR
NOTIFICACIONES_DB_PASSWORD=CAMBIAR
MARKETING_DB_PASSWORD=CAMBIAR
AUDITORIA_DB_PASSWORD=CAMBIAR

# Postgres (ms-agenda y Keycloak)
AGENDA_DB_PASSWORD=CAMBIAR
KEYCLOAK_DB_PASSWORD=CAMBIAR

# Keycloak
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=CAMBIAR
KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET=CAMBIAR
KEYCLOAK_ENABLED=true

# MinIO (documentos)
MINIO_ROOT_USER=medicalsys
MINIO_ROOT_PASSWORD=CAMBIAR_minimo_8_caracteres
MINIO_BUCKET=medicalsys-documents

# Gateway: origenes del frontend separados por coma, p. ej. https://medicalsys.vercel.app
GATEWAY_ALLOWED_ORIGINS=CAMBIAR

# Datos de demostracion (para la presentacion)
AUDITORIA_DEMO_SEED=true

# Notificaciones: mock no envia nada real. Para WhatsApp real: meta + credenciales.
NOTIFICATION_PROVIDER=mock
WHATSAPP_MANUAL_SEND_ENABLED=false
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_VERIFY_TOKEN=CAMBIAR
WHATSAPP_APP_SECRET=
```

`IMAGE_REGISTRY` e `IMAGE_TAG` no van en el secreto: el workflow los agrega al `.env` del VPS
en cada despliegue.

## Volver a una version anterior

En **Actions**, abre la ejecucion del commit bueno y pulsa **Re-run all jobs**. Las imagenes
de ese commit siguen en GHCR.
