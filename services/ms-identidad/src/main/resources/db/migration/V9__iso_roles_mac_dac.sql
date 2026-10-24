-- ISO-01 - Roles por funcion (ISO/IEC 27001:2022 A.5.2, A.5.3, A.5.15, A.5.18; ISO 27799) y tres
-- modelos de control de acceso que se aplican juntos:
--
--   * RBAC: el rol decide que acciones (permiso modulo.recurso.accion) puede ejecutar la cuenta.
--   * MAC:  cada permiso tiene una clasificacion (PUBLIC < INTERNAL < CONFIDENTIAL < RESTRICTED,
--           la escala de la Matriz de Clasificacion de la Informacion mas un nivel para el secreto
--           medico y la administracion de accesos) y cada cuenta un nivel de autorizacion
--           (clearance). El token solo lleva los permisos del rol cuya clasificacion no supera el
--           nivel de la cuenta. El nivel lo fija unicamente el OSI (identity.clearance.manage): ni
--           el administrador del centro ni el propio usuario pueden subirlo.
--   * DAC:  el paciente, dueno de sus datos, decide que otra organizacion los ve, confirmando un
--           OTP (patients.access_grant.*, ms-pacientes). No cambia aqui.
--
-- El OSI (Oficial de Seguridad de la Informacion) es el rol que otorga permisos: tiene
-- identity.permission.grant, con el que puede dar a un rol permisos que el mismo no ejerce
-- (custodia sin uso). PLATFORM_ADMIN deja de gestionar roles y permisos, y ADMINISTRATOR deja de
-- leer la historia clinica y la bitacora (desviaciones del anexo D de PO-02).
--
-- La asignacion rol -> permiso y Matriz_Roles_MedicalSys.xlsx salen del mismo generador.

