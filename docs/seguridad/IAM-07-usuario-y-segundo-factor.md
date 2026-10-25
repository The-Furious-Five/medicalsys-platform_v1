# IAM-07 · Nombre de usuario estándar y verificación en dos pasos

Este documento fija dos reglas de acceso a MedicalSys y explica en qué normas se apoyan:

1. Cada persona inicia sesión con un **nombre de usuario estándar**, generado a partir de sus nombres al crear la cuenta y que nunca cambia.
2. Todo ingreso exige **dos factores**: la contraseña (algo que se sabe) y un código de 6 dígitos de una aplicación autenticadora como Google Authenticator (algo que se tiene). El primer ingreso registra esa aplicación con un código QR.

La pantalla de acceso es la misma en cualquier entorno, incluida la demostración: no muestra cuentas de ejemplo, contraseñas ni avisos de «entorno de pruebas».

---

## 1. Estándar del nombre de usuario

### 1.1 Formato

```
nombre.apellido[.i][n]
```

| Parte | Regla |
|---|---|
| `nombre` | Primer nombre de pila, completo. |
| `apellido` | Primer apellido completo. Si es compuesto, sus palabras van juntas: «San Martín» → `sanmartin`. |
| `.i` | Inicial del segundo apellido, solo si existe. |
| `n` | Número de desempate (2, 3, …) solo si el usuario ya existe. |

**Normalización.** Todo en minúsculas ASCII. Se quitan las tildes y la diéresis (á → a, ü → u), la ñ pasa a n y desaparecen los apóstrofos, guiones y espacios. Las partículas `de`, `del`, `la`, `las`, `los`, `y`, `e`, `da`, `das`, `do`, `dos`, `di`, `van`, `von` y `der` no forman parte del usuario, pero se conservan en el nombre completo.

**Largo.** El nombre se recorta a 15 caracteres y el apellido a 19, así que el total, con el desempate, nunca supera 40. En la base, la columna `username` es única y tiene una restricción `CHECK` con el patrón `^[a-z][a-z0-9.]{2,39}$`.

| Persona | Usuario |
|---|---|
| Ricardo Salazar Peredo | `ricardo.salazar.p` |
| Lucía Quispe de Rojas | `lucia.quispe.r` |
| María José Núñez del Castillo | `maria.nunez.c` |
| Juan Pérez (un solo apellido) | `juan.perez` |
| Iñaki de la Cruz O'Brien | `inaki.cruz.o` |
| Un segundo Ricardo Salazar Peredo | `ricardo.salazar.p2` |

Implementación: `services/ms-identidad/.../service/UsernameGenerator.java`, con pruebas en `UsernameGeneratorTest`.

### 1.2 Cómo se crea y por qué no se edita

- **Lo genera el sistema.** Quien da de alta la cuenta escribe *Nombres*, *Primer apellido* y *Segundo apellido* (este último, opcional). El usuario se calcula en ms-identidad y se muestra junto con la contraseña temporal para entregarlos a la persona. Nadie lo elige ni lo escribe a mano.
- **No cambia nunca.** No hay pantalla ni endpoint que lo modifique. La entidad lo mapea con `updatable = false`, así que Hibernate no lo incluye en ningún `UPDATE`. Si la persona cambia de nombre o de correo, conserva el usuario.
- **No se reutiliza.** Una cuenta dada de baja se deshabilita, no se borra, así que su usuario sigue ocupado y una persona nueva con el mismo nombre recibe el siguiente número.
- **Cuentas anteriores.** Las cuentas creadas antes de este cambio recibieron su usuario al arrancar el servicio (`UsernameBackfill`), a partir del nombre completo que ya tenían: los dos últimos bloques se toman como apellidos.
- **Dos identificadores con funciones distintas.**
  - *User ID* `MS-AA-NNNNN` (IAM-04): no lleva datos personales y es el que identifica a la persona en la bitácora y en los registros administrativos.
  - *Usuario*: es el que la persona escribe para entrar.
  - El correo queda solo para avisos: ya no sirve para iniciar sesión.

### 1.3 Qué protege y qué no

El usuario **no es un secreto**. NIST SP 800-63B no le asigna esa función, y un formato predecible como `nombre.apellido` es fácil de adivinar. Lo que protege la cuenta es la combinación de estas medidas:

