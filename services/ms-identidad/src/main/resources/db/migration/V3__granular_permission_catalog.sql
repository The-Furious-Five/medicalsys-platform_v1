-- RBAC-01 (SINS-11) - Catalogo granular de permisos por funcionalidad.
--
-- Un permiso por accion de negocio con el formato modulo.recurso.accion. Cada endpoint de
-- /api/v1/** de los microservicios queda cubierto por uno de estos permisos, salvo los publicos
-- (ping, login, webhook de WhatsApp) y los de autoservicio de la propia sesion (me, logout,
-- change-password). Los /internal/v1/** son llamadas entre servicios y no pasan por el gateway.
-- La hoja endpoint -> permiso esta en docs/seguridad/rbac-01-endpoints-permisos.csv.
--
-- La asignacion inicial a roles replica el control vigente para que RBAC-03 pueda reemplazar
-- los @RequiresRole sin cambiar quien puede hacer que:
--   * servicios con @RequiresRole (historial, pacientes, facturacion, tenencia, documental,
--     auditoria, marketing): exactamente los roles de la anotacion;
--   * servicios sin control (agenda, profesionales, catalogos): la regla del frontend;
--   * ms-notificaciones y los endpoints futuros de IAM-05 y RBAC-02: propuesta de minimo privilegio.
-- PATIENT deja de tener scheduling.appointment.read: ms-agenda no filtra por paciente, asi que
-- con ese permiso un paciente veria la agenda completa de su organizacion.

-- Modulos nuevos: ms-notificaciones, ms-auditoria y ms-marketing no tenian modulo propio.
ALTER TABLE permission DROP CHECK ck_permission_module;
ALTER TABLE permission ADD CONSTRAINT ck_permission_module CHECK (module IN (
    'IDENTITY', 'TENANCY', 'CATALOGS', 'CRYPTOGRAPHY', 'PATIENTS', 'PROFESSIONALS', 'SCHEDULING',
    'CLINICAL', 'DOCUMENT_MANAGEMENT', 'BILLING', 'ADMINISTRATION', 'NOTIFICATIONS', 'AUDIT', 'MARKETING'));

-- La asignacion del V2 se reconstruye entera mas abajo con los permisos granulares.
DELETE FROM role_permission;

-- Permisos gruesos del V2 reemplazados por permisos granulares:
--   identity.user.write                  -> identity.user.create, identity.user.update, identity.user.change_status
--   scheduling.appointment.write         -> scheduling.appointment.create/confirm/check_in/attend/no_show/cancel/reschedule
--   scheduling.resource.write            -> scheduling.resource.create/update/change_status, scheduling.block.*
--   patients.patient.write               -> patients.patient.create, patients.patient.update
--   clinical.record.write                -> clinical.encounter.*, clinical.diagnosis.*, clinical.prescription.* y demas clinical.*
--   billing.invoice.write                -> billing.invoice.issue
--   administration.organization.manage   -> tenancy.organization.*, tenancy.branch.*, tenancy.service_offering.*
DELETE FROM permission WHERE code IN (
    'identity.user.write',
    'scheduling.appointment.write',
    'scheduling.resource.write',
    'patients.patient.write',
    'clinical.record.write',
    'billing.invoice.write',
    'administration.organization.manage');

-- Los codigos del V2 que se conservan (identity.user.read, clinical.record.read, ...) solo
-- actualizan su descripcion; el resto se inserta.
INSERT INTO permission (code, module, description, is_sensitive) VALUES
    -- IDENTITY
    ('identity.user.read', 'IDENTITY', 'Consultar cuentas de usuario', FALSE),
    ('identity.user.create', 'IDENTITY', 'Crear cuentas de usuario', TRUE),
    ('identity.user.update', 'IDENTITY', 'Modificar los datos de una cuenta de usuario', TRUE),
    ('identity.user.change_status', 'IDENTITY', 'Activar, desactivar o bloquear una cuenta de usuario', TRUE),
    ('identity.user.unlock', 'IDENTITY', 'Desbloquear una cuenta bloqueada por intentos fallidos (IAM-05)', TRUE),
    ('identity.user.reset', 'IDENTITY', 'Reiniciar la contrasena de una cuenta con una temporal (IAM-05)', TRUE),
    ('identity.role.read', 'IDENTITY', 'Consultar roles y el catalogo de permisos', FALSE),
    ('identity.role.assign', 'IDENTITY', 'Asignar o revocar roles a una cuenta de usuario', TRUE),
    ('identity.role.manage', 'IDENTITY', 'Crear, editar y desactivar roles y sus permisos (RBAC-02)', TRUE),
    -- TENANCY
    ('tenancy.organization.create', 'TENANCY', 'Dar de alta una organizacion cliente', TRUE),
    ('tenancy.organization.read', 'TENANCY', 'Consultar los datos de la organizacion', FALSE),
    ('tenancy.organization.update', 'TENANCY', 'Modificar los datos de la organizacion', FALSE),
    ('tenancy.organization.change_status', 'TENANCY', 'Suspender o reactivar la organizacion', TRUE),
    ('tenancy.branch.create', 'TENANCY', 'Crear sucursales', FALSE),
    ('tenancy.branch.read', 'TENANCY', 'Consultar sucursales', FALSE),
    ('tenancy.branch.update', 'TENANCY', 'Modificar sucursales', FALSE),
    ('tenancy.branch.change_status', 'TENANCY', 'Activar o desactivar sucursales', FALSE),
    ('tenancy.service_offering.create', 'TENANCY', 'Crear prestaciones del tarifario', FALSE),
    ('tenancy.service_offering.read', 'TENANCY', 'Consultar prestaciones del tarifario', FALSE),
    ('tenancy.service_offering.update', 'TENANCY', 'Modificar prestaciones y precios del tarifario', FALSE),
    ('tenancy.service_offering.change_status', 'TENANCY', 'Cambiar la vigencia de una prestacion', FALSE),
    ('tenancy.subscription.read', 'TENANCY', 'Consultar la suscripcion vigente', FALSE),
    ('tenancy.subscription.read_history', 'TENANCY', 'Consultar el historial de suscripciones', FALSE),
    ('tenancy.subscription.subscribe', 'TENANCY', 'Contratar o cambiar el plan de suscripcion', TRUE),
    ('tenancy.subscription.change_status', 'TENANCY', 'Suspender, reactivar o cancelar la suscripcion', TRUE),
    ('tenancy.plan.read', 'TENANCY', 'Consultar los planes de suscripcion', FALSE),
    ('tenancy.usage.read', 'TENANCY', 'Consultar el consumo del plan', FALSE),
    ('tenancy.usage.record', 'TENANCY', 'Registrar consumo del plan', FALSE),
    -- CATALOGS
    ('catalogs.icd10.read', 'CATALOGS', 'Consultar el catalogo CIE-10', FALSE),
    ('catalogs.medication.read', 'CATALOGS', 'Consultar el vademecum de medicamentos', FALSE),
    ('catalogs.specialty.read', 'CATALOGS', 'Consultar especialidades medicas', FALSE),
    ('catalogs.study_type.read', 'CATALOGS', 'Consultar tipos de estudio', FALSE),
    ('catalogs.tax.read', 'CATALOGS', 'Consultar el catalogo tributario', FALSE),
    -- PATIENTS
    ('patients.patient.read', 'PATIENTS', 'Consultar y buscar datos administrativos del paciente', FALSE),
    ('patients.patient.create', 'PATIENTS', 'Registrar pacientes', FALSE),
    ('patients.patient.update', 'PATIENTS', 'Actualizar datos administrativos del paciente', FALSE),
    ('patients.merge.create', 'PATIENTS', 'Fusionar registros duplicados de paciente', TRUE),
    ('patients.merge.read', 'PATIENTS', 'Consultar el historial de fusiones de un paciente', FALSE),
    ('patients.notification_preference.read', 'PATIENTS', 'Consultar preferencias de notificacion del paciente', FALSE),
    ('patients.notification_preference.update', 'PATIENTS', 'Modificar preferencias de notificacion del paciente', FALSE),
    ('patients.access_grant.read', 'PATIENTS', 'Consultar solicitudes de acceso a los datos de un paciente', FALSE),
    ('patients.access_grant.request', 'PATIENTS', 'Solicitar acceso a los datos de un paciente de otra organizacion', TRUE),
    ('patients.access_grant.confirm', 'PATIENTS', 'Confirmar una solicitud de acceso con el consentimiento del paciente', TRUE),
    ('patients.access_grant.revoke', 'PATIENTS', 'Revocar un acceso concedido a los datos de un paciente', TRUE),
    -- PROFESSIONALS
    ('professionals.professional.read', 'PROFESSIONALS', 'Consultar el directorio de profesionales de salud', FALSE),
    ('professionals.professional.create', 'PROFESSIONALS', 'Dar de alta profesionales de salud', FALSE),
    ('professionals.professional.update', 'PROFESSIONALS', 'Modificar datos de un profesional de salud', FALSE),
    ('professionals.affiliation.change_status', 'PROFESSIONALS', 'Activar o desactivar el vinculo de un profesional con la organizacion', TRUE),
    -- SCHEDULING
    ('scheduling.appointment.read', 'SCHEDULING', 'Consultar citas y su historial', FALSE),
    ('scheduling.appointment.create', 'SCHEDULING', 'Agendar citas', FALSE),
    ('scheduling.appointment.confirm', 'SCHEDULING', 'Confirmar citas', FALSE),
    ('scheduling.appointment.check_in', 'SCHEDULING', 'Registrar la llegada del paciente a la cita', FALSE),
    ('scheduling.appointment.attend', 'SCHEDULING', 'Iniciar y completar la atencion de una cita', FALSE),
    ('scheduling.appointment.no_show', 'SCHEDULING', 'Marcar una cita como inasistencia', FALSE),
    ('scheduling.appointment.cancel', 'SCHEDULING', 'Cancelar citas', FALSE),
    ('scheduling.appointment.reschedule', 'SCHEDULING', 'Reprogramar citas', FALSE),
    ('scheduling.availability.read', 'SCHEDULING', 'Consultar disponibilidad y horarios libres', FALSE),
    ('scheduling.availability.create', 'SCHEDULING', 'Definir reglas de disponibilidad de un profesional', FALSE),
    ('scheduling.availability.deactivate', 'SCHEDULING', 'Desactivar reglas de disponibilidad', FALSE),
    ('scheduling.block.read', 'SCHEDULING', 'Consultar bloqueos de agenda', FALSE),
    ('scheduling.block.create', 'SCHEDULING', 'Bloquear franjas de agenda de un profesional', FALSE),
    ('scheduling.block.delete', 'SCHEDULING', 'Eliminar bloqueos de agenda', FALSE),
    ('scheduling.reservation.read', 'SCHEDULING', 'Consultar reservas de consultorios y recursos', FALSE),
    ('scheduling.reservation.create', 'SCHEDULING', 'Reservar consultorios y recursos', FALSE),
    ('scheduling.reservation.release', 'SCHEDULING', 'Liberar una reserva de recurso', FALSE),
    ('scheduling.reservation.mark_in_use', 'SCHEDULING', 'Marcar una reserva de recurso como en uso', FALSE),
    ('scheduling.reservation.cancel', 'SCHEDULING', 'Cancelar una reserva de recurso', FALSE),
    ('scheduling.resource.read', 'SCHEDULING', 'Consultar recursos fisicos', FALSE),
    ('scheduling.resource.create', 'SCHEDULING', 'Crear recursos fisicos', FALSE),
    ('scheduling.resource.update', 'SCHEDULING', 'Modificar recursos fisicos', FALSE),
    ('scheduling.resource.change_status', 'SCHEDULING', 'Cambiar el estado de un recurso fisico', FALSE),
    -- CLINICAL
    ('clinical.record.create', 'CLINICAL', 'Abrir el expediente clinico de un paciente', TRUE),
    ('clinical.record.read', 'CLINICAL', 'Consultar expedientes clinicos', TRUE),
    ('clinical.record.share', 'CLINICAL', 'Configurar el compartido del expediente con otras organizaciones', TRUE),
    ('clinical.record.change_status', 'CLINICAL', 'Cerrar o reabrir un expediente clinico', TRUE),
    ('clinical.allergy.read', 'CLINICAL', 'Consultar alergias del paciente', TRUE),
    ('clinical.allergy.create', 'CLINICAL', 'Registrar alergias del paciente', TRUE),
    ('clinical.allergy.update', 'CLINICAL', 'Modificar alergias del paciente', TRUE),
    ('clinical.background.read', 'CLINICAL', 'Consultar antecedentes medicos', TRUE),
    ('clinical.background.create', 'CLINICAL', 'Registrar antecedentes medicos', TRUE),
    ('clinical.background.deactivate', 'CLINICAL', 'Desactivar antecedentes medicos', TRUE),
    ('clinical.encounter.read', 'CLINICAL', 'Consultar encuentros clinicos', TRUE),
    ('clinical.encounter.open', 'CLINICAL', 'Abrir un encuentro clinico', TRUE),
    ('clinical.encounter.update_notes', 'CLINICAL', 'Redactar la nota clinica del encuentro', TRUE),
    ('clinical.encounter.sign', 'CLINICAL', 'Firmar un encuentro clinico', TRUE),
    ('clinical.encounter.void', 'CLINICAL', 'Anular un encuentro clinico', TRUE),
    ('clinical.addendum.read', 'CLINICAL', 'Consultar adendas de un encuentro firmado', TRUE),
    ('clinical.addendum.create', 'CLINICAL', 'Agregar adendas a un encuentro firmado', TRUE),
    ('clinical.diagnosis.read', 'CLINICAL', 'Consultar diagnosticos', TRUE),
    ('clinical.diagnosis.create', 'CLINICAL', 'Registrar diagnosticos', TRUE),
    ('clinical.diagnosis.update', 'CLINICAL', 'Modificar diagnosticos', TRUE),
    ('clinical.procedure.read', 'CLINICAL', 'Consultar procedimientos realizados', TRUE),
    ('clinical.procedure.create', 'CLINICAL', 'Registrar procedimientos realizados', TRUE),
    ('clinical.prescription.read', 'CLINICAL', 'Consultar recetas', TRUE),
    ('clinical.prescription.create', 'CLINICAL', 'Emitir recetas', TRUE),
    ('clinical.prescription.change_status', 'CLINICAL', 'Cambiar el estado de una receta', TRUE),
    ('clinical.prescription.attach_document', 'CLINICAL', 'Adjuntar el documento firmado de una receta', TRUE),
    ('clinical.study_order.read', 'CLINICAL', 'Consultar ordenes de estudio', TRUE),
    ('clinical.study_order.create', 'CLINICAL', 'Emitir ordenes de estudio', TRUE),
    ('clinical.study_order.schedule_collection', 'CLINICAL', 'Agendar la toma de muestra de una orden de estudio', FALSE),
    ('clinical.study_order.change_status', 'CLINICAL', 'Cambiar el estado de una orden de estudio', FALSE),
    ('clinical.study_result.read', 'CLINICAL', 'Consultar resultados de estudios', TRUE),
    ('clinical.study_result.create', 'CLINICAL', 'Registrar resultados de estudios', TRUE),
    ('clinical.vital_sign.read', 'CLINICAL', 'Consultar signos vitales', TRUE),
    ('clinical.vital_sign.create', 'CLINICAL', 'Registrar signos vitales', TRUE),
    -- DOCUMENT_MANAGEMENT
    ('documents.document.read', 'DOCUMENT_MANAGEMENT', 'Consultar y buscar documentos', FALSE),
    ('documents.document.upload', 'DOCUMENT_MANAGEMENT', 'Subir documentos', TRUE),
    ('documents.document.download', 'DOCUMENT_MANAGEMENT', 'Descargar el contenido de un documento', TRUE),
    ('documents.document.classify', 'DOCUMENT_MANAGEMENT', 'Clasificar documentos', FALSE),
    ('documents.document.link', 'DOCUMENT_MANAGEMENT', 'Vincular un documento a otra entidad', FALSE),
    ('documents.consent_template.read', 'DOCUMENT_MANAGEMENT', 'Consultar plantillas de consentimiento', FALSE),
    ('documents.consent_template.create', 'DOCUMENT_MANAGEMENT', 'Crear plantillas de consentimiento', FALSE),
    ('documents.consent_template.publish', 'DOCUMENT_MANAGEMENT', 'Publicar plantillas de consentimiento', TRUE),
    ('documents.consent.read', 'DOCUMENT_MANAGEMENT', 'Consultar consentimientos informados', TRUE),
    ('documents.consent.generate', 'DOCUMENT_MANAGEMENT', 'Generar un consentimiento informado', FALSE),
    ('documents.consent.request_otp', 'DOCUMENT_MANAGEMENT', 'Enviar el codigo OTP de firma al paciente', FALSE),
    ('documents.consent.sign', 'DOCUMENT_MANAGEMENT', 'Registrar la firma de un consentimiento', TRUE),
    ('documents.consent.reject', 'DOCUMENT_MANAGEMENT', 'Registrar el rechazo de un consentimiento', TRUE),
    -- BILLING
    ('billing.invoice.read', 'BILLING', 'Consultar facturas', FALSE),
    ('billing.invoice.issue', 'BILLING', 'Emitir facturas', TRUE),
    -- NOTIFICATIONS
    ('notifications.template.read', 'NOTIFICATIONS', 'Consultar plantillas de mensaje', FALSE),
    ('notifications.template.create', 'NOTIFICATIONS', 'Crear plantillas de mensaje', FALSE),
    ('notifications.template.update', 'NOTIFICATIONS', 'Modificar plantillas de mensaje', FALSE),
    ('notifications.rule.read', 'NOTIFICATIONS', 'Consultar reglas de notificacion automatica', FALSE),
    ('notifications.rule.create', 'NOTIFICATIONS', 'Crear reglas de notificacion automatica', FALSE),
    ('notifications.rule.update', 'NOTIFICATIONS', 'Modificar reglas de notificacion automatica', FALSE),
    ('notifications.notification.read', 'NOTIFICATIONS', 'Consultar notificaciones enviadas y pendientes', FALSE),
    ('notifications.notification.create', 'NOTIFICATIONS', 'Crear notificaciones', FALSE),
    ('notifications.notification.dispatch', 'NOTIFICATIONS', 'Despachar una notificacion', FALSE),
    ('notifications.notification.retry', 'NOTIFICATIONS', 'Reintentar una notificacion fallida', FALSE),
    ('notifications.whatsapp.send', 'NOTIFICATIONS', 'Enviar mensajes manuales de WhatsApp a pacientes', TRUE),
    -- AUDIT
    ('audit.log.read', 'AUDIT', 'Consultar la bitacora de auditoria y su resumen', TRUE),
    ('audit.log.verify', 'AUDIT', 'Verificar la integridad de la cadena de auditoria', FALSE),
    ('audit.log.export', 'AUDIT', 'Exportar la bitacora de auditoria', TRUE),
    -- MARKETING
    ('marketing.campaign.read', 'MARKETING', 'Consultar campanas y su rendimiento', FALSE),
    ('marketing.campaign.create', 'MARKETING', 'Crear campanas', FALSE),
    ('marketing.campaign.build_audience', 'MARKETING', 'Materializar la audiencia de una campana', TRUE),
    ('marketing.campaign.schedule', 'MARKETING', 'Programar campanas', FALSE),
    ('marketing.campaign.launch', 'MARKETING', 'Lanzar campanas', TRUE),
    ('marketing.campaign.pause', 'MARKETING', 'Pausar campanas', FALSE),
    ('marketing.campaign.finish', 'MARKETING', 'Finalizar campanas', FALSE),
    ('marketing.campaign.cancel', 'MARKETING', 'Cancelar campanas', FALSE),
    ('marketing.recipient.read', 'MARKETING', 'Consultar los destinatarios de una campana', TRUE),
    ('marketing.segment.read', 'MARKETING', 'Consultar segmentos y criterios de segmentacion', FALSE),
    ('marketing.segment.create', 'MARKETING', 'Crear segmentos', FALSE),
    ('marketing.segment.update', 'MARKETING', 'Modificar segmentos', FALSE),
    ('marketing.segment.evaluate', 'MARKETING', 'Evaluar segmentos y previsualizar audiencias', TRUE),
    ('marketing.segment.deactivate', 'MARKETING', 'Desactivar segmentos', FALSE),
    ('marketing.segment.delete', 'MARKETING', 'Eliminar segmentos', FALSE),
    ('marketing.promotion.read', 'MARKETING', 'Consultar promociones y cupones', FALSE),
    ('marketing.promotion.create', 'MARKETING', 'Crear promociones', FALSE),
    ('marketing.promotion.deactivate', 'MARKETING', 'Desactivar promociones', FALSE),
    ('marketing.coupon.validate', 'MARKETING', 'Validar cupones', FALSE),
    ('marketing.coupon.redeem', 'MARKETING', 'Canjear cupones', FALSE),
    ('marketing.advertisement.read', 'MARKETING', 'Consultar el listado de anuncios', FALSE),
    ('marketing.advertisement.view', 'MARKETING', 'Ver anuncios vigentes', FALSE),
    ('marketing.advertisement.track', 'MARKETING', 'Registrar impresiones y clics de anuncios', FALSE),
    ('marketing.advertisement.create', 'MARKETING', 'Crear anuncios', FALSE),
    ('marketing.advertisement.publish', 'MARKETING', 'Publicar anuncios', FALSE),
    ('marketing.advertisement.pause', 'MARKETING', 'Pausar anuncios', FALSE),
    ('marketing.advertisement.finish', 'MARKETING', 'Finalizar anuncios', FALSE),
    ('marketing.loyalty_program.read', 'MARKETING', 'Consultar programas de fidelizacion', FALSE),
    ('marketing.loyalty_program.create', 'MARKETING', 'Crear programas de fidelizacion', FALSE),
    ('marketing.loyalty_program.deactivate', 'MARKETING', 'Desactivar programas de fidelizacion', FALSE),
    ('marketing.loyalty_account.read', 'MARKETING', 'Consultar saldo y movimientos de fidelizacion', FALSE),
    ('marketing.loyalty_movement.create', 'MARKETING', 'Registrar canjes y ajustes de puntos', FALSE),
    ('marketing.patient_activity.read', 'MARKETING', 'Consultar la actividad comercial de pacientes', TRUE),
    ('marketing.opt_out.update', 'MARKETING', 'Registrar la baja o el alta de comunicaciones comerciales', FALSE)
AS incoming
ON DUPLICATE KEY UPDATE
    module = incoming.module,
    description = incoming.description,
    is_sensitive = incoming.is_sensitive;

-- PLATFORM_ADMIN figura en todos los @RequiresRole vigentes: recibe el catalogo completo.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PLATFORM_ADMIN';

-- ADMINISTRATOR: administra su organizacion; no crea organizaciones ni redefine roles globales.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'ADMINISTRATOR'
  AND p.code IN (
      'identity.user.read', 'identity.user.create', 'identity.user.update',
      'identity.user.change_status', 'identity.user.unlock', 'identity.user.reset',
      'identity.role.read', 'identity.role.assign',
      'tenancy.organization.read', 'tenancy.organization.update', 'tenancy.organization.change_status',
      'tenancy.branch.create', 'tenancy.branch.read', 'tenancy.branch.update',
      'tenancy.branch.change_status', 'tenancy.service_offering.create',
      'tenancy.service_offering.read', 'tenancy.service_offering.update',
      'tenancy.service_offering.change_status', 'tenancy.subscription.read',
      'tenancy.subscription.read_history', 'tenancy.subscription.subscribe',
      'tenancy.subscription.change_status', 'tenancy.plan.read', 'tenancy.usage.read',
      'tenancy.usage.record',
      'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
      'catalogs.study_type.read', 'catalogs.tax.read',
      'patients.patient.read', 'patients.patient.create', 'patients.patient.update',
      'patients.merge.create', 'patients.merge.read', 'patients.notification_preference.read',
      'patients.notification_preference.update', 'patients.access_grant.read',
      'patients.access_grant.request', 'patients.access_grant.confirm', 'patients.access_grant.revoke',
      'professionals.professional.read', 'professionals.professional.create',
      'professionals.professional.update', 'professionals.affiliation.change_status',
      'scheduling.appointment.read', 'scheduling.appointment.create', 'scheduling.appointment.confirm',
      'scheduling.appointment.check_in', 'scheduling.appointment.attend',
      'scheduling.appointment.no_show', 'scheduling.appointment.cancel',
      'scheduling.appointment.reschedule', 'scheduling.availability.read',
      'scheduling.availability.create', 'scheduling.availability.deactivate', 'scheduling.block.read',
      'scheduling.block.create', 'scheduling.block.delete', 'scheduling.reservation.read',
      'scheduling.reservation.create', 'scheduling.reservation.release',
      'scheduling.reservation.mark_in_use', 'scheduling.reservation.cancel',
      'scheduling.resource.read', 'scheduling.resource.create', 'scheduling.resource.update',
      'scheduling.resource.change_status',
      'clinical.record.create', 'clinical.record.read', 'clinical.record.share',
      'clinical.record.change_status', 'clinical.allergy.read', 'clinical.allergy.create',
      'clinical.allergy.update', 'clinical.background.read', 'clinical.background.create',
      'clinical.background.deactivate', 'clinical.encounter.read', 'clinical.encounter.open',
      'clinical.encounter.void', 'clinical.addendum.read', 'clinical.diagnosis.read',
      'clinical.procedure.read', 'clinical.prescription.read', 'clinical.prescription.change_status',
      'clinical.prescription.attach_document', 'clinical.study_order.read',
      'clinical.study_order.schedule_collection', 'clinical.study_order.change_status',
      'clinical.study_result.read', 'clinical.study_result.create', 'clinical.vital_sign.read',
      'documents.document.read', 'documents.document.upload', 'documents.document.download',
      'documents.document.classify', 'documents.document.link', 'documents.consent_template.read',
      'documents.consent_template.create', 'documents.consent_template.publish',
      'documents.consent.read', 'documents.consent.generate', 'documents.consent.request_otp',
      'documents.consent.sign', 'documents.consent.reject',
      'billing.invoice.read', 'billing.invoice.issue',
      'notifications.template.read', 'notifications.template.create', 'notifications.template.update',
      'notifications.rule.read', 'notifications.rule.create', 'notifications.rule.update',
      'notifications.notification.read', 'notifications.notification.create',
      'notifications.notification.dispatch', 'notifications.notification.retry',
      'notifications.whatsapp.send',
      'audit.log.read', 'audit.log.verify', 'audit.log.export',
      'marketing.campaign.read', 'marketing.campaign.create', 'marketing.campaign.build_audience',
      'marketing.campaign.schedule', 'marketing.campaign.launch', 'marketing.campaign.pause',
      'marketing.campaign.finish', 'marketing.campaign.cancel', 'marketing.recipient.read',
      'marketing.segment.read', 'marketing.segment.create', 'marketing.segment.update',
      'marketing.segment.evaluate', 'marketing.segment.deactivate', 'marketing.segment.delete',
      'marketing.promotion.read', 'marketing.promotion.create', 'marketing.promotion.deactivate',
      'marketing.coupon.validate', 'marketing.coupon.redeem', 'marketing.advertisement.read',
      'marketing.advertisement.view', 'marketing.advertisement.track',
      'marketing.advertisement.create', 'marketing.advertisement.publish',
      'marketing.advertisement.pause', 'marketing.advertisement.finish',
      'marketing.loyalty_program.read', 'marketing.loyalty_program.create',
      'marketing.loyalty_program.deactivate', 'marketing.loyalty_account.read',
      'marketing.loyalty_movement.create', 'marketing.patient_activity.read',
      'marketing.opt_out.update');

-- PHYSICIAN: atencion clinica; no administra la organizacion, usuarios ni auditoria.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PHYSICIAN'
  AND p.code IN (
      'tenancy.organization.read', 'tenancy.branch.read', 'tenancy.service_offering.read',
      'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
      'catalogs.study_type.read', 'catalogs.tax.read',
      'patients.patient.read', 'patients.notification_preference.read', 'patients.access_grant.read',
      'patients.access_grant.request', 'patients.access_grant.confirm', 'patients.access_grant.revoke',
      'professionals.professional.read',
      'scheduling.appointment.read', 'scheduling.appointment.create', 'scheduling.appointment.confirm',
      'scheduling.appointment.check_in', 'scheduling.appointment.attend',
      'scheduling.appointment.no_show', 'scheduling.appointment.cancel',
      'scheduling.appointment.reschedule', 'scheduling.availability.read',
      'scheduling.availability.create', 'scheduling.availability.deactivate', 'scheduling.block.read',
      'scheduling.block.create', 'scheduling.block.delete', 'scheduling.reservation.read',
      'scheduling.reservation.create', 'scheduling.reservation.release',
      'scheduling.reservation.mark_in_use', 'scheduling.reservation.cancel',
      'scheduling.resource.read',
      'clinical.record.create', 'clinical.record.read', 'clinical.record.share',
      'clinical.allergy.read', 'clinical.allergy.create', 'clinical.allergy.update',
      'clinical.background.read', 'clinical.background.create', 'clinical.background.deactivate',
      'clinical.encounter.read', 'clinical.encounter.open', 'clinical.encounter.update_notes',
      'clinical.encounter.sign', 'clinical.encounter.void', 'clinical.addendum.read',
      'clinical.addendum.create', 'clinical.diagnosis.read', 'clinical.diagnosis.create',
      'clinical.diagnosis.update', 'clinical.procedure.read', 'clinical.procedure.create',
      'clinical.prescription.read', 'clinical.prescription.create',
      'clinical.prescription.change_status', 'clinical.prescription.attach_document',
      'clinical.study_order.read', 'clinical.study_order.create',
      'clinical.study_order.schedule_collection', 'clinical.study_order.change_status',
      'clinical.study_result.read', 'clinical.study_result.create', 'clinical.vital_sign.read',
      'clinical.vital_sign.create',
      'documents.document.read', 'documents.document.upload', 'documents.document.download',
      'documents.document.classify', 'documents.document.link', 'documents.consent_template.read',
      'documents.consent.read', 'documents.consent.generate', 'documents.consent.request_otp',
      'documents.consent.sign', 'documents.consent.reject');

-- RECEPTIONIST: mostrador (agenda, admision, cobro y consulta clinica de apoyo).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'RECEPTIONIST'
  AND p.code IN (
      'tenancy.organization.read', 'tenancy.branch.read', 'tenancy.service_offering.read',
      'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
      'catalogs.study_type.read', 'catalogs.tax.read',
      'patients.patient.read', 'patients.patient.create', 'patients.patient.update',
      'patients.notification_preference.read', 'patients.notification_preference.update',
      'patients.access_grant.read', 'patients.access_grant.request', 'patients.access_grant.confirm',
      'patients.access_grant.revoke',
      'professionals.professional.read',
      'scheduling.appointment.read', 'scheduling.appointment.create', 'scheduling.appointment.confirm',
      'scheduling.appointment.check_in', 'scheduling.appointment.attend',
      'scheduling.appointment.no_show', 'scheduling.appointment.cancel',
      'scheduling.appointment.reschedule', 'scheduling.availability.read', 'scheduling.block.read',
      'scheduling.reservation.read', 'scheduling.reservation.create', 'scheduling.reservation.release',
      'scheduling.reservation.mark_in_use', 'scheduling.reservation.cancel',
      'scheduling.resource.read',
      'clinical.record.create', 'clinical.record.read', 'clinical.allergy.read',
      'clinical.background.read', 'clinical.encounter.read', 'clinical.diagnosis.read',
      'clinical.prescription.read', 'clinical.prescription.change_status',
      'clinical.prescription.attach_document', 'clinical.study_order.read',
      'clinical.study_order.schedule_collection', 'clinical.study_order.change_status',
      'clinical.study_result.read', 'clinical.study_result.create', 'clinical.vital_sign.read',
      'clinical.vital_sign.create',
      'documents.document.read', 'documents.document.upload', 'documents.document.download',
      'documents.document.classify', 'documents.document.link', 'documents.consent_template.read',
      'documents.consent.read', 'documents.consent.generate', 'documents.consent.request_otp',
      'documents.consent.sign', 'documents.consent.reject',
      'billing.invoice.read', 'billing.invoice.issue',
      'notifications.template.read', 'notifications.notification.read',
      'notifications.notification.create', 'notifications.notification.dispatch',
      'notifications.notification.retry', 'notifications.whatsapp.send',
      'marketing.campaign.read', 'marketing.segment.read', 'marketing.promotion.read',
      'marketing.coupon.validate', 'marketing.coupon.redeem', 'marketing.advertisement.read',
      'marketing.advertisement.view', 'marketing.advertisement.track',
      'marketing.loyalty_program.read', 'marketing.loyalty_account.read',
      'marketing.loyalty_movement.create', 'marketing.patient_activity.read',
      'marketing.opt_out.update');

-- BILLING_CLERK: facturacion y caja.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'BILLING_CLERK'
  AND p.code IN (
      'tenancy.organization.read', 'tenancy.branch.read', 'tenancy.service_offering.read',
      'tenancy.subscription.read', 'tenancy.plan.read',
      'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
      'catalogs.study_type.read', 'catalogs.tax.read',
      'billing.invoice.read', 'billing.invoice.issue',
      'marketing.promotion.read', 'marketing.coupon.validate', 'marketing.coupon.redeem',
      'marketing.loyalty_account.read', 'marketing.loyalty_movement.create');

-- PATIENT: solo autoservicio comercial (anuncios, fidelizacion y baja de comunicaciones).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PATIENT'
  AND p.code IN (
      'marketing.advertisement.view', 'marketing.advertisement.track',
      'marketing.loyalty_account.read', 'marketing.opt_out.update');