-- --------------------------------------------------------------------- MAC: clasificacion
ALTER TABLE permission ADD COLUMN classification VARCHAR(20) NOT NULL DEFAULT 'INTERNAL' AFTER is_sensitive;
ALTER TABLE permission ADD CONSTRAINT ck_permission_classification
    CHECK (classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'));

INSERT INTO permission (code, module, description, is_sensitive) VALUES
    ('identity.permission.grant', 'IDENTITY',
     'Otorgar a un rol cualquier permiso del catalogo sin tenerlo (OSI, ISO 27001 A.5.15/A.5.18)', TRUE),
    ('identity.clearance.manage', 'IDENTITY',
     'Fijar el nivel de autorizacion MAC de una cuenta (ISO 27001 A.5.12)', TRUE);

-- Secreto medico y administracion de accesos: RESTRICTED. Lo publicado a pacientes: PUBLIC. Los
-- modulos que la matriz de clasificacion declara confidenciales, y todo permiso sensible:
-- CONFIDENTIAL. El resto (tarifario, catalogos, profesionales, campanas): INTERNAL.
UPDATE permission SET classification = CASE
    WHEN code IN ('clinical.narrative.read', 'identity.role.manage', 'identity.permission.grant',
                  'identity.clearance.manage') THEN 'RESTRICTED'
    WHEN code IN ('marketing.advertisement.view', 'marketing.advertisement.track', 'tenancy.plan.read') THEN 'PUBLIC'
    WHEN is_sensitive OR module IN ('CLINICAL', 'PATIENTS', 'AUDIT', 'BILLING', 'DOCUMENT_MANAGEMENT',
                                    'SCHEDULING', 'IDENTITY') THEN 'CONFIDENTIAL'
    ELSE 'INTERNAL'
END;

-- --------------------------------------------------------------------- MAC: autorizacion
-- Una cuenta nueva nace INTERNAL. Las existentes reciben el nivel que hoy necesitan sus roles,
-- calculado al final, para que la migracion no le quite a nadie lo que ya usaba.
ALTER TABLE user_account ADD COLUMN clearance_level VARCHAR(20) NOT NULL DEFAULT 'INTERNAL' AFTER status;
ALTER TABLE user_account ADD CONSTRAINT ck_user_account_clearance_level
    CHECK (clearance_level IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'));

-- --------------------------------------------------------------------- roles
-- OSI y AUDITOR pudieron crearse antes desde la API como roles propios: pasan a ser de sistema.
INSERT INTO `role` (code, name, description, scope, is_system, is_active) VALUES
    ('PLATFORM_ADMIN', 'Administrador de plataforma', 'Opera MedicalSys por encima de las organizaciones: organizaciones, planes y cuentas. Sin acceso clinico ni gestion de permisos', 'PLATFORM', TRUE, TRUE),
    ('OSI', 'Oficial de Seguridad de la Informacion', 'Custodia los accesos: define roles y permisos, fija niveles MAC, asigna roles y revisa la bitacora. No opera datos clinicos ni de negocio', 'PLATFORM', TRUE, TRUE),
    ('AUDITOR', 'Auditor interno', 'Verifica de forma independiente la bitacora y la matriz de accesos. Solo lectura', 'ORGANIZATION', TRUE, TRUE),
    ('ADMINISTRATOR', 'Administrador de organizacion', 'Administra su centro: personal, sucursales, tarifario, agenda y profesionales. Sin historia clinica ni bitacora', 'ORGANIZATION', TRUE, TRUE),
    ('PHYSICIAN', 'Profesional de salud', 'Atiende pacientes: registra, firma y consulta la historia clinica, incluida la narrativa cifrada', 'ORGANIZATION', TRUE, TRUE),
    ('NURSE', 'Enfermeria', 'Triaje y cuidados: signos vitales, alergias, procedimientos y toma de muestras. Sin narrativa cifrada', 'ORGANIZATION', TRUE, TRUE),
    ('LAB_TECHNICIAN', 'Tecnico de laboratorio', 'Procesa ordenes de estudio y registra sus resultados', 'ORGANIZATION', TRUE, TRUE),
    ('RECEPTIONIST', 'Recepcionista', 'Admision y agenda: registra pacientes, gestiona citas, consentimientos y avisos. Sin historia clinica ni cobros', 'ORGANIZATION', TRUE, TRUE),
    ('BILLING_CLERK', 'Personal de facturacion', 'Caja: emite y consulta comprobantes, canjea cupones y puntos', 'ORGANIZATION', TRUE, TRUE),
    ('MARKETING_MANAGER', 'Gestor de marketing', 'Campanas, segmentos, cupones, anuncios y fidelizacion. Sin acceso a datos clinicos ni al padron', 'ORGANIZATION', TRUE, TRUE),
    ('PATIENT', 'Paciente', 'Autoservicio: anuncios, saldo de fidelizacion y baja de comunicaciones', 'ORGANIZATION', TRUE, TRUE)
AS incoming
ON DUPLICATE KEY UPDATE
    name = incoming.name,
    description = incoming.description,
    scope = incoming.scope,
    is_system = TRUE,
    is_active = TRUE;

-- Un rol de plataforma no se asigna dentro de una organizacion.
UPDATE user_role ur JOIN `role` r ON r.id = ur.role_id
SET ur.organization_public_id = NULL
WHERE r.code = 'OSI';

-- --------------------------------------------------------------------- RBAC: rol -> permiso
DELETE rp FROM role_permission rp JOIN `role` r ON r.id = rp.role_id
WHERE r.code IN ('PLATFORM_ADMIN', 'OSI', 'AUDITOR', 'ADMINISTRATOR', 'PHYSICIAN', 'NURSE', 'LAB_TECHNICIAN',
                 'RECEPTIONIST', 'BILLING_CLERK', 'MARKETING_MANAGER', 'PATIENT');

-- PLATFORM_ADMIN: 33 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PLATFORM_ADMIN' AND p.code IN (
    'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
    'catalogs.study_type.read', 'catalogs.tax.read', 'identity.role.assign',
    'identity.role.read', 'identity.user.change_status', 'identity.user.create',
    'identity.user.read', 'identity.user.reset', 'identity.user.unlock',
    'identity.user.update', 'professionals.professional.read', 'tenancy.branch.change_status',
    'tenancy.branch.create', 'tenancy.branch.read', 'tenancy.branch.update',
    'tenancy.organization.change_status', 'tenancy.organization.create', 'tenancy.organization.read',
    'tenancy.organization.update', 'tenancy.plan.read', 'tenancy.service_offering.change_status',
    'tenancy.service_offering.create', 'tenancy.service_offering.read', 'tenancy.service_offering.update',
    'tenancy.subscription.change_status', 'tenancy.subscription.read', 'tenancy.subscription.read_history',
    'tenancy.subscription.subscribe', 'tenancy.usage.read', 'tenancy.usage.record');

-- OSI: 15 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'OSI' AND p.code IN (
    'audit.log.read', 'audit.log.verify', 'identity.clearance.manage',
    'identity.permission.grant', 'identity.role.assign', 'identity.role.manage',
    'identity.role.read', 'identity.user.change_status', 'identity.user.create',
    'identity.user.read', 'identity.user.reset', 'identity.user.unlock',
    'identity.user.update', 'tenancy.branch.read', 'tenancy.organization.read');

-- AUDITOR: 7 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'AUDITOR' AND p.code IN (
    'audit.log.export', 'audit.log.read', 'audit.log.verify',
    'identity.role.read', 'identity.user.read', 'tenancy.branch.read',
    'tenancy.organization.read');

-- ADMINISTRATOR: 58 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'ADMINISTRATOR' AND p.code IN (
    'billing.invoice.read', 'catalogs.icd10.read', 'catalogs.medication.read',
    'catalogs.specialty.read', 'catalogs.study_type.read', 'catalogs.tax.read',
    'documents.consent_template.create', 'documents.consent_template.publish', 'documents.consent_template.read',
    'identity.role.assign', 'identity.role.read', 'identity.user.change_status',
    'identity.user.create', 'identity.user.read', 'identity.user.reset',
    'identity.user.unlock', 'identity.user.update', 'notifications.notification.read',
    'notifications.rule.create', 'notifications.rule.read', 'notifications.rule.update',
    'notifications.template.create', 'notifications.template.read', 'notifications.template.update',
    'patients.merge.create', 'patients.merge.read', 'patients.patient.read',
    'professionals.affiliation.change_status', 'professionals.professional.create', 'professionals.professional.read',
    'professionals.professional.update', 'scheduling.appointment.read', 'scheduling.availability.create',
    'scheduling.availability.deactivate', 'scheduling.availability.read', 'scheduling.block.create',
    'scheduling.block.delete', 'scheduling.block.read', 'scheduling.reservation.read',
    'scheduling.resource.change_status', 'scheduling.resource.create', 'scheduling.resource.read',
    'scheduling.resource.update', 'tenancy.branch.change_status', 'tenancy.branch.create',
    'tenancy.branch.read', 'tenancy.branch.update', 'tenancy.organization.read',
    'tenancy.organization.update', 'tenancy.plan.read', 'tenancy.service_offering.change_status',
    'tenancy.service_offering.create', 'tenancy.service_offering.read', 'tenancy.service_offering.update',
    'tenancy.subscription.read', 'tenancy.subscription.read_history', 'tenancy.subscription.subscribe',
    'tenancy.usage.read');

-- PHYSICIAN: 76 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PHYSICIAN' AND p.code IN (
    'catalogs.icd10.read', 'catalogs.medication.read', 'catalogs.specialty.read',
    'catalogs.study_type.read', 'catalogs.tax.read', 'clinical.addendum.create',
    'clinical.addendum.read', 'clinical.allergy.create', 'clinical.allergy.read',
    'clinical.allergy.update', 'clinical.background.create', 'clinical.background.deactivate',
    'clinical.background.read', 'clinical.diagnosis.create', 'clinical.diagnosis.read',
    'clinical.diagnosis.update', 'clinical.encounter.open', 'clinical.encounter.read',
    'clinical.encounter.sign', 'clinical.encounter.update_notes', 'clinical.encounter.void',
    'clinical.narrative.read', 'clinical.prescription.attach_document', 'clinical.prescription.change_status',
    'clinical.prescription.create', 'clinical.prescription.read', 'clinical.procedure.create',
    'clinical.procedure.read', 'clinical.record.change_status', 'clinical.record.create',
    'clinical.record.read', 'clinical.record.share', 'clinical.study_order.change_status',
    'clinical.study_order.create', 'clinical.study_order.read', 'clinical.study_order.schedule_collection',
    'clinical.study_result.create', 'clinical.study_result.read', 'clinical.vital_sign.create',
    'clinical.vital_sign.read', 'documents.consent.generate', 'documents.consent.read',
    'documents.consent.reject', 'documents.consent.request_otp', 'documents.consent.sign',
    'documents.consent_template.read', 'documents.document.classify', 'documents.document.download',
    'documents.document.link', 'documents.document.read', 'documents.document.upload',
    'patients.access_grant.confirm', 'patients.access_grant.read', 'patients.access_grant.request',
    'patients.access_grant.revoke', 'patients.notification_preference.read', 'patients.patient.read',
    'professionals.professional.read', 'scheduling.appointment.attend', 'scheduling.appointment.no_show',
    'scheduling.appointment.read', 'scheduling.availability.create', 'scheduling.availability.deactivate',
    'scheduling.availability.read', 'scheduling.block.create', 'scheduling.block.delete',
    'scheduling.block.read', 'scheduling.reservation.cancel', 'scheduling.reservation.create',
    'scheduling.reservation.mark_in_use', 'scheduling.reservation.read', 'scheduling.reservation.release',
    'scheduling.resource.read', 'tenancy.branch.read', 'tenancy.organization.read',
    'tenancy.service_offering.read');