- **Mensajes que no revelan nada.** Un usuario inexistente y una contraseña incorrecta reciben la misma respuesta: «Usuario o contraseña incorrectos».
- **Bloqueo.** La cuenta se bloquea al tercer intento fallido de contraseña (Keycloak, IAM-02/IAM-05) y tras demasiados códigos incorrectos (sección 2.3).
- **Segundo factor obligatorio.** Ni con la contraseña correcta se entra sin el código del teléfono.

El estándar sí aporta otras cosas:

- **Identificación única y consistente.** Ayuda a la trazabilidad de la bitácora y al control de accesos.
- **Menos exposición del correo.** El correo deja de ser la llave de entrada y no aparece en la pantalla de acceso.
- **Validación previa.** La contraseña no puede contener el usuario: lo verifica ms-identidad además de Keycloak.

---

## 2. Verificación en dos pasos (TOTP)

### 2.1 Flujo

```
Usuario + contraseña ──► Keycloak valida (política, vencimiento, bloqueo al 3er intento)
        │
        ├─ contraseña temporal o vencida ──► 403 PASSWORD_CHANGE_REQUIRED ──► define la nueva
        │
        └─ contraseña válida ──► 202 + desafío (token de un solo uso, 5 minutos)
                 │
                 ├─ MFA_ENROLLMENT_REQUIRED (primer ingreso o tras un reinicio)
                 │      QR + clave manual ──► primer código ──► se activa y se abre la sesión
                 │
                 └─ MFA_REQUIRED ──► código de 6 dígitos ──► se abre la sesión
```

Una contraseña correcta **no abre sesión ni entrega token**: solo da acceso al paso del código. El JWT de MedicalSys y la sesión en Redis recién existen después de un código válido. El evento `LOGIN_SUCCESS` se registra en ese momento.

En el primer ingreso de una cuenta nueva, los pasos son estos:

1. Escribir el usuario y la contraseña temporal que entregó el OSI o el administrador.
2. Definir una contraseña propia, que debe cumplir la política de IAM-02.
3. Escanear el QR con Google Authenticator, o cargar la clave a mano.
4. Escribir el primer código de 6 dígitos.

### 2.2 Parámetros

| Parámetro | Valor | Motivo |
|---|---|---|
| Algoritmo | TOTP, RFC 6238 (sobre HOTP, RFC 4226), HMAC-SHA1 | Es el que entienden Google Authenticator, Microsoft Authenticator, FreeOTP y otras. |
| Dígitos y período | 6 dígitos, 30 segundos | Valores por defecto de RFC 6238 y de las aplicaciones. |
| Secreto | 160 bits aleatorios (`SecureRandom`) | El largo que recomienda RFC 4226 §4. |
| Tolerancia de reloj | ±1 paso (±30 s) | RFC 6238 §5.2. |
| Desafío | Token opaco de 256 bits, 5 min, en Redis solo su SHA-256 | No es una sesión y sirve solo para el paso del código. |
| URI | `otpauth://totp/MedicalSys:<usuario>?secret=…&issuer=MedicalSys&algorithm=SHA1&digits=6&period=30` | Formato de Google Authenticator. |

### 2.3 Protecciones

- **Cada código sirve una sola vez.** Se guarda el último paso aceptado (`mfa_last_used_step`) y un código igual o anterior se rechaza aunque siga vigente, así que interceptar un código no permite reutilizarlo.
- **Límite de intentos.** Cada desafío admite 5 códigos. Si se agotan, hay que volver a escribir la contraseña. Además, 10 códigos fallidos en 15 minutos bloquean la cuenta, igual que el tercer intento de contraseña. Al bloquearse:
  - se cierran sus sesiones;
  - queda un evento `ACCOUNT_LOCKED`;
  - solo un administrador la desbloquea, después de verificar la identidad de la persona.
- **El secreto se guarda cifrado.** Se usa AES-256-GCM, con el identificador de la cuenta como dato asociado. Una copia de la base no basta para generar códigos, y un secreto copiado a otra fila no descifra.
  - La llave se deriva de `IDENTITY_MFA_KEY` con HMAC-SHA256 y una etiqueta propia, así que nunca coincide con la que firma los tokens.
