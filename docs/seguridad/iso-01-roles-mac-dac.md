# ISO-01 · Roles por función, OSI y control de acceso RBAC + MAC + DAC

> Esta página describe V9. La matriz vigente de 15 roles y los cambios de V11 están en
> [Matriz de Roles aprobada](matriz-roles/README.md).

Migración `services/ms-identidad/src/main/resources/db/migration/V9__iso_roles_mac_dac.sql`.
Matriz aprobada: `tmp/docs/anexos/Matriz_Roles_MedicalSys.xlsx` (hojas *Matriz de Roles*,
*Detalle permisos* y *Modelos de control*), generada con los mismos datos que la migración.

## Roles

| Rol | Alcance | Nivel MAC | Función (mínimo privilegio, ISO 27001 A.5.15 / A.8.2) |
|---|---|---|---|
| `OSI` | Plataforma | Restringido | Define roles y permisos, fija niveles MAC, asigna roles, revisa la bitácora. Sin datos clínicos ni de negocio. |
| `PLATFORM_ADMIN` | Plataforma | Confidencial | Organizaciones, planes y cuentas. Ya no gestiona roles ni ve historia clínica. |
| `AUDITOR` | Organización | Confidencial | Solo lectura de bitácora (consulta, verificación, exportación) y de cuentas y roles. |
| `ADMINISTRATOR` | Organización | Confidencial | Personal, sucursales, tarifario, agenda y profesionales. Ya no lee historia clínica ni bitácora. |
| `PHYSICIAN` | Organización | Restringido | Historia clínica completa, incluida la narrativa cifrada. |
| `NURSE` | Organización | Confidencial | Signos vitales, alergias, procedimientos, toma de muestras. Sin narrativa. |
| `LAB_TECHNICIAN` | Organización | Confidencial | Órdenes de estudio y resultados. |
| `RECEPTIONIST` | Organización | Confidencial | Admisión, citas, consentimientos y avisos. Sin historia clínica ni cobros. |
| `BILLING_CLERK` | Organización | Confidencial | Facturas, cupones y puntos. |
| `MARKETING_MANAGER` | Organización | Confidencial | Campañas, segmentos, cupones, anuncios. Sin padrón ni historia clínica. |
| `PATIENT` | Organización | Uso interno | Anuncios, saldo de fidelización y baja de comunicaciones. |

## Los tres modelos

- **RBAC.** El rol concede permisos `modulo.recurso.accion`; cada endpoint exige el suyo (RBAC-03/04).
- **MAC.** Cada permiso tiene `classification` (`PUBLIC < INTERNAL < CONFIDENTIAL < RESTRICTED`) y cada
  cuenta `clearance_level`. `IdentityMapper.activePermissionCodes` solo pone en el JWT los permisos que
  el nivel cubre, así que ningún servicio necesita cambios. Solo el OSI cambia un nivel
  (`PUT /api/v1/identity/users/{id}/clearance`, permiso `identity.clearance.manage`), nunca el suyo ni
  por encima del suyo; el cambio cierra las sesiones de la cuenta. Una cuenta nueva nace `INTERNAL`:
  el administrador le da el rol y el OSI la habilita (control dual).
- **DAC.** El paciente decide qué otra organización ve su ficha e historial confirmando un OTP de
  WhatsApp (`patients.access_grant.*`, ms-pacientes). En el frontend: ficha del paciente → *Accesos
  concedidos por el paciente*.

## El OSI otorga permisos

`identity.permission.grant` saca al OSI de la regla de no escalada de `RoleGovernance`: puede dar a un
rol permisos que él no ejerce (custodia sin uso). Las demás reglas siguen: nadie se asigna roles ni fija
su nivel, nadie edita un rol que tiene, y la plataforma no puede quedarse sin un OSI con nivel
`RESTRICTED`.

Primer OSI fuera de la demo: `OSI_SEED_ENABLED=true`, `OSI_EMAIL`, `OSI_PASSWORD` (otra persona que
`PLATFORM_ADMIN`). Activarlo en el primer arranque tras V9 y volver a desactivarlo.

## Segregación de funciones

PHYSICIAN–BILLING_CLERK · PLATFORM_ADMIN–PHYSICIAN · OSI–PLATFORM_ADMIN · OSI–ADMINISTRATOR ·
OSI–PHYSICIAN · AUDITOR–ADMINISTRATOR · AUDITOR–OSI · AUDITOR–PLATFORM_ADMIN ·
MARKETING_MANAGER–PHYSICIAN · MARKETING_MANAGER–NURSE.

`ADMINISTRATOR` ya no se delega a sí mismo: asigna PHYSICIAN, NURSE, LAB_TECHNICIAN, RECEPTIONIST,
BILLING_CLERK, MARKETING_MANAGER y PATIENT. Los demás roles los asigna la plataforma o el OSI.

## Bitácora (LOG-02/LOG-04)

- **Todo queda asentado.** El gateway (`GatewayAuditor`) registra en ms-auditoria cada escritura
  (POST/PUT/PATCH/DELETE) de cualquier servicio, cada lectura de pacientes, historia clínica y documentos,
  cada 403 (`ACCESS_DENIED`) y cada uso de un token con la sesión cerrada (`REVOKED_SESSION_USED`), con
  actor, roles, IP, servicio y desenlace. ms-identidad audita lo suyo con más detalle (login, ABM, roles,
  `CLEARANCE_CHANGED`), y ahora con el correo y los roles de quien actúa, así que una acción del OSI se ve
  como del OSI.
- **El OSI ve toda la plataforma.** `audit.log.read_platform` (V10, `RESTRICTED`, solo OSI): la pantalla de
  auditoría muestra todas las organizaciones o la que elija (`organizationId`). Para el resto pedir otra
  organización es un 403.