-- NURSE: 25 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'NURSE' AND p.code IN (
    'catalogs.icd10.read', 'catalogs.medication.read', 'clinical.allergy.create',
    'clinical.allergy.read', 'clinical.allergy.update', 'clinical.diagnosis.read',
    'clinical.encounter.read', 'clinical.prescription.read', 'clinical.procedure.create',
    'clinical.procedure.read', 'clinical.record.read', 'clinical.study_order.read',
    'clinical.study_order.schedule_collection', 'clinical.vital_sign.create', 'clinical.vital_sign.read',
    'documents.consent.read', 'documents.document.read', 'patients.patient.read',
    'scheduling.appointment.check_in', 'scheduling.appointment.read', 'scheduling.reservation.mark_in_use',
    'scheduling.reservation.read', 'scheduling.resource.read', 'tenancy.branch.read',
    'tenancy.organization.read');

-- LAB_TECHNICIAN: 13 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'LAB_TECHNICIAN' AND p.code IN (
    'catalogs.study_type.read', 'clinical.study_order.change_status', 'clinical.study_order.read',
    'clinical.study_order.schedule_collection', 'clinical.study_result.create', 'clinical.study_result.read',
    'documents.document.download', 'documents.document.link', 'documents.document.read',
    'documents.document.upload', 'patients.patient.read', 'tenancy.branch.read',
    'tenancy.organization.read');

