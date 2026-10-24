-- Matriz de Roles.xlsx: 15 roles y 30 recursos. No editar migraciones anteriores.
-- Generada por scripts/ci/check_role_matrix.py --write desde docs/seguridad/matriz-roles/matriz.json.
-- Las dependencias son lecturas de selectores, nunca permisos de escritura en otros modulos.
ALTER TABLE permission DROP CHECK ck_permission_module;
ALTER TABLE permission ADD CONSTRAINT ck_permission_module CHECK (module IN ('IDENTITY', 'TENANCY', 'CATALOGS', 'CRYPTOGRAPHY', 'PATIENTS', 'PROFESSIONALS', 'SCHEDULING', 'CLINICAL', 'DOCUMENT_MANAGEMENT', 'BILLING', 'ADMINISTRATION', 'NOTIFICATIONS', 'AUDIT', 'MARKETING', 'INFRASTRUCTURE'));
ALTER TABLE `role` ADD COLUMN access_model VARCHAR(4) NOT NULL DEFAULT 'MAC';
ALTER TABLE `role` ADD CONSTRAINT ck_role_access_model CHECK (access_model IN ('MAC', 'DAC', 'RBAC'));
INSERT INTO permission (code, module, description, is_sensitive, classification) VALUES
    ('identity.role.assign_initial', 'IDENTITY', 'Asignar un rol delegado exclusivamente al crear una cuenta', TRUE, 'CONFIDENTIAL'),
    ('billing.invoice.void', 'BILLING', 'Anular una factura; separado de la emision (Cajero 2)', TRUE, 'CONFIDENTIAL'),
    ('infrastructure.server.manage', 'INFRASTRUCTURE', 'Operacion de servidores por el equipo de Tecnologia (DevOps)', TRUE, 'RESTRICTED'),
    ('infrastructure.database.manage', 'INFRASTRUCTURE', 'Operacion de bases de datos por el equipo de Tecnologia (DevOps)', TRUE, 'RESTRICTED'),
    ('patients.self_access.read', 'PATIENTS', 'Consultar las solicitudes de acceso a los propios datos', TRUE, 'CONFIDENTIAL'),
    ('patients.self_access.confirm', 'PATIENTS', 'Autorizar con OTP el acceso a los propios datos', TRUE, 'CONFIDENTIAL'),
    ('patients.self_access.revoke', 'PATIENTS', 'Revocar el acceso a los propios datos', TRUE, 'CONFIDENTIAL');
