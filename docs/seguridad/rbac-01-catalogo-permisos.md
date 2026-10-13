# RBAC-01 — Catálogo granular de permisos por funcionalidad

**Issue:** SINS-11 · **Sección del informe:** 9.2 · **Migración:** `services/ms-identidad/src/main/resources/db/migration/V3__granular_permission_catalog.sql`
**Hoja endpoint → permiso (borrador de la matriz RBAC-06):** [`rbac-01-endpoints-permisos.csv`](rbac-01-endpoints-permisos.csv)

El catálogo define **169 permisos** en 12 módulos (168 en RBAC-01, más `clinical.narrative.read` de RBAC-03) y cubre los **275 endpoints** que exponen hoy los 13 microservicios (267 en RBAC-01, más los 8 de gestión de roles de RBAC-02). Se inventariaron leyendo cada `@*Mapping` de los controladores, no la documentación. `GET /api/v1/identity/permissions` lo devuelve completo y acepta `?module=` para filtrar.

## Convención

- Formato `modulo.recurso.accion`, en minúsculas, con `snake_case` cuando una parte tiene más de una palabra: `clinical.encounter.sign`, `scheduling.appointment.check_in`, `notifications.whatsapp.send`.
- El módulo es el prefijo del código y coincide con la columna `permission.module`. ms-notificaciones, ms-auditoría y ms-marketing no tenían módulo propio: se agregan `NOTIFICATIONS`, `AUDIT` y `MARKETING` al enum y al `CHECK`.
- `read` cubre listar y ver el detalle. Las acciones de negocio (firmar, anular, cancelar, canjear, lanzar…) tienen su propio permiso, aunque hoy las ejerzan los mismos roles: separarlas es lo que permite restringirlas después desde la API de roles (RBAC-02) sin tocar código.
- `is_sensitive` marca lo que expone datos clínicos o personales, o lo que cambia el acceso de otras personas. La matriz de RBAC-06 debería justificar cada asignación de estos permisos.

## Cobertura de los 275 endpoints

| Tipo de acceso | Endpoints | Criterio |
|---|---:|---|
| `PERMISO` | 212 | Endpoint de negocio en `/api/v1/**`: exige un permiso del catálogo. |
| `PUBLICO` | 16 | Los 13 `/ping`, `POST /auth/login` y el webhook de WhatsApp (`GET` de verificación y `POST` firmado por Meta). |
| `AUTENTICADO` | 3 | Autoservicio de la propia sesión: `GET /auth/me`, `POST /auth/logout`, `POST /auth/change-password`. Lo necesita cualquier usuario, incluido `PATIENT`. |
| `INTERNO` | 44 | `/internal/v1/**`: llamadas entre servicios que el gateway no publica. Su protección es de red (HARD-01), no de rol. |

Además, el catálogo reserva los permisos de 2 endpoints que todavía no existen pero cuyo código ya fija el plan: `identity.user.unlock` e `identity.user.reset` (IAM-05). Los de `identity.role.manage` ya existen desde RBAC-02 ([`rbac-02-gestion-de-roles.md`](rbac-02-gestion-de-roles.md)).

## Asignación inicial a roles

La asignación replica el control vigente para que RBAC-03 pueda cambiar `@RequiresRole` por `@RequiresPermission` sin cambiar quién puede hacer qué:

| Origen | Servicios | Cómo se asignó |
|---|---|---|
| `@RequiresRole` | historial, pacientes, facturación, tenencia, documental, auditoría, marketing | Exactamente los roles de la anotación de cada endpoint (comprobado de forma automática). |
| Regla del frontend | agenda, profesionales, catálogos | Estos servicios no controlan roles. Se replicó `medicalsys_frontend/src/app/core/auth/permissions.ts` (`viewSchedule`, `manageAvailability`, `manageResources`, `viewProfessionals`, `viewCatalogs`…). |
| Propuesta nueva | notificaciones, `identity.role.read`, IAM-05, RBAC-02 | Mínimo privilegio: recepción opera notificaciones y WhatsApp; plantillas y reglas, solo administración; `identity.role.manage` solo `PLATFORM_ADMIN`, porque los roles son globales y no de una organización. |

| Rol | Permisos |
|---|---:|
| PLATFORM_ADMIN (PA) | 168 |
| ADMINISTRATOR (AD) | 157 |
| RECEPTIONIST (RE) | 82 |
| PHYSICIAN (PH) | 79 |
| BILLING_CLERK (BC) | 17 |
| PATIENT (PT) | 4 |