-- RECEPTIONIST: 44 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'RECEPTIONIST' AND p.code IN (
    'catalogs.specialty.read', 'documents.consent.generate', 'documents.consent.read',
    'documents.consent.reject', 'documents.consent.request_otp', 'documents.consent.sign',
    'documents.consent_template.read', 'marketing.advertisement.view', 'marketing.coupon.validate',
    'marketing.opt_out.update', 'notifications.notification.create', 'notifications.notification.dispatch',
    'notifications.notification.read', 'notifications.notification.retry', 'notifications.template.read',
    'notifications.whatsapp.send', 'patients.access_grant.confirm', 'patients.access_grant.read',
    'patients.access_grant.request', 'patients.access_grant.revoke', 'patients.notification_preference.read',
    'patients.notification_preference.update', 'patients.patient.create', 'patients.patient.read',
    'patients.patient.update', 'professionals.professional.read', 'scheduling.appointment.attend',
    'scheduling.appointment.cancel', 'scheduling.appointment.check_in', 'scheduling.appointment.confirm',
    'scheduling.appointment.create', 'scheduling.appointment.no_show', 'scheduling.appointment.read',
    'scheduling.appointment.reschedule', 'scheduling.availability.read', 'scheduling.block.read',
    'scheduling.reservation.cancel', 'scheduling.reservation.create', 'scheduling.reservation.read',
    'scheduling.reservation.release', 'scheduling.resource.read', 'tenancy.branch.read',
    'tenancy.organization.read', 'tenancy.service_offering.read');

-- BILLING_CLERK: 12 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'BILLING_CLERK' AND p.code IN (
    'billing.invoice.issue', 'billing.invoice.read', 'catalogs.tax.read',
    'marketing.coupon.redeem', 'marketing.coupon.validate', 'marketing.loyalty_account.read',
    'marketing.loyalty_movement.create', 'marketing.promotion.read', 'patients.patient.read',
    'tenancy.branch.read', 'tenancy.organization.read', 'tenancy.service_offering.read');