- **El QR se genera en el servidor** como SVG. El secreto no pasa por librerías ni servicios de terceros en el navegador, y se muestra una sola vez, en el primer ingreso.
- **La comparación de códigos es de tiempo constante** (`MessageDigest.isEqual`), así que la demora de la respuesta no revela cuántos dígitos acertó un intento.
- **Reinicio.** «Reiniciar acceso» (antes «Reiniciar contraseña») emite una contraseña temporal y además borra el segundo factor. Sirve, por ejemplo, cuando alguien perdió o cambió el teléfono: al entrar, la persona vuelve a registrar el QR.
  - Como esa acción permite tomar la cuenta, el procedimiento (PR-02) exige verificar la identidad por un canal distinto antes de ejecutarla.
- **Bitácora.**
  - `MFA_ENROLLED`: registro del segundo factor.
  - `MFA_FAILED`: cada código incorrecto.
  - `ACCOUNT_LOCKED`: bloqueo de la cuenta.
  - `LOGIN_SUCCESS`: solo con los dos factores.
  - `PASSWORD_RESET`: incluye `mfaReset` para saber si el reinicio borró un segundo factor.
  - Ningún evento lleva contraseñas, secretos ni códigos.

Implementación:
- `security/mfa/`: `Totp`, `Base32`, `MfaSecretCipher`, `MfaChallengeStore` y `QrCodeSvg`.
- `service/MfaService`.
- Endpoints `POST /api/v1/identity/auth/login`, `/auth/mfa/enrollment`, `/auth/mfa/activate` y `/auth/mfa/verify`.
- Migración `V12__username_and_totp.sql`.
- Pruebas: `TotpTest` (con los vectores del Apéndice B de RFC 6238), `MfaSecretCipherTest`, `MfaServiceTest` y `AuthServiceTests`.

---

## 3. Fundamentos normativos

| Control de MedicalSys | Norma o referencia | Qué pide |
|---|---|---|
| Dos factores en cada ingreso | **NIST SP 800-63B**, nivel AAL2 | Dos factores distintos. Una contraseña más un autenticador OTP cumple. |
| | **ISO/IEC 27001:2022**, control A.8.5 *Autenticación segura*, e ISO/IEC 27002:2022 §8.5 | Autenticación robusta; la guía recomienda varios factores para sistemas críticos o datos sensibles. |
| | **OWASP ASVS 4.0.3**, capítulo V2 | Requisitos de autenticación por niveles; el nivel 2 corresponde a aplicaciones con datos sensibles. |
| Código de un solo uso, con vida limitada | **NIST SP 800-63B**, verificadores OTP | Un OTP basado en tiempo se acepta una sola vez dentro de su período de validez. |
| | **OWASP ASVS 4.0.3**, 2.8.1 y 2.8.4 | El TOTP tiene una vida definida y no se puede reutilizar dentro de ella. |
| | **RFC 6238** y **RFC 4226** | Algoritmo, ventana de tolerancia y secreto de al menos 128 bits (se recomiendan 160). |
| Límite de intentos y bloqueo | **NIST SP 800-63B**, limitación de intentos (*rate limiting*) | Limitar los intentos fallidos consecutivos. |
| | **RFC 4226** §7.3 | Limitar los intentos de verificación de códigos. |
| | **OWASP ASVS 4.0.3**, 2.2.1 | Controles contra la automatización de intentos. |
| Secreto cifrado y protegido | **OWASP ASVS 4.0.3**, 2.8.2 | Las llaves simétricas que verifican OTP deben estar muy protegidas. |
| | **ISO/IEC 27001:2022**, control A.8.24 *Uso de criptografía* | Uso adecuado de la criptografía. |
| Identificador único por persona, que no cambia ni se reutiliza | **ISO/IEC 27001:2022**, control A.5.16 *Gestión de identidades* | Identidades únicas a lo largo de todo su ciclo de vida. |
| | **HIPAA Security Rule**, 45 CFR §164.312(a)(2)(i) | Identificación única de usuario. Es una regla de EE. UU. para sistemas con información de salud; aquí se toma como buena práctica, no como obligación legal. |
| Entrega de la contraseña temporal y del primer ingreso, y reinicio con verificación de identidad | **ISO/IEC 27001:2022**, control A.5.17 *Información de autenticación* | Asignación y gestión controlada de la información de autenticación. |
| | **OWASP**, *Multifactor Authentication Cheat Sheet* | Restablecer un segundo factor exige verificar a la persona. |
| | **PR-02** (procedimiento interno) | Verificar a la persona por un canal distinto antes de reiniciar. |
| Bitácora de cada desenlace | **ISO/IEC 27001:2022**, control A.8.15 *Registro de eventos* | Registrar eventos de seguridad. |
| | **LOG-01/LOG-02** (políticas internas) | Política propia de registro. |
| Respuestas que no revelan si existe el usuario | **OWASP**, *Authentication Cheat Sheet* | Mensajes de error genéricos en el inicio de sesión. |
| Pantalla de acceso sin cuentas ni contraseñas de ejemplo | **OWASP Top 10 2021**, A07 *Identification and Authentication Failures* | Evitar credenciales por defecto o conocidas. |
| | **ISO/IEC 27002:2022** §8.9 *Gestión de la configuración* | Retirar las credenciales y configuraciones por defecto. |