INSERT INTO `role` (code, name, description, scope, is_system, is_active, access_model) VALUES
    ('PLATFORM_ADMIN', 'Admin. de plataforma', 'Sucursales y tarifario; Suscripción y planes; Alta de organizaciones', 'PLATFORM', TRUE, TRUE, 'MAC'),
    ('OSI', 'Oficial Seguridad (OSI)', 'Módulo de Administración de Usuarios: Altas; Módulo de Administración de Usuarios: Bajas; Módulo de Administración de Usuarios: Cambio perfil; Bitácora', 'PLATFORM', TRUE, TRUE, 'MAC'),
    ('AUDITOR', 'Auditor interno', 'Bitácora; Exportación de bitácora', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('EXTERNAL_AUDITOR', 'Auditor externo', 'Bitácora', 'ORGANIZATION', TRUE, TRUE, 'DAC'),
    ('ADMINISTRATOR', 'Admin. de organización', 'Módulo de Administración de Usuarios: Altas; Sucursales y tarifario; Suscripción y planes; Ficha del paciente; Fusión de duplicados; Citas; Disponibilidad y bloqueos; Reservas y recursos; Plantillas consentimiento; Plantillas y reglas', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('PHYSICIAN', 'Médico', 'Ficha del paciente; Acceso otro centro (OTP); Citas; Disponibilidad y bloqueos; Reservas y recursos; Expediente clínico; Narrativa cifrada; Registro y firma; Signos vitales y alergias; Estudios y resultados; Documentos clínicos; Consentimientos', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('MEDICAL_INTERN', 'Pasante de medicina', 'Ficha del paciente; Expediente clínico; Signos vitales y alergias', 'ORGANIZATION', TRUE, TRUE, 'DAC'),
    ('NURSE', 'Enfermería', 'Ficha del paciente; Citas; Reservas y recursos; Expediente clínico; Signos vitales y alergias; Estudios y resultados; Documentos clínicos', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('LAB_TECHNICIAN', 'Laboratorio', 'Ficha del paciente; Estudios y resultados; Documentos clínicos', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('RECEPTIONIST', 'Recepción', 'Ficha del paciente; Acceso otro centro (OTP); Citas; Reservas y recursos; Consentimientos; Anuncios', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('BILLING_CLERK', 'Cajero 1', 'Ficha del paciente; Emisión de facturas; Cupones y puntos', 'ORGANIZATION', TRUE, TRUE, 'RBAC'),
    ('BILLING_SUPERVISOR', 'Cajero 2', 'Anulación de facturas', 'ORGANIZATION', TRUE, TRUE, 'RBAC'),
    ('MARKETING_MANAGER', 'Marketing', 'Cupones y puntos; Plantillas y reglas; Campañas; Anuncios', 'ORGANIZATION', TRUE, TRUE, 'MAC'),
    ('PATIENT', 'Paciente', 'Acceso otro centro (OTP); Anuncios', 'ORGANIZATION', TRUE, TRUE, 'DAC'),
    ('DEVOPS', 'Tecnología (DevOps)', 'Servidores; Bases de datos', 'PLATFORM', TRUE, TRUE, 'MAC')
AS incoming ON DUPLICATE KEY UPDATE name = incoming.name, description = incoming.description,
    scope = incoming.scope, is_system = TRUE, is_active = TRUE, access_model = incoming.access_model;
DELETE rp FROM role_permission rp JOIN `role` r ON r.id = rp.role_id WHERE r.code IN ('PLATFORM_ADMIN', 'OSI', 'AUDITOR', 'EXTERNAL_AUDITOR', 'ADMINISTRATOR', 'PHYSICIAN', 'MEDICAL_INTERN', 'NURSE', 'LAB_TECHNICIAN', 'RECEPTIONIST', 'BILLING_CLERK', 'BILLING_SUPERVISOR', 'MARKETING_MANAGER', 'PATIENT', 'DEVOPS');

-- Admin. de plataforma (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PLATFORM_ADMIN' AND p.code IN (
    'tenancy.branch.change_status', 'tenancy.branch.create', 'tenancy.branch.read',
    'tenancy.branch.update', 'tenancy.organization.change_status', 'tenancy.organization.create',
    'tenancy.organization.read', 'tenancy.organization.update', 'tenancy.plan.read',
    'tenancy.service_offering.change_status', 'tenancy.service_offering.create', 'tenancy.service_offering.read',
    'tenancy.service_offering.update', 'tenancy.subscription.change_status', 'tenancy.subscription.read',
    'tenancy.subscription.read_history', 'tenancy.subscription.subscribe', 'tenancy.usage.read',
    'tenancy.usage.record');

-- Oficial Seguridad (OSI) (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'OSI' AND p.code IN (
    'audit.log.read', 'audit.log.verify', 'identity.clearance.manage',
    'identity.permission.grant', 'identity.role.assign', 'identity.role.assign_initial',
    'identity.role.manage', 'identity.role.read', 'identity.user.change_status',
    'identity.user.create', 'identity.user.read', 'identity.user.reset',
    'identity.user.unlock', 'identity.user.update');

-- Auditor interno (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'AUDITOR' AND p.code IN (
    'audit.log.export', 'audit.log.read', 'audit.log.verify');

-- Auditor externo (DAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'EXTERNAL_AUDITOR' AND p.code IN (
    'audit.log.read');

-- Admin. de organización (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'ADMINISTRATOR' AND p.code IN (
    'documents.consent_template.create', 'documents.consent_template.publish', 'documents.consent_template.read',
    'identity.role.assign_initial', 'identity.role.read', 'identity.user.create',
    'identity.user.read', 'notifications.rule.create', 'notifications.rule.read',
    'notifications.rule.update', 'notifications.template.create', 'notifications.template.read',
    'notifications.template.update', 'patients.merge.create', 'patients.merge.read',
    'patients.notification_preference.read', 'patients.notification_preference.update', 'patients.patient.create',
    'patients.patient.read', 'patients.patient.update', 'professionals.professional.read',
    'scheduling.appointment.attend', 'scheduling.appointment.cancel', 'scheduling.appointment.check_in',
    'scheduling.appointment.confirm', 'scheduling.appointment.create', 'scheduling.appointment.no_show',
    'scheduling.appointment.read', 'scheduling.appointment.reschedule', 'scheduling.availability.create',
    'scheduling.availability.deactivate', 'scheduling.availability.read', 'scheduling.block.create',
    'scheduling.block.delete', 'scheduling.block.read', 'scheduling.reservation.cancel',
    'scheduling.reservation.create', 'scheduling.reservation.mark_in_use', 'scheduling.reservation.read',
    'scheduling.reservation.release', 'scheduling.resource.change_status', 'scheduling.resource.create',
    'scheduling.resource.read', 'scheduling.resource.update', 'tenancy.branch.change_status',
    'tenancy.branch.create', 'tenancy.branch.read', 'tenancy.branch.update',
    'tenancy.organization.read', 'tenancy.organization.update', 'tenancy.plan.read',
    'tenancy.service_offering.change_status', 'tenancy.service_offering.create', 'tenancy.service_offering.read',
    'tenancy.service_offering.update', 'tenancy.subscription.change_status', 'tenancy.subscription.read',
    'tenancy.subscription.read_history', 'tenancy.subscription.subscribe', 'tenancy.usage.read',
    'tenancy.usage.record');

-- Médico (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PHYSICIAN' AND p.code IN (
    'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.study_type.read',
    'clinical.addendum.create', 'clinical.addendum.read', 'clinical.allergy.create',
    'clinical.allergy.read', 'clinical.allergy.update', 'clinical.background.create',
    'clinical.background.deactivate', 'clinical.background.read', 'clinical.diagnosis.create',
    'clinical.diagnosis.read', 'clinical.diagnosis.update', 'clinical.encounter.open',
    'clinical.encounter.read', 'clinical.encounter.sign', 'clinical.encounter.update_notes',
    'clinical.encounter.void', 'clinical.narrative.read', 'clinical.prescription.attach_document',
    'clinical.prescription.change_status', 'clinical.prescription.create', 'clinical.prescription.read',
    'clinical.procedure.create', 'clinical.procedure.read', 'clinical.record.change_status',
    'clinical.record.create', 'clinical.record.read', 'clinical.record.share',
    'clinical.study_order.change_status', 'clinical.study_order.create', 'clinical.study_order.read',
    'clinical.study_order.schedule_collection', 'clinical.study_result.create', 'clinical.study_result.read',
    'clinical.vital_sign.create', 'clinical.vital_sign.read', 'documents.consent.generate',
    'documents.consent.read', 'documents.consent.reject', 'documents.consent.request_otp',
    'documents.consent.sign', 'documents.consent_template.read', 'documents.document.classify',
    'documents.document.download', 'documents.document.link', 'documents.document.read',
    'documents.document.upload', 'patients.access_grant.confirm', 'patients.access_grant.read',
    'patients.access_grant.request', 'patients.access_grant.revoke', 'patients.notification_preference.read',
    'patients.patient.read', 'professionals.professional.read', 'scheduling.appointment.attend',
    'scheduling.appointment.cancel', 'scheduling.appointment.check_in', 'scheduling.appointment.confirm',
    'scheduling.appointment.create', 'scheduling.appointment.no_show', 'scheduling.appointment.read',
    'scheduling.appointment.reschedule', 'scheduling.availability.create', 'scheduling.availability.deactivate',
    'scheduling.availability.read', 'scheduling.block.create', 'scheduling.block.delete',
    'scheduling.block.read', 'scheduling.reservation.cancel', 'scheduling.reservation.create',
    'scheduling.reservation.mark_in_use', 'scheduling.reservation.read', 'scheduling.reservation.release',
    'scheduling.resource.change_status', 'scheduling.resource.create', 'scheduling.resource.read',
    'scheduling.resource.update', 'tenancy.branch.read', 'tenancy.service_offering.read');

-- Pasante de medicina (DAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'MEDICAL_INTERN' AND p.code IN (
    'clinical.addendum.read', 'clinical.allergy.create', 'clinical.allergy.read',
    'clinical.allergy.update', 'clinical.background.read', 'clinical.diagnosis.read',
    'clinical.encounter.read', 'clinical.prescription.read', 'clinical.procedure.read',
    'clinical.record.read', 'clinical.vital_sign.create', 'clinical.vital_sign.read',
    'patients.notification_preference.read', 'patients.patient.read');

-- Enfermería (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'NURSE' AND p.code IN (
    'catalogs.study_type.read', 'clinical.addendum.read', 'clinical.allergy.create',
    'clinical.allergy.read', 'clinical.allergy.update', 'clinical.background.create',
    'clinical.background.deactivate', 'clinical.background.read', 'clinical.diagnosis.read',
    'clinical.encounter.read', 'clinical.prescription.read', 'clinical.procedure.read',
    'clinical.record.change_status', 'clinical.record.create', 'clinical.record.read',
    'clinical.record.share', 'clinical.study_order.change_status', 'clinical.study_order.create',
    'clinical.study_order.read', 'clinical.study_order.schedule_collection', 'clinical.study_result.create',
    'clinical.study_result.read', 'clinical.vital_sign.create', 'clinical.vital_sign.read',
    'documents.document.classify', 'documents.document.download', 'documents.document.link',
    'documents.document.read', 'documents.document.upload', 'patients.notification_preference.read',
    'patients.patient.read', 'professionals.professional.read', 'scheduling.appointment.attend',
    'scheduling.appointment.cancel', 'scheduling.appointment.check_in', 'scheduling.appointment.confirm',
    'scheduling.appointment.create', 'scheduling.appointment.no_show', 'scheduling.appointment.read',
    'scheduling.appointment.reschedule', 'scheduling.availability.read', 'scheduling.block.read',
    'scheduling.reservation.cancel', 'scheduling.reservation.create', 'scheduling.reservation.mark_in_use',
    'scheduling.reservation.read', 'scheduling.reservation.release', 'scheduling.resource.change_status',
    'scheduling.resource.create', 'scheduling.resource.read', 'scheduling.resource.update',
    'tenancy.branch.read', 'tenancy.service_offering.read');

-- Laboratorio (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'LAB_TECHNICIAN' AND p.code IN (
    'catalogs.study_type.read', 'clinical.study_order.change_status', 'clinical.study_order.create',
    'clinical.study_order.read', 'clinical.study_order.schedule_collection', 'clinical.study_result.create',
    'clinical.study_result.read', 'documents.document.classify', 'documents.document.download',
    'documents.document.link', 'documents.document.read', 'documents.document.upload',
    'patients.notification_preference.read', 'patients.patient.read');

-- Recepción (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'RECEPTIONIST' AND p.code IN (
    'documents.consent.generate', 'documents.consent.read', 'documents.consent.reject',
    'documents.consent.request_otp', 'documents.consent.sign', 'documents.consent_template.read',
    'marketing.advertisement.create', 'marketing.advertisement.finish', 'marketing.advertisement.pause',
    'marketing.advertisement.publish', 'marketing.advertisement.read', 'marketing.advertisement.track',
    'marketing.advertisement.view', 'patients.access_grant.confirm', 'patients.access_grant.read',
    'patients.access_grant.request', 'patients.access_grant.revoke', 'patients.notification_preference.read',
    'patients.notification_preference.update', 'patients.patient.create', 'patients.patient.read',
    'patients.patient.update', 'professionals.professional.read', 'scheduling.appointment.attend',
    'scheduling.appointment.cancel', 'scheduling.appointment.check_in', 'scheduling.appointment.confirm',
    'scheduling.appointment.create', 'scheduling.appointment.no_show', 'scheduling.appointment.read',
    'scheduling.appointment.reschedule', 'scheduling.availability.read', 'scheduling.block.read',
    'scheduling.reservation.cancel', 'scheduling.reservation.create', 'scheduling.reservation.mark_in_use',
    'scheduling.reservation.read', 'scheduling.reservation.release', 'scheduling.resource.change_status',
    'scheduling.resource.create', 'scheduling.resource.read', 'scheduling.resource.update',
    'tenancy.branch.read', 'tenancy.service_offering.read');

-- Cajero 1 (RBAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'BILLING_CLERK' AND p.code IN (
    'billing.invoice.issue', 'billing.invoice.read', 'catalogs.tax.read',
    'marketing.coupon.redeem', 'marketing.coupon.validate', 'marketing.loyalty_account.read',
    'marketing.loyalty_movement.create', 'marketing.loyalty_program.create', 'marketing.loyalty_program.deactivate',
    'marketing.loyalty_program.read', 'marketing.promotion.create', 'marketing.promotion.deactivate',
    'marketing.promotion.read', 'patients.notification_preference.read', 'patients.patient.read',
    'tenancy.branch.read', 'tenancy.service_offering.read');

-- Cajero 2 (RBAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'BILLING_SUPERVISOR' AND p.code IN (
    'billing.invoice.read', 'billing.invoice.void');

-- Marketing (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'MARKETING_MANAGER' AND p.code IN (
    'marketing.advertisement.create', 'marketing.advertisement.finish', 'marketing.advertisement.pause',
    'marketing.advertisement.publish', 'marketing.advertisement.read', 'marketing.advertisement.track',
    'marketing.advertisement.view', 'marketing.campaign.build_audience', 'marketing.campaign.cancel',
    'marketing.campaign.create', 'marketing.campaign.finish', 'marketing.campaign.launch',
    'marketing.campaign.pause', 'marketing.campaign.read', 'marketing.campaign.schedule',
    'marketing.coupon.redeem', 'marketing.coupon.validate', 'marketing.loyalty_account.read',
    'marketing.loyalty_movement.create', 'marketing.loyalty_program.create', 'marketing.loyalty_program.deactivate',
    'marketing.loyalty_program.read', 'marketing.opt_out.update', 'marketing.patient_activity.read',
    'marketing.promotion.create', 'marketing.promotion.deactivate', 'marketing.promotion.read',
    'marketing.recipient.read', 'marketing.segment.create', 'marketing.segment.deactivate',
    'marketing.segment.delete', 'marketing.segment.evaluate', 'marketing.segment.read',
    'marketing.segment.update', 'notifications.rule.create', 'notifications.rule.read',
    'notifications.rule.update', 'notifications.template.create', 'notifications.template.read',
    'notifications.template.update', 'tenancy.service_offering.read');

-- Paciente (DAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PATIENT' AND p.code IN (
    'marketing.advertisement.track', 'marketing.advertisement.view', 'patients.self_access.confirm',
    'patients.self_access.read', 'patients.self_access.revoke');

-- Tecnología (DevOps) (MAC)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'DEVOPS' AND p.code IN (
    'infrastructure.database.manage', 'infrastructure.server.manage');

-- Las altas de personal pueden asignar solo roles operativos delegados, sin cambiar perfiles existentes.
DELETE d FROM role_delegation d JOIN `role` r ON r.id = d.role_id WHERE r.code = 'ADMINISTRATOR';
INSERT INTO role_delegation (role_id, assignable_role_id)
SELECT a.id, b.id FROM `role` a JOIN `role` b
WHERE a.code = 'ADMINISTRATOR' AND b.code IN ('PHYSICIAN', 'MEDICAL_INTERN', 'NURSE', 'LAB_TECHNICIAN',
    'RECEPTIONIST', 'BILLING_CLERK', 'BILLING_SUPERVISOR', 'MARKETING_MANAGER', 'PATIENT');
-- Emitir y anular son funciones incompatibles en una misma cuenta.
INSERT IGNORE INTO role_incompatibility (role_id, incompatible_role_id, reason)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), 'Separacion entre emision y anulacion de facturas'
FROM `role` a JOIN `role` b WHERE a.code = 'BILLING_CLERK' AND b.code = 'BILLING_SUPERVISOR';
-- Las asignaciones de plataforma no quedan vinculadas a una organizacion.
UPDATE user_role ur JOIN `role` r ON r.id = ur.role_id SET ur.organization_public_id = NULL WHERE r.scope = 'PLATFORM';
