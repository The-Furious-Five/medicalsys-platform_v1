# Keycloak (IAM-01)

Herramienta externa de identidad elegida en el kickoff (C-01). MedicalSys valida las
credenciales contra Keycloak y sigue emitiendo su propio JWT con roles y permisos
(IAM-03), así que el resto de la plataforma no cambia.

| Pieza | Valor |
|---|---|
| Imagen | `quay.io/keycloak/keycloak:26.4` (26.4.7 al 12/10/2026) |
| Base | `keycloak` en el Postgres de la plataforma; la crean `keycloak-db-init` y `create-database.sh` |
| Realm | `medicalsys`, importado desde `realm-medicalsys.json` al arrancar |
| Cliente | `ms-identidad`, confidencial, con Direct Access Grants y service account |
| Service account | `realm-management`: `manage-users`, `view-users`, `query-users` |
| Consola | `http://localhost:${KEYCLOAK_HOST_PORT:-18080}/admin` (usuario `KEYCLOAK_ADMIN_USER`) |
| Token endpoint | `/realms/medicalsys/protocol/openid-connect/token` |

Ningún secreto se versiona. El realm toma el del cliente de
`${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET}`, y Compose falla al arrancar si falta
alguna de las variables de `.env.example`. Los valores reales se guardan en KeePassXC.

## Arrancar y probar

```bash
cp .env.example .env    # y completar las variables KEYCLOAK_*
docker compose up -d keycloak
set -a; source .env; set +a
bash infra/keycloak/smoke-token.sh
```

`smoke-token.sh` comprueba, sin dejar nada creado:

1. el cliente `ms-identidad` obtiene un token con `client_credentials`;
2. su service account crea un usuario por la Admin REST API (`manage-users`);
3. ese usuario obtiene un token con su contraseña (password grant), que es lo que usará
   `AuthService.login`;
4. una contraseña incorrecta devuelve `invalid_grant`.

## Cambiar la configuración del realm

`--import-realm` solo importa si el realm no existe. Después de editar
`realm-medicalsys.json` (por ejemplo, la política de contraseñas de IAM-02):

```bash
bash infra/keycloak/sync-realm.sh
```

Aplica la configuración de nivel realm sin tocar usuarios ni clientes.

## Política de contraseñas y bloqueo (IAM-02)

Valores de la hoja «Decisiones» del kickoff (C-01) y de PO-03, en `passwordPolicy` y en la
detección de fuerza bruta del realm:

| Decisión | Configuración en Keycloak |
|---|---|
| Longitud de 12 a 64 caracteres | `length(12)`, `maxLength(64)` |
| 1 mayúscula, 1 minúscula, 1 dígito y 1 carácter especial; los espacios se permiten | `upperCase(1)`, `lowerCase(1)`, `digits(1)`, `specialChars(1)` |
| No usar el usuario ni el correo | `notUsername`, `notEmail` |
| Lista negra de frases fáciles | `passwordBlacklist(medicalsys.txt)`: [`password-blacklists/medicalsys.txt`](password-blacklists/medicalsys.txt), 821 entradas en minúsculas (Keycloak compara sin distinguir mayúsculas) |
| Histórico de las últimas 5 | `passwordHistory(5)` |
| Vida útil de 90 días | `forceExpiredPasswordChange(90)` |
| Hash Argon2id | `hashAlgorithm(argon2)`: Keycloak guarda `type=id`, versión 1.3 y sal propia por credencial |
| Bloqueo al 3er intento, sin desbloqueo automático | `bruteForceProtected`, `failureFactor: 3`, `permanentLockout: true`, `maxTemporaryLockouts: 0` |

El token endpoint responde `Invalid user credentials` tanto a una contraseña incorrecta como a
una cuenta bloqueada: no revela si el usuario existe ni cuántos intentos le quedan.
ms-identidad distingue el bloqueo consultando
`/attack-detection/brute-force/users/{id}` con su service account (IAM-03).

Comprobación:

```bash
bash infra/keycloak/policy-check.sh
```

Recorre cada regla con una contraseña inválida, el histórico, el algoritmo del hash y el
bloqueo al 3er intento. La salida de referencia está en
[`evidencia-iam-02.txt`](evidencia-iam-02.txt).

## Decisión go/no-go (12/10/2026): GO

Keycloak arrancó con el realm importado sobre el Postgres existente, y el smoke test pasó
completo (salida en [`evidencia-iam-01.txt`](evidencia-iam-01.txt)). El plan B (IAM-07,
políticas nativas en ms-identidad) no se activa.