**Marco boliviano.** En Bolivia no hay todavía una ley general de protección de datos personales. Los referentes más cercanos son estos:

- la Constitución, que protege la intimidad y la privacidad y da la acción de protección de privacidad (art. 130);
- la Ley N.º 164 de Telecomunicaciones y TIC y su reglamento para el desarrollo de TIC (D.S. N.º 1793);
- los lineamientos de la AGETIC para los planes institucionales de seguridad de la información, que se apoyan en la familia ISO/IEC 27001/27002.

Las medidas de este documento siguen esos estándares internacionales. Antes de citar artículos exactos en el informe, conviene confirmarlos con la fuente oficial vigente.

---

## 4. Limitaciones y decisiones conscientes

- **TOTP no es resistente al phishing.** Una página falsa podría pedir la contraseña y el código y usarlos dentro de los 30 segundos. Lo que da resistencia al phishing son las llaves de acceso (*passkeys*, WebAuthn/FIDO2), que NIST SP 800-63B exige en el nivel AAL3. Serían el siguiente paso si el riesgo lo justifica.
- **No hay códigos de respaldo.** Si alguien pierde el teléfono, recupera el acceso con un reinicio del administrador, con verificación de identidad. Es más lento, pero evita que existan códigos impresos que se puedan robar.
- **Llave de cifrado.** Si `IDENTITY_MFA_KEY` no está definida, la llave se deriva de `JWT_SECRET` con otra etiqueta, para no romper un despliegue que todavía no la tiene; el servicio registra un aviso.
  - En producción se debe definir una llave propia de 32 bytes o más, guardada en la bóveda (KeePassXC).
  - No se debe cambiar una vez en uso: los secretos ya registrados dejarían de descifrarse y cada persona tendría que volver a escanear el QR.
- **Vencimiento de contraseñas a los 90 días.** La política IAM-02 lo mantiene por decisión del equipo (Excel de políticas), aunque la revisión 4 de NIST SP 800-63B ya no recomienda cambios periódicos sin indicios de compromiso. El segundo factor reduce el riesgo que motivaba esa regla, así que conviene revisarla.
- **Cuentas anteriores con nombres genéricos.** Una cuenta antigua cuyo nombre era genérico (por ejemplo «Administrador Demo») recibió un usuario genérico (`administrador.demo`). Como el usuario no cambia, la forma de corregirlo es dar de baja esa cuenta y crear una nueva con los nombres reales.

---

## 5. Operación

| Tarea | Cómo |
|---|---|
| Dar acceso a una persona | Crear la cuenta con nombres y apellidos. Se muestran una sola vez el usuario y la contraseña temporal. Entregarlos por un canal seguro (PR-01). |
| Teléfono perdido o cambiado | «Reiniciar acceso» sobre la cuenta, después de verificar la identidad (PR-02). |
| Cuenta bloqueada | «Desbloquear» o «Reactivar», después de verificar la identidad. También limpia el contador de códigos fallidos. |
| Saber el usuario de las cuentas de arranque (OSI y PLATFORM_ADMIN) | El servicio lo escribe en el log al arrancar, junto con el de cada cuenta que recibió usuario por primera vez: `docker compose logs ms-identidad \| grep "inicia sesion"`. El usuario no es secreto. |
| Llave de los secretos | `IDENTITY_MFA_KEY` en el `.env` o en el ENV_FILE del VPS (`openssl rand -base64 48`). |
| Pruebas automáticas | `bash scripts/test/e2e.sh`: registra y usa el segundo factor de cada rol con `oathtool`. La primera vez guarda en `.env` el secreto del OSI (`OSI_TOTP_SECRET`), que se puede cargar en Google Authenticator para usar esa cuenta en el navegador. Los scripts de IAM-03, IAM-04 e IAM-05 usan `services/ms-identidad/scripts/lib-mfa.sh`. |
