-- Catalogo de roles y permisos del MVP. Son datos de sistema, no datos demo.

INSERT INTO `role` (code, name, description, scope, is_system) VALUES
    ('PLATFORM_ADMIN', 'Administrador de plataforma', 'Opera MedicalSys por encima de las organizaciones', 'PLATFORM', TRUE),
    ('ADMINISTRATOR',  'Administrador',               'Administra una organizacion y sus sucursales',     'ORGANIZATION', TRUE),
    ('PHYSICIAN',      'Profesional de salud',        'Atiende pacientes y registra informacion clinica', 'ORGANIZATION', TRUE),
    ('RECEPTIONIST',   'Recepcionista',               'Gestiona agenda, admision y cobros de mostrador',  'ORGANIZATION', TRUE),
    ('PATIENT',        'Paciente',                    'Consulta su propia informacion y sus citas',       'ORGANIZATION', TRUE),
    ('BILLING_CLERK',  'Personal de facturacion',     'Emite y controla documentos fiscales',             'ORGANIZATION', TRUE);

INSERT INTO permission (code, module, description, is_sensitive) VALUES
    ('identity.user.read',        'IDENTITY',       'Consultar cuentas de usuario',                  FALSE),
    ('identity.user.write',       'IDENTITY',       'Crear y modificar cuentas de usuario',          TRUE),
    ('identity.role.assign',      'IDENTITY',       'Asignar o revocar roles a una cuenta',          TRUE),
    ('scheduling.appointment.read',  'SCHEDULING',  'Consultar citas y disponibilidad',              FALSE),
    ('scheduling.appointment.write', 'SCHEDULING',  'Crear, reprogramar y cancelar citas',           FALSE),
    ('scheduling.resource.read',     'SCHEDULING',  'Consultar recursos fisicos',                    FALSE),
    ('scheduling.resource.write',    'SCHEDULING',  'Administrar recursos fisicos y bloqueos',       FALSE),
    ('patients.patient.read',     'PATIENTS',       'Consultar datos administrativos del paciente',  FALSE),
    ('patients.patient.write',    'PATIENTS',       'Registrar y actualizar pacientes',              FALSE),
    ('professionals.professional.read', 'PROFESSIONALS', 'Consultar profesionales de salud',        FALSE),
    ('clinical.record.read',      'CLINICAL',       'Leer la historia clinica del paciente',         TRUE),
    ('clinical.record.write',     'CLINICAL',       'Registrar encuentros, diagnosticos y recetas',  TRUE),
    ('billing.invoice.read',      'BILLING',        'Consultar facturas',                            FALSE),
    ('billing.invoice.write',     'BILLING',        'Emitir facturas y notas',                       TRUE),
    ('administration.organization.manage', 'ADMINISTRATION', 'Administrar organizacion y sucursales', TRUE);

-- ADMINISTRATOR: todo dentro de su organizacion salvo la historia clinica.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'ADMINISTRATOR'
  AND p.code IN ('identity.user.read','identity.user.write','identity.role.assign',
                 'scheduling.appointment.read','scheduling.appointment.write',
                 'scheduling.resource.read','scheduling.resource.write',
                 'patients.patient.read','patients.patient.write',
                 'professionals.professional.read',
                 'billing.invoice.read','billing.invoice.write',
                 'administration.organization.manage');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PLATFORM_ADMIN' AND p.module IN ('IDENTITY','ADMINISTRATION');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'RECEPTIONIST'
  AND p.code IN ('scheduling.appointment.read','scheduling.appointment.write',
                 'scheduling.resource.read',
                 'patients.patient.read','patients.patient.write',
                 'professionals.professional.read',
                 'billing.invoice.read');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PHYSICIAN'
  AND p.code IN ('scheduling.appointment.read','scheduling.appointment.write',
                 'patients.patient.read',
                 'professionals.professional.read',
                 'clinical.record.read','clinical.record.write');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'BILLING_CLERK'
  AND p.code IN ('billing.invoice.read','billing.invoice.write','patients.patient.read');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'PATIENT'
  AND p.code IN ('scheduling.appointment.read');