### Cambios respecto al seed V2

- Los permisos gruesos `identity.user.write`, `scheduling.appointment.write`, `scheduling.resource.write`, `patients.patient.write`, `clinical.record.write`, `billing.invoice.write` y `administration.organization.manage` se eliminan y se reemplazan por los granulares. `SecurityConfig` de ms-identidad pasa a exigir `identity.user.create`, `identity.user.update` e `identity.user.change_status` donde antes pedía `identity.user.write`.
- `PATIENT` pierde `scheduling.appointment.read`: ms-agenda no filtra por paciente, así que con ese permiso un paciente vería la agenda completa de su organización.
- `BILLING_CLERK` pierde `patients.patient.read`: ms-pacientes nunca se lo permitió (`@RequiresRole` sin `BILLING_CLERK`). El frontend sí le muestra la sección Pacientes (`viewPatients`), y eso conviene corregirlo en RBAC-05.
- `ms-identidad` ahora exige `identity.role.read` para `GET /roles`, `/roles/{code}` y `/permissions`, que antes solo pedían sesión. Cualquier otra operación nueva bajo `/users/**` queda denegada hasta que se le asigne su permiso.

### Puntos a revisar en la matriz (RBAC-06)

Replicar el control vigente hace visibles decisiones que no son de mínimo privilegio. No se cambian aquí para no alterar la demo, pero la matriz debería justificarlas o corregirlas. La política de control de acceso las registra como desviaciones a resolver ([PO-02](politicas/PO-02.md), Anexo C.3):

1. **PLATFORM_ADMIN tiene todos los permisos**, incluidos los clínicos (`clinical.encounter.sign`, `clinical.diagnosis.create`…), porque figura en todos los `@RequiresRole`. Un operador de la plataforma no debería firmar actos médicos (segregación de funciones). Desde RBAC-02 se corrige por la API: un oficial de seguridad, con un rol propio que reúna los permisos de gestión, se los quita (ver [`rbac-02-gestion-de-roles.md`](rbac-02-gestion-de-roles.md)).
2. **ADMINISTRATOR lee la historia clínica** (`clinical.record.read`, `clinical.encounter.read`, `clinical.study_result.read`…) y puede abrir y anular encuentros. El seed V2 lo excluía de la historia clínica a propósito.
3. **RECEPTIONIST registra resultados de estudios** y consulta diagnósticos y recetas.
4. **`MARKETING_MANAGER`** figura en los `@RequiresRole` de ms-marketing pero no existe en ms-identidad, y LOG-04 menciona un rol `AUDITOR` que tampoco existe. Ambos se pueden crear con la API de RBAC-02 y asignarles los permisos `marketing.*` y `audit.log.*`.

## Tamaño del JWT

El token lleva el claim `permissions`. Con el catálogo granular, el JWT de `PLATFORM_ADMIN` pasa a medir **7115 bytes** y el de `ADMINISTRATOR`, 6676. El límite por defecto de Tomcat para todas las cabeceras es de 8 KB. Medido contra el servicio real a través del gateway, a `PLATFORM_ADMIN` le quedaban unos 500 bytes para el resto de cabeceras: una cookie de `localhost` lo deja en `400 Bad Request`, y el `X-Permissions` que agrega RBAC-03 lo supera seguro. Por eso los 14 servicios, gateway incluido, declaran `server.max-http-request-header-size: 32KB`.

## Hallazgo fuera de alcance

El gateway no enruta `/api/v1/consentimientos/**`: la ruta `documents-service` solo publica `/api/v1/documents/**`, así que los 10 endpoints de consentimientos y plantillas de ms-documental no se alcanzan desde el frontend. El catálogo ya les asigna permisos, pero hay que agregar la ruta en el gateway.

## Permisos por módulo

PA = PLATFORM_ADMIN · AD = ADMINISTRATOR · PH = PHYSICIAN · RE = RECEPTIONIST · BC = BILLING_CLERK · PT = PATIENT

