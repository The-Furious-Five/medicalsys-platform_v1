# DEP-01 (SINS-57) — Publicación del sistema por túnel de Cloudflare

**Estado al 12/10/2026: preparado en el repositorio, pendiente de ejecutar.** La publicación necesita el token del túnel de Cloudflare, las credenciales reales de la base de datos y una comprobación desde un teléfono; ninguna de las tres puede hacerse desde el repositorio. Este documento deja los pasos listos y marca qué evidencia falta.

## Qué quedó listo

- Servicio `cloudflared` en `compose.yaml`, detrás del perfil `publico`. No publica puertos: se conecta hacia Cloudflare desde dentro de la red `medicalsys-interna` y alcanza al gateway en `http://ms-gateway:8080`. Es compatible con HARD-01: el gateway sigue siendo el único punto de entrada.
- Variable `CLOUDFLARE_TUNNEL_TOKEN` en `.env.example` (vacía; el valor real va en la bóveda).
- El gateway ya envía `Strict-Transport-Security` y el resto de cabeceras de seguridad (HARD-04). El HTTPS lo termina Cloudflare.
- CORS por lista exacta: hay que agregar el origen público del frontend a `GATEWAY_ALLOWED_ORIGINS`.

## Pasos

1. En el panel de Cloudflare Zero Trust, abrir el túnel existente (Networks → Tunnels) y copiar su token.
2. En ese túnel, definir los *public hostnames*:

| Hostname público | Servicio de destino | Para qué |
|---|---|---|
| `api.<dominio>` | `http://ms-gateway:8080` | API de la plataforma |
| `app.<dominio>` | donde se sirva el frontend | Interfaz web |
| `auth.<dominio>` | `http://keycloak:8080` | Solo si se necesita la consola de Keycloak (IAM-03) |

3. En `.env` del servidor:

```
CLOUDFLARE_TUNNEL_TOKEN=<token del túnel, desde la bóveda>
GATEWAY_ALLOWED_ORIGINS=https://app.<dominio>
IDENTITY_DEMO_USERS_ENABLED=true
```

Las cuentas demo vienen apagadas por defecto (HARD-03). Se encienden aquí a propósito porque la rúbrica pide usuarios de prueba por rol; es una decisión para el entorno de evaluación, no el valor por defecto.

4. Levantar con el perfil:

```
docker compose --profile publico up -d
docker compose logs cloudflared --tail 20
```

El túnel está arriba cuando el log muestra `Registered tunnel connection`.

5. Comprobar desde fuera de la red local (teléfono con datos móviles, sin Wi-Fi):

```
https://api.<dominio>/actuator/health        → {"status":"UP"}
https://app.<dominio>                        → pantalla de inicio de sesión
```

6. Verificar que el cierre de puertos se mantiene: desde el teléfono, `https://api.<dominio>:8088` no debe responder.

## Usuarios de prueba por rol

Las cuentas las siembra `DemoUserSeeder` cuando `IDENTITY_DEMO_USERS_ENABLED=true`. La contraseña es la de `IDENTITY_DEMO_PASSWORD`: definir una propia para el despliegue público y guardarla en la bóveda; aquí no se escribe.

| Rol | Cuenta | Nombre ficticio |
|---|---|---|
| ADMINISTRATOR | `admin@medicalsys.demo` | Patricia Loayza Bernal |
| RECEPTIONIST | `recepcion@medicalsys.demo` | Elena Cruz Apaza |
| PHYSICIAN | `doctor@medicalsys.demo` | Ricardo Salazar Peredo |
| BILLING_CLERK | `caja@medicalsys.demo` | Marcelo Villegas Ortuno |
| PLATFORM_ADMIN | `platform.admin@medicalsys.demo` | Solo con `PLATFORM_ADMIN_SEED_ENABLED=true` y contraseña propia |

Hay seis cuentas PHYSICIAN más, una por especialidad (pediatría, ginecología, cardiología, traumatología, nutrición y odontología).

## Pendiente para cerrar la tarea

- [ ] Token del túnel en la bóveda y en el `.env` del servidor.
- [ ] Credenciales reales de Aiven en el `.env` del servidor (el `.env` local tiene el host de ejemplo).
- [ ] Hostnames públicos configurados en el túnel.
- [ ] Depende de IAM-03 (login delegado a Keycloak): Keycloak todavía no está en `compose.yaml`.
- [ ] Contraseña de las cuentas demo cambiada y registrada en la bóveda.
- [ ] URL pública anotada en el informe.
- [ ] Captura de pantalla del acceso desde el teléfono con datos móviles.
