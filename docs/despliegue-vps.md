# Despliegue en el VPS (CI/CD)

Workflow: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml).

Se ejecuta en cada push a `main` (salvo cambios solo de documentacion) y tambien a mano desde
**Actions → CI/CD - Deploy al VPS → Run workflow**. Cada ejecucion:

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

`DEMO_MASTER_KEY` tampoco se cambia nunca despues del primer despliegue: de ella se derivan las
llaves con las que se cifra la narrativa clinica, y con otra ya no se puede descifrar.

```dotenv
# Seguridad
JWT_SECRET=CAMBIAR_minimo_32_bytes
# Segundo factor (IAM-07): llave de los secretos TOTP, 32 bytes o mas (openssl rand -base64 48).
# Definirla antes del primer ingreso y no cambiarla despues: las aplicaciones ya registradas
# dejarian de servir y cada persona tendria que volver a escanear el QR.
IDENTITY_MFA_KEY=CAMBIAR
DEMO_MASTER_KEY=CAMBIAR
CRYPTO_SERVICE_SHARED_SECRET=CAMBIAR
IDENTITY_DEMO_ORGANIZATION=00000000-0000-4000-8000-0000000000a1

# Cuentas demo del personal del centro, para la presentacion. Se entra con el usuario estandar
# (nombre.apellido.inicial, docs/seguridad/IAM-07-usuario-y-segundo-factor.md), no con el correo,
# y con el codigo de Google Authenticator: el primer ingreso registra el QR.
# No llevan contrasena aqui: cada una nace con una temporal aleatoria propia que nadie ve. Para usar
# una, el OSI entra, pulsa «Reiniciar contrasena» sobre esa cuenta y se la pasa a la persona, que
# elige la suya en el primer ingreso (PR-02). Ninguna cuenta comparte contrasena con otra.
IDENTITY_DEMO_USERS_ENABLED=true
# Solo si el servidor ya tenia cuentas demo con la contrasena comun de versiones anteriores:
# true durante UN despliegue (les da una temporal nueva y cierra sus sesiones) y luego false.
IDENTITY_DEMO_ROTATE_PASSWORDS=false

# Cuenta de plataforma: opera organizaciones, planes y cuentas (sin acceso clinico ni gestion de
# permisos desde ISO-01). Su contrasena tambien debe cumplir la politica de IAM-02.
PLATFORM_ADMIN_SEED_ENABLED=true
PLATFORM_ADMIN_EMAIL=CAMBIAR
PLATFORM_ADMIN_PASSWORD=CAMBIAR

# Oficial de Seguridad de la Informacion (ISO-01): el unico rol que crea roles, otorga permisos y fija
# niveles MAC, necesario para la matriz de accesos (RBAC-02/RBAC-05). Debe ser OTRA persona que la de
# plataforma (roles incompatibles).
OSI_SEED_ENABLED=true
OSI_EMAIL=CAMBIAR
OSI_PASSWORD=CAMBIAR

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

# MinIO (documentos)
MINIO_ROOT_USER=medicalsys
MINIO_ROOT_PASSWORD=CAMBIAR_minimo_8_caracteres
MINIO_BUCKET=medicalsys-documents

# Gateway: origenes del frontend separados por coma (nunca *). El frontend del VPS:
GATEWAY_ALLOWED_ORIGINS=https://medicalsys.los5furiosos.cloud

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

## Frontend (repo medicalsys_frontend)

El frontend se despliega con su propio workflow (`Deploy frontend`) en `/opt/medicalsys-frontend`, con
Caddy publicando 80/443 y el dominio con HTTPS. Se une a la red `medicalsys-interna` que crea este
compose, asi que **el backend se despliega primero**. Ese repo necesita sus propios secretos
`VPS_HOST`, `VPS_USER` y `VPS_SSH_KEY` (o secretos de organizacion), y el usuario de deploy tiene que
poder escribir en `/opt/medicalsys-frontend`:

```bash
sudo mkdir -p /opt/medicalsys-frontend && sudo chown deploy: /opt/medicalsys-frontend
```

## Volver a una version anterior

En **Actions**, abre la ejecucion del commit bueno y pulsa **Re-run all jobs**. Las imagenes
de ese commit siguen en GHCR.

## Despliegue desde la computadora (sin GitHub)

`scripts/deploy/deploy-local.sh` construye las imagenes con el codigo tal como esta en disco (aunque no
este commiteado), las sube al VPS por SSH con la llave de `deploy` y levanta los servicios. El `.env` del
VPS no se toca y el proximo despliegue de GitHub reemplaza lo subido.

```bash
bash scripts/deploy/deploy-local.sh ms-identidad ms-gateway   # solo esos microservicios
bash scripts/deploy/deploy-local.sh todos                      # los 14 (sube varios GB: tarda)
bash scripts/deploy/deploy-local.sh --frontend                 # el frontend de ../medicalsys_frontend
bash scripts/deploy/deploy-local.sh --levantar                 # no construye: arranca lo que este caido
```

La llave `~/.ssh/los5furiosos-deploy` tiene frase de paso. Para no escribirla en cada despliegue, se
carga una sola vez en el agente SSH de Windows, que la guarda cifrada con tu usuario de Windows y la
recuerda entre reinicios. En PowerShell **como administrador** (solo la primera vez):

```powershell
Get-Service ssh-agent | Set-Service -StartupType Automatic
Start-Service ssh-agent
```

Despues, en una PowerShell normal (pide la frase de paso una vez):

```powershell
ssh-add $HOME\.ssh\los5furiosos-deploy
ssh-add -l
```

El script usa el `ssh` de Windows, que consulta ese agente: ya no pide la frase. Para quitarla del
agente: `ssh-add -d $HOME\.ssh\los5furiosos-deploy`.

Si un `docker compose up` en el VPS se corta a mitad (por ejemplo, se cerro la sesion SSH mientras
esperaba a Keycloak), los servicios quedan en estado «Created» y el login responde con un error generico.
`--levantar` los arranca con `nohup`, asi que ya no depende de que la conexion siga abierta.