### Identidad (ms-identidad) — 9 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `identity.user.read` | Consultar cuentas de usuario |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/identity/users`<br>`GET /api/v1/identity/users/{publicId}` |
| `identity.user.create` | Crear cuentas de usuario | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/identity/users` |
| `identity.user.update` | Modificar los datos de una cuenta de usuario | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/identity/users/{publicId}` |
| `identity.user.change_status` | Activar, desactivar o bloquear una cuenta de usuario | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/identity/users/{publicId}/status` |
| `identity.user.unlock` | Desbloquear una cuenta bloqueada por intentos fallidos (IAM-05) | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/identity/users/{publicId}/unlock` *(futuro)* |
| `identity.user.reset` | Reiniciar la contrasena de una cuenta con una temporal (IAM-05) | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/identity/users/{publicId}/reset-password` *(futuro)* |
| `identity.role.read` | Consultar roles y el catalogo de permisos |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/identity/permissions`<br>`GET /api/v1/identity/role-incompatibilities`<br>`GET /api/v1/identity/roles`<br>`GET /api/v1/identity/roles/{code}` |
| `identity.role.assign` | Asignar o revocar roles a una cuenta de usuario | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/identity/users/{publicId}/roles`<br>`DELETE /api/v1/identity/users/{publicId}/roles/{role}` |
| `identity.role.manage` | Crear, editar y desactivar roles y sus permisos (RBAC-02) | sí | ✓ |  |  |  |  |  | `POST /api/v1/identity/role-incompatibilities`<br>`DELETE /api/v1/identity/role-incompatibilities/{roleA}/{roleB}`<br>`POST /api/v1/identity/roles`<br>`DELETE /api/v1/identity/roles/{code}`<br>`PATCH /api/v1/identity/roles/{code}`<br>`PUT /api/v1/identity/roles/{code}/assignable-roles`<br>`PUT /api/v1/identity/roles/{code}/permissions` |

### Tenencia (ms-tenencia) — 19 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `tenancy.organization.create` | Dar de alta una organizacion cliente | sí | ✓ |  |  |  |  |  | `POST /api/v1/tenancy/organizacion` |
| `tenancy.organization.read` | Consultar los datos de la organizacion |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/tenancy/organizacion` |
| `tenancy.organization.update` | Modificar los datos de la organizacion |  | ✓ | ✓ |  |  |  |  | `PUT /api/v1/tenancy/organizacion` |
| `tenancy.organization.change_status` | Suspender o reactivar la organizacion | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/tenancy/organizacion/estado` |
| `tenancy.branch.create` | Crear sucursales |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/tenancy/sucursales` |
| `tenancy.branch.read` | Consultar sucursales |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/tenancy/sucursales`<br>`GET /api/v1/tenancy/sucursales/{branchId}` |
| `tenancy.branch.update` | Modificar sucursales |  | ✓ | ✓ |  |  |  |  | `PUT /api/v1/tenancy/sucursales/{branchId}` |
| `tenancy.branch.change_status` | Activar o desactivar sucursales |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/tenancy/sucursales/{branchId}/estado` |
| `tenancy.service_offering.create` | Crear prestaciones del tarifario |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/tenancy/prestaciones` |
| `tenancy.service_offering.read` | Consultar prestaciones del tarifario |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/tenancy/prestaciones`<br>`GET /api/v1/tenancy/prestaciones/{serviceOfferingId}` |
| `tenancy.service_offering.update` | Modificar prestaciones y precios del tarifario |  | ✓ | ✓ |  |  |  |  | `PUT /api/v1/tenancy/prestaciones/{serviceOfferingId}` |
| `tenancy.service_offering.change_status` | Cambiar la vigencia de una prestacion |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/tenancy/prestaciones/{serviceOfferingId}/vigencia` |
| `tenancy.subscription.read` | Consultar la suscripcion vigente |  | ✓ | ✓ |  |  | ✓ |  | `GET /api/v1/tenancy/suscripcion` |
| `tenancy.subscription.read_history` | Consultar el historial de suscripciones |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/tenancy/suscripcion/historial` |
| `tenancy.subscription.subscribe` | Contratar o cambiar el plan de suscripcion | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/tenancy/suscripcion` |
| `tenancy.subscription.change_status` | Suspender, reactivar o cancelar la suscripcion | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/tenancy/suscripcion/estado` |
| `tenancy.plan.read` | Consultar los planes de suscripcion |  | ✓ | ✓ |  |  | ✓ |  | `GET /api/v1/tenancy/planes` |
| `tenancy.usage.read` | Consultar el consumo del plan |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/tenancy/consumo` |
| `tenancy.usage.record` | Registrar consumo del plan |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/tenancy/consumo` |