-- MARKETING_MANAGER: 41 permisos
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
    'marketing.segment.update', 'notifications.rule.read', 'notifications.template.create',
    'notifications.template.read', 'notifications.template.update', 'tenancy.branch.read',
    'tenancy.organization.read', 'tenancy.service_offering.read');

-- PATIENT: 4 permisos
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = 'PATIENT' AND p.code IN (
    'marketing.advertisement.track', 'marketing.advertisement.view', 'marketing.loyalty_account.read',
    'marketing.opt_out.update');

-- --------------------------------------------------------------------- segregacion de funciones
-- Cada par se guarda con el id menor primero (ck_role_incompatibility_order).
INSERT IGNORE INTO role_incompatibility (role_id, incompatible_role_id, reason)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), pair.reason
FROM (
    SELECT 'PHYSICIAN' AS role_code, 'BILLING_CLERK' AS other_code, 'Quien registra la atencion no emite su cobro' AS reason
    UNION ALL
    SELECT 'PLATFORM_ADMIN' AS role_code, 'PHYSICIAN' AS other_code, 'Quien opera la plataforma no ejerce actos medicos dentro de ella' AS reason
    UNION ALL
    SELECT 'OSI' AS role_code, 'PLATFORM_ADMIN' AS other_code, 'Quien custodia los accesos no opera la plataforma que controla' AS reason
    UNION ALL
    SELECT 'OSI' AS role_code, 'ADMINISTRATOR' AS other_code, 'Quien custodia los accesos no administra un centro' AS reason
    UNION ALL
    SELECT 'OSI' AS role_code, 'PHYSICIAN' AS other_code, 'Quien custodia los accesos no ejerce funciones clinicas' AS reason
    UNION ALL
    SELECT 'AUDITOR' AS role_code, 'ADMINISTRATOR' AS other_code, 'Quien audita no administra lo que audita' AS reason
    UNION ALL
    SELECT 'AUDITOR' AS role_code, 'OSI' AS other_code, 'Quien audita no custodia los accesos que audita' AS reason
    UNION ALL
    SELECT 'AUDITOR' AS role_code, 'PLATFORM_ADMIN' AS other_code, 'Quien audita no opera la plataforma' AS reason
    UNION ALL
    SELECT 'MARKETING_MANAGER' AS role_code, 'PHYSICIAN' AS other_code, 'Quien segmenta campanas no accede a la historia clinica' AS reason
    UNION ALL
    SELECT 'MARKETING_MANAGER' AS role_code, 'NURSE' AS other_code, 'Quien segmenta campanas no accede a la historia clinica' AS reason
) pair
JOIN `role` a ON a.code = pair.role_code
JOIN `role` b ON b.code = pair.other_code;

-- --------------------------------------------------------------------- delegacion
-- El administrador del centro da de alta a su personal operativo. ADMINISTRATOR, AUDITOR y los
-- roles de plataforma los asigna la plataforma o el OSI: nadie fabrica a su propio par ni a su auditor.
DELETE d FROM role_delegation d JOIN `role` r ON r.id = d.role_id WHERE r.code = 'ADMINISTRATOR';
INSERT INTO role_delegation (role_id, assignable_role_id)
SELECT grantor.id, assignable.id FROM `role` grantor JOIN `role` assignable
WHERE grantor.code = 'ADMINISTRATOR'
  AND assignable.code IN ('PHYSICIAN', 'NURSE', 'LAB_TECHNICIAN', 'RECEPTIONIST', 'BILLING_CLERK', 'MARKETING_MANAGER', 'PATIENT');

-- --------------------------------------------------------------------- MAC: cuentas existentes
UPDATE user_account u
JOIN (
    SELECT ur.user_account_id,
           MAX(CASE p.classification WHEN 'PUBLIC' THEN 1 WHEN 'INTERNAL' THEN 2
                                     WHEN 'CONFIDENTIAL' THEN 3 WHEN 'RESTRICTED' THEN 4 END) AS level
    FROM user_role ur
    JOIN role_permission rp ON rp.role_id = ur.role_id
    JOIN permission p ON p.id = rp.permission_id
    GROUP BY ur.user_account_id
) needed ON needed.user_account_id = u.id
SET u.clearance_level = ELT(needed.level, 'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
WHERE needed.level > 2;
