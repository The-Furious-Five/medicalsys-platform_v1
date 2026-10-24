# Matriz de Roles aprobada

`Matriz de Roles.xlsx` es el archivo recibido el 24/10/2026, conservado sin modificar.
`matriz.json` conserva las 30 columnas, las X, los 15 nombres exactos y el modelo principal
MAC/DAC/RBAC de cada fila. También explica la traducción de cada recurso a los permisos existentes.

La migración **V11 de ms-identidad** actualiza los roles existentes conservando sus códigos y
asignaciones. Agrega `EXTERNAL_AUDITOR`, `MEDICAL_INTERN`, `BILLING_SUPERVISOR` y `DEVOPS`.
Reemplaza los permisos de los 15 roles base; los roles personalizados permanecen intactos.
No se modifican migraciones ya aplicadas ni se borran volúmenes.

| Código | Nombre exacto | Modelo |
|---|---|---|
| PLATFORM_ADMIN | Admin. de plataforma | MAC |
| OSI | Oficial Seguridad (OSI) | MAC |
| AUDITOR | Auditor interno | MAC |
| EXTERNAL_AUDITOR | Auditor externo | DAC |
| ADMINISTRATOR | Admin. de organización | MAC |
| PHYSICIAN | Médico | MAC |
| MEDICAL_INTERN | Pasante de medicina | DAC |
| NURSE | Enfermería | MAC |
| LAB_TECHNICIAN | Laboratorio | MAC |
| RECEPTIONIST | Recepción | MAC |
| BILLING_CLERK | Cajero 1 | RBAC |
| BILLING_SUPERVISOR | Cajero 2 | RBAC |
| MARKETING_MANAGER | Marketing | MAC |
| PATIENT | Paciente | DAC |
| DEVOPS | Tecnología (DevOps) | MAC |

## Interpretación de las X

El Excel autoriza recursos, sin detallar operaciones CRUD. La traducción está explícita en JSON:
las X habilitan las operaciones existentes de ese recurso según la función de la persona.
Las excepciones de mínimo privilegio son la ficha en lectura para personal clínico y caja,
el expediente en lectura para el pasante y la bitácora en lectura para auditoría externa.
El pasante sí registra signos vitales y alergias, pero no abre encuentros, firma ni lee narrativa.

`dependencies` documenta únicamente lecturas necesarias para selectores (profesional, sucursal,
prestación, disponibilidad o catálogo). No concede escrituras en columnas sin X.
Leer un documento o una plantilla para usarlo no habilita su administración.

- **MAC:** el rol concede permisos y el nivel de la cuenta filtra los que pueden ir al JWT.
  Solo el OSI administra niveles. El alta de personal requiere que después el OSI habilite
  el nivel apropiado; el administrador del centro no puede subirlo.
- **DAC:** las asignaciones de auditor externo y pasante son concesiones explícitas,
  revocables, con concedente y fechas en `user_role`; no dependen de un nivel MAC.
  El paciente concede/revoca el acceso a sus propios datos mediante OTP. El servidor
  resuelve su ficha por `user_account_public_id`, nunca por un identificador enviado por el cliente.
- **RBAC:** Cajero 1 emite y opera cupones/puntos; Cajero 2 consulta las facturas necesarias
  para anularlas. No se filtran por nivel MAC. Los dos roles son incompatibles, y una
  persona no puede anular una factura que emitió aunque se le cambie el rol después.

El OSI conserva la lectura de auditoría de todas las organizaciones (`audit.log.read_platform`, V10)
como parte de Bitácora, y la gestión de roles/permisos dentro de «Cambio perfil». El administrador
solo tiene «Altas»: `identity.role.assign_initial` permite asignar un rol operativo delegado
al crear una cuenta; no permite cambiar roles de cuentas existentes ni dar bajas.
El administrador de plataforma solo tiene organización, sucursales/tarifario y suscripción.

## Funciones completadas

- `POST /api/v1/facturas/{invoiceId}/anulacion`: permiso `billing.invoice.void`, motivo
  obligatorio (1–4), aislamiento por organización, bloqueo de fila y registro de actor/fecha.
  Conserva número, detalle, importe y etiqueta demo. La implementación funciona con el
  adaptador fiscal simulado del proyecto; la integración real con SIAT sigue fuera del MVP.
- `GET /api/v1/patients/me/access-grants`: solicitudes de acceso a la ficha del paciente autenticado.
- `POST /api/v1/patients/me/access-grants/{grantId}/confirmacion`: OTP para autorizar.
- `POST /api/v1/patients/me/access-grants/{grantId}/revocacion`: revocación por su dueño.
- Auditoría: solo el auditor interno exporta. El externo consulta sin verificar/exportar.
- Interfaz: muestra nombres/modelos de la matriz, separa emisión/anulación y consulta/
  exportación, usa anuncios vigentes para pacientes y no consulta historial/facturas
  desde el inicio o la ficha si la cuenta carece del permiso.

## Infraestructura (Tecnología / DevOps)

`DEVOPS` tiene exclusivamente `infrastructure.server.manage` e
`infrastructure.database.manage`, clasificados RESTRICTED. No recibe funciones clínicas,
comerciales ni administración de usuarios de MedicalSys.

Los servidores y motores de base de datos son recursos externos al API de negocio.
Los permisos del catálogo **no crean cuentas SSH, credenciales de base de datos ni acceso
al proveedor de infraestructura**. Su asignación efectiva debe hacerse en esos sistemas
con la cuenta técnica individual correspondiente. No se expone una consola SQL o shell
por HTTP ni se reutiliza el rol de administrador de plataforma para ello.

## Aplicación y comprobación

1. Actualizar juntos ms-identidad, ms-gateway, ms-pacientes, ms-facturacion y el frontend;
   recompilar los demás servicios que reconocen los códigos de roles para trazabilidad.
2. Arrancar normalmente: Flyway aplica V11 de identidad y V3 de facturación sin borrar datos.
3. Volver a iniciar sesión. El espacio Redis `identity:session:v11:` invalida los tokens
   emitidos por la política anterior, sin borrar claves ajenas. Gateway e identidad deben
   desplegarse juntos.
4. Para demostración, activar `IDENTITY_DEMO_USERS_ENABLED=true`: hay cuentas para los
   15 roles, con contraseña temporal individual generada por Keycloak. Para usar una cuenta,
   el OSI reinicia su contraseña y la persona define la suya. El paciente
   `paciente@medicalsys.demo` queda vinculado a una ficha solo si se incluye `db/demo`
   en ms-pacientes. El seeder no aumenta niveles de cuentas demo ya existentes.

```sh
python3 scripts/ci/check_role_matrix.py
python3 scripts/ci/check_permissions.py
python3 scripts/ci/check_migrations.py
cd services/ms-identidad && ./mvnw clean test
# Igualmente: ms-gateway, ms-pacientes y ms-facturacion.
# En medicalsys_frontend: npm ci && npm run check
```

`check_role_matrix.py` compara el SHA-256 del Excel, sus nombres/modelos/X, el catálogo,
las dependencias de lectura y el SQL generado. CI ejecuta este control. Para revisar
una nueva traducción antes de aplicar V11: `python3 scripts/ci/check_role_matrix.py --write`.
Una vez aplicada V11, cualquier cambio posterior necesita otra migración.