### Catálogos (ms-catalogos) — 5 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `catalogs.icd10.read` | Consultar el catalogo CIE-10 |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/catalogs/cie10`<br>`GET /api/v1/catalogs/cie10/{code}` |
| `catalogs.medication.read` | Consultar el vademecum de medicamentos |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/catalogs/medications`<br>`GET /api/v1/catalogs/medications/{code}` |
| `catalogs.specialty.read` | Consultar especialidades medicas |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/catalogs/specialties` |
| `catalogs.study_type.read` | Consultar tipos de estudio |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/catalogs/study-types` |
| `catalogs.tax.read` | Consultar el catalogo tributario |  | ✓ | ✓ | ✓ | ✓ | ✓ |  | `GET /api/v1/catalogs/tax` |

### Pacientes (ms-pacientes) — 11 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `patients.patient.read` | Consultar y buscar datos administrativos del paciente |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/patients`<br>`GET /api/v1/patients/search`<br>`GET /api/v1/patients/{patientId}` |
| `patients.patient.create` | Registrar pacientes |  | ✓ | ✓ |  | ✓ |  |  | `POST /api/v1/patients` |
| `patients.patient.update` | Actualizar datos administrativos del paciente |  | ✓ | ✓ |  | ✓ |  |  | `PUT /api/v1/patients/{patientId}` |
| `patients.merge.create` | Fusionar registros duplicados de paciente | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/patients/{survivingPatientId}/merges` |
| `patients.merge.read` | Consultar el historial de fusiones de un paciente |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/patients/{survivingPatientId}/merges` |
| `patients.notification_preference.read` | Consultar preferencias de notificacion del paciente |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/patients/{patientId}/notification-preferences` |
| `patients.notification_preference.update` | Modificar preferencias de notificacion del paciente |  | ✓ | ✓ |  | ✓ |  |  | `PUT /api/v1/patients/{patientId}/notification-preferences/{channel}/{category}` |
| `patients.access_grant.read` | Consultar solicitudes de acceso a los datos de un paciente |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/patients/{patientId}/acceso/solicitudes` |
| `patients.access_grant.request` | Solicitar acceso a los datos de un paciente de otra organizacion | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/patients/{patientId}/acceso/solicitudes` |
| `patients.access_grant.confirm` | Confirmar una solicitud de acceso con el consentimiento del paciente | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/patients/{patientId}/acceso/solicitudes/{grantId}/confirmacion` |
| `patients.access_grant.revoke` | Revocar un acceso concedido a los datos de un paciente | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/patients/{patientId}/acceso/solicitudes/{grantId}/revocacion` |

### Profesionales (ms-profesionales) — 4 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `professionals.professional.read` | Consultar el directorio de profesionales de salud |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/professionals`<br>`GET /api/v1/professionals/{id}` |
| `professionals.professional.create` | Dar de alta profesionales de salud |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/professionals` |
| `professionals.professional.update` | Modificar datos de un profesional de salud |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/professionals/{id}` |
| `professionals.affiliation.change_status` | Activar o desactivar el vinculo de un profesional con la organizacion | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/professionals/{id}/organizations/{linkId}` |

### Agenda (ms-agenda) — 23 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `scheduling.appointment.read` | Consultar citas y su historial |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/scheduling/appointments`<br>`GET /api/v1/scheduling/appointments/{publicId}`<br>`GET /api/v1/scheduling/appointments/{publicId}/history` |
| `scheduling.appointment.create` | Agendar citas |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments` |
| `scheduling.appointment.confirm` | Confirmar citas |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/confirm` |
| `scheduling.appointment.check_in` | Registrar la llegada del paciente a la cita |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/check-in` |
| `scheduling.appointment.attend` | Iniciar y completar la atencion de una cita |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/complete`<br>`POST /api/v1/scheduling/appointments/{publicId}/start` |
| `scheduling.appointment.no_show` | Marcar una cita como inasistencia |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/no-show` |
| `scheduling.appointment.cancel` | Cancelar citas |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/cancel` |
| `scheduling.appointment.reschedule` | Reprogramar citas |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/appointments/{publicId}/reschedule` |
| `scheduling.availability.read` | Consultar disponibilidad y horarios libres |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/scheduling/availabilities`<br>`GET /api/v1/scheduling/availability/slots` |
| `scheduling.availability.create` | Definir reglas de disponibilidad de un profesional |  | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/scheduling/availabilities` |
| `scheduling.availability.deactivate` | Desactivar reglas de disponibilidad |  | ✓ | ✓ | ✓ |  |  |  | `DELETE /api/v1/scheduling/availabilities/{id}` |
| `scheduling.block.read` | Consultar bloqueos de agenda |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/scheduling/blocks` |
| `scheduling.block.create` | Bloquear franjas de agenda de un profesional |  | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/scheduling/blocks` |
| `scheduling.block.delete` | Eliminar bloqueos de agenda |  | ✓ | ✓ | ✓ |  |  |  | `DELETE /api/v1/scheduling/blocks/{id}` |
| `scheduling.reservation.read` | Consultar reservas de consultorios y recursos |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/scheduling/reservations` |
| `scheduling.reservation.create` | Reservar consultorios y recursos |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/reservations` |
| `scheduling.reservation.release` | Liberar una reserva de recurso |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/reservations/{id}/release` |
| `scheduling.reservation.mark_in_use` | Marcar una reserva de recurso como en uso |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/scheduling/reservations/{id}/in-use` |
| `scheduling.reservation.cancel` | Cancelar una reserva de recurso |  | ✓ | ✓ | ✓ | ✓ |  |  | `DELETE /api/v1/scheduling/reservations/{id}` |
| `scheduling.resource.read` | Consultar recursos fisicos |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/scheduling/resources`<br>`GET /api/v1/scheduling/resources/{publicId}` |
| `scheduling.resource.create` | Crear recursos fisicos |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/scheduling/resources` |
| `scheduling.resource.update` | Modificar recursos fisicos |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/scheduling/resources/{publicId}` |
| `scheduling.resource.change_status` | Cambiar el estado de un recurso fisico |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/scheduling/resources/{publicId}/status` |

### Historia clínica (ms-historial) — 35 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `clinical.record.create` | Abrir el expediente clinico de un paciente | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/expedientes` |
| `clinical.record.read` | Consultar expedientes clinicos | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/expedientes`<br>`GET /api/v1/expedientes/{recordId}` |
| `clinical.record.share` | Configurar el compartido del expediente con otras organizaciones | sí | ✓ | ✓ | ✓ |  |  |  | `PATCH /api/v1/expedientes/{recordId}/compartir` |
| `clinical.record.change_status` | Cerrar o reabrir un expediente clinico | sí | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/expedientes/{recordId}/estado` |
| `clinical.allergy.read` | Consultar alergias del paciente | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/expedientes/{recordId}/alergias` |
| `clinical.allergy.create` | Registrar alergias del paciente | sí | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/expedientes/{recordId}/alergias` |
| `clinical.allergy.update` | Modificar alergias del paciente | sí | ✓ | ✓ | ✓ |  |  |  | `PATCH /api/v1/alergias/{allergyId}` |
| `clinical.background.read` | Consultar antecedentes medicos | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/expedientes/{recordId}/antecedentes` |
| `clinical.background.create` | Registrar antecedentes medicos | sí | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/expedientes/{recordId}/antecedentes` |
| `clinical.background.deactivate` | Desactivar antecedentes medicos | sí | ✓ | ✓ | ✓ |  |  |  | `DELETE /api/v1/antecedentes/{backgroundId}` |
| `clinical.encounter.read` | Consultar encuentros clinicos | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/encuentros`<br>`GET /api/v1/encuentros/{encounterId}` |
| `clinical.encounter.open` | Abrir un encuentro clinico | sí | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/encuentros` |
| `clinical.encounter.update_notes` | Redactar la nota clinica del encuentro | sí | ✓ |  | ✓ |  |  |  | `PUT /api/v1/encuentros/{encounterId}/notas` |
| `clinical.encounter.sign` | Firmar un encuentro clinico | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/firma` |
| `clinical.encounter.void` | Anular un encuentro clinico | sí | ✓ | ✓ | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/anulacion` |
| `clinical.addendum.read` | Consultar adendas de un encuentro firmado | sí | ✓ | ✓ | ✓ |  |  |  | `GET /api/v1/encuentros/{encounterId}/addendums` |
| `clinical.addendum.create` | Agregar adendas a un encuentro firmado | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/addendums` |
| `clinical.diagnosis.read` | Consultar diagnosticos | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/encuentros/{encounterId}/diagnosticos` |
| `clinical.diagnosis.create` | Registrar diagnosticos | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/diagnosticos` |
| `clinical.diagnosis.update` | Modificar diagnosticos | sí | ✓ |  | ✓ |  |  |  | `PATCH /api/v1/diagnosticos/{diagnosisId}` |
| `clinical.procedure.read` | Consultar procedimientos realizados | sí | ✓ | ✓ | ✓ |  |  |  | `GET /api/v1/encuentros/{encounterId}/procedimientos` |
| `clinical.procedure.create` | Registrar procedimientos realizados | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/procedimientos` |
| `clinical.prescription.read` | Consultar recetas | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/encuentros/{encounterId}/recetas`<br>`GET /api/v1/recetas/{prescriptionId}` |
| `clinical.prescription.create` | Emitir recetas | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/recetas` |
| `clinical.prescription.change_status` | Cambiar el estado de una receta | sí | ✓ | ✓ | ✓ | ✓ |  |  | `PATCH /api/v1/recetas/{prescriptionId}/estado` |
| `clinical.prescription.attach_document` | Adjuntar el documento firmado de una receta | sí | ✓ | ✓ | ✓ | ✓ |  |  | `PUT /api/v1/recetas/{prescriptionId}/documento` |
| `clinical.study_order.read` | Consultar ordenes de estudio | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/encuentros/{encounterId}/ordenes-estudio`<br>`GET /api/v1/ordenes-estudio`<br>`GET /api/v1/ordenes-estudio/{orderId}` |
| `clinical.study_order.create` | Emitir ordenes de estudio | sí | ✓ |  | ✓ |  |  |  | `POST /api/v1/encuentros/{encounterId}/ordenes-estudio` |
| `clinical.study_order.schedule_collection` | Agendar la toma de muestra de una orden de estudio |  | ✓ | ✓ | ✓ | ✓ |  |  | `PATCH /api/v1/ordenes-estudio/{orderId}/cita-toma` |
| `clinical.study_order.change_status` | Cambiar el estado de una orden de estudio |  | ✓ | ✓ | ✓ | ✓ |  |  | `PATCH /api/v1/ordenes-estudio/{orderId}/estado` |
| `clinical.study_result.read` | Consultar resultados de estudios | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/ordenes-estudio/{orderId}/resultados` |
| `clinical.study_result.create` | Registrar resultados de estudios | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/ordenes-estudio/{orderId}/resultados` |
| `clinical.vital_sign.read` | Consultar signos vitales | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/encuentros/{encounterId}/signos-vitales` |
| `clinical.vital_sign.create` | Registrar signos vitales | sí | ✓ |  | ✓ | ✓ |  |  | `POST /api/v1/encuentros/{encounterId}/signos-vitales` |
| `clinical.narrative.read` | Leer en claro el contenido clinico cifrado (antecedentes y resultados de estudios) | sí | ✓ | ✓ | ✓ |  |  |  | Sin endpoint propio: decide si las respuestas de antecedentes y resultados traen el texto descifrado (V5, RBAC-03) |

### Gestión documental (ms-documental) — 13 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `documents.document.read` | Consultar y buscar documentos |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/documents`<br>`GET /api/v1/documents/{documentId}` |
| `documents.document.upload` | Subir documentos | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/documents` |
| `documents.document.download` | Descargar el contenido de un documento | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/documents/{documentId}/content` |
| `documents.document.classify` | Clasificar documentos |  | ✓ | ✓ | ✓ | ✓ |  |  | `PATCH /api/v1/documents/{documentId}/classification` |
| `documents.document.link` | Vincular un documento a otra entidad |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/documents/{documentId}/links` |
| `documents.consent_template.read` | Consultar plantillas de consentimiento |  | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/consentimientos/plantillas`<br>`GET /api/v1/consentimientos/plantillas/{templateId}` |
| `documents.consent_template.create` | Crear plantillas de consentimiento |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/consentimientos/plantillas` |
| `documents.consent_template.publish` | Publicar plantillas de consentimiento | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/consentimientos/plantillas/{templateId}/publicacion` |
| `documents.consent.read` | Consultar consentimientos informados | sí | ✓ | ✓ | ✓ | ✓ |  |  | `GET /api/v1/consentimientos`<br>`GET /api/v1/consentimientos/{consentId}` |
| `documents.consent.generate` | Generar un consentimiento informado |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/consentimientos/generar` |
| `documents.consent.request_otp` | Enviar el codigo OTP de firma al paciente |  | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/consentimientos/{consentId}/otp` |
| `documents.consent.sign` | Registrar la firma de un consentimiento | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/consentimientos/{consentId}/firma` |
| `documents.consent.reject` | Registrar el rechazo de un consentimiento | sí | ✓ | ✓ | ✓ | ✓ |  |  | `POST /api/v1/consentimientos/{consentId}/rechazo` |

### Facturación (ms-facturacion) — 2 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `billing.invoice.read` | Consultar facturas |  | ✓ | ✓ |  | ✓ | ✓ |  | `GET /api/v1/facturas`<br>`GET /api/v1/facturas/{invoiceId}` |
| `billing.invoice.issue` | Emitir facturas | sí | ✓ | ✓ |  | ✓ | ✓ |  | `POST /api/v1/facturas` |

### Notificaciones (ms-notificaciones) — 11 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `notifications.template.read` | Consultar plantillas de mensaje |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/notifications/templates`<br>`GET /api/v1/notifications/templates/{templateId}` |
| `notifications.template.create` | Crear plantillas de mensaje |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/notifications/templates` |
| `notifications.template.update` | Modificar plantillas de mensaje |  | ✓ | ✓ |  |  |  |  | `PUT /api/v1/notifications/templates/{templateId}` |
| `notifications.rule.read` | Consultar reglas de notificacion automatica |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/notifications/rules` |
| `notifications.rule.create` | Crear reglas de notificacion automatica |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/notifications/rules` |
| `notifications.rule.update` | Modificar reglas de notificacion automatica |  | ✓ | ✓ |  |  |  |  | `PUT /api/v1/notifications/rules/{ruleId}` |
| `notifications.notification.read` | Consultar notificaciones enviadas y pendientes |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/notifications`<br>`GET /api/v1/notifications/{notificationId}` |
| `notifications.notification.create` | Crear notificaciones |  | ✓ | ✓ |  | ✓ |  |  | `POST /api/v1/notifications` |
| `notifications.notification.dispatch` | Despachar una notificacion |  | ✓ | ✓ |  | ✓ |  |  | `POST /api/v1/notifications/{notificationId}/dispatch` |
| `notifications.notification.retry` | Reintentar una notificacion fallida |  | ✓ | ✓ |  | ✓ |  |  | `POST /api/v1/notifications/{notificationId}/retry` |
| `notifications.whatsapp.send` | Enviar mensajes manuales de WhatsApp a pacientes | sí | ✓ | ✓ |  | ✓ |  |  | `POST /api/v1/notifications/whatsapp/send` |

### Auditoría (ms-auditoria) — 3 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `audit.log.read` | Consultar la bitacora de auditoria y su resumen | sí | ✓ | ✓ |  |  |  |  | `GET /api/v1/audit/events`<br>`GET /api/v1/audit/events/{eventId}`<br>`GET /api/v1/audit/summary` |
| `audit.log.verify` | Verificar la integridad de la cadena de auditoria |  | ✓ | ✓ |  |  |  |  | `GET /api/v1/audit/integrity` |
| `audit.log.export` | Exportar la bitacora de auditoria | sí | ✓ | ✓ |  |  |  |  | `GET /api/v1/audit/events/export` |

### Marketing (ms-marketing) — 34 permisos

| Permiso | Qué permite | Sensible | PA | AD | PH | RE | BC | PT | Endpoints |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `marketing.campaign.read` | Consultar campanas y su rendimiento |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/campanas`<br>`GET /api/v1/campanas/{campaignId}`<br>`GET /api/v1/campanas/{campaignId}/rendimiento` |
| `marketing.campaign.create` | Crear campanas |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas` |
| `marketing.campaign.build_audience` | Materializar la audiencia de una campana | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/audiencia` |
| `marketing.campaign.schedule` | Programar campanas |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/programacion` |
| `marketing.campaign.launch` | Lanzar campanas | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/lanzamiento` |
| `marketing.campaign.pause` | Pausar campanas |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/pausa` |
| `marketing.campaign.finish` | Finalizar campanas |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/finalizacion` |
| `marketing.campaign.cancel` | Cancelar campanas |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/campanas/{campaignId}/cancelacion` |
| `marketing.recipient.read` | Consultar los destinatarios de una campana | sí | ✓ | ✓ |  |  |  |  | `GET /api/v1/campanas/{campaignId}/destinatarios` |
| `marketing.segment.read` | Consultar segmentos y criterios de segmentacion |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/marketing/criterios-permitidos`<br>`GET /api/v1/segmentos`<br>`GET /api/v1/segmentos/{segmentId}` |
| `marketing.segment.create` | Crear segmentos |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/segmentos` |
| `marketing.segment.update` | Modificar segmentos |  | ✓ | ✓ |  |  |  |  | `PATCH /api/v1/segmentos/{segmentId}` |
| `marketing.segment.evaluate` | Evaluar segmentos y previsualizar audiencias | sí | ✓ | ✓ |  |  |  |  | `POST /api/v1/segmentos/vista-previa`<br>`POST /api/v1/segmentos/{segmentId}/evaluacion` |
| `marketing.segment.deactivate` | Desactivar segmentos |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/segmentos/{segmentId}/desactivacion` |
| `marketing.segment.delete` | Eliminar segmentos |  | ✓ | ✓ |  |  |  |  | `DELETE /api/v1/segmentos/{segmentId}` |
| `marketing.promotion.read` | Consultar promociones y cupones |  | ✓ | ✓ |  | ✓ | ✓ |  | `GET /api/v1/promociones`<br>`GET /api/v1/promociones/{promotionId}` |
| `marketing.promotion.create` | Crear promociones |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/promociones` |
| `marketing.promotion.deactivate` | Desactivar promociones |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/promociones/{promotionId}/desactivacion` |
| `marketing.coupon.validate` | Validar cupones |  | ✓ | ✓ |  | ✓ | ✓ |  | `POST /api/v1/promociones/validar` |
| `marketing.coupon.redeem` | Canjear cupones |  | ✓ | ✓ |  | ✓ | ✓ |  | `POST /api/v1/promociones/canjes` |
| `marketing.advertisement.read` | Consultar el listado de anuncios |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/anuncios` |
| `marketing.advertisement.view` | Ver anuncios vigentes |  | ✓ | ✓ |  | ✓ |  | ✓ | `GET /api/v1/anuncios/vigentes`<br>`GET /api/v1/anuncios/{advertisementId}` |
| `marketing.advertisement.track` | Registrar impresiones y clics de anuncios |  | ✓ | ✓ |  | ✓ |  | ✓ | `POST /api/v1/anuncios/{advertisementId}/clics`<br>`POST /api/v1/anuncios/{advertisementId}/impresiones` |
| `marketing.advertisement.create` | Crear anuncios |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/anuncios` |
| `marketing.advertisement.publish` | Publicar anuncios |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/anuncios/{advertisementId}/publicacion` |
| `marketing.advertisement.pause` | Pausar anuncios |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/anuncios/{advertisementId}/pausa` |
| `marketing.advertisement.finish` | Finalizar anuncios |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/anuncios/{advertisementId}/finalizacion` |
| `marketing.loyalty_program.read` | Consultar programas de fidelizacion |  | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/fidelidad/programas` |
| `marketing.loyalty_program.create` | Crear programas de fidelizacion |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/fidelidad/programas` |
| `marketing.loyalty_program.deactivate` | Desactivar programas de fidelizacion |  | ✓ | ✓ |  |  |  |  | `POST /api/v1/fidelidad/programas/{programId}/desactivacion` |
| `marketing.loyalty_account.read` | Consultar saldo y movimientos de fidelizacion |  | ✓ | ✓ |  | ✓ | ✓ | ✓ | `GET /api/v1/fidelidad/movimientos`<br>`GET /api/v1/fidelidad/saldo` |
| `marketing.loyalty_movement.create` | Registrar canjes y ajustes de puntos |  | ✓ | ✓ |  | ✓ | ✓ |  | `POST /api/v1/fidelidad/movimientos` |
| `marketing.patient_activity.read` | Consultar la actividad comercial de pacientes | sí | ✓ | ✓ |  | ✓ |  |  | `GET /api/v1/pacientes/actividad`<br>`GET /api/v1/pacientes/{patientId}/actividad` |
| `marketing.opt_out.update` | Registrar la baja o el alta de comunicaciones comerciales |  | ✓ | ✓ |  | ✓ |  | ✓ | `PUT /api/v1/pacientes/{patientId}/opt-out` |
