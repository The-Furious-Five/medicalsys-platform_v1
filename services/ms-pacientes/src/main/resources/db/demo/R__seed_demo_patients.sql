-- Datos exclusivamente ficticios para desarrollo y demostracion local.
-- Esta ubicacion solo se habilita desde compose.yaml mediante FLYWAY_LOCATIONS.
-- INSERT IGNORE permite que la migracion repetible no sobrescriba cambios hechos
-- manualmente durante una demostracion.

-- ------------------------------------------------------------------ pacientes
INSERT IGNORE INTO patient (
    public_id,
    user_account_public_id,
    document_type,
    document_number,
    document_complement,
    issued_department,
    given_names,
    paternal_surname,
    maternal_surname,
    birth_date,
    sex,
    marital_status,
    blood_type,
    occupation,
    phone_e164,
    alternate_phone,
    email,
    encrypted_address,
    city,
    department,
    emergency_contact_name,
    emergency_contact_phone,
    emergency_contact_relationship,
    encryption_key_public_id,
    data_consent_at,
    status,
    death_date
) VALUES
(
    UUID_TO_BIN('10000000-0000-4000-8000-000000000001'),
    UUID_TO_BIN('20000000-0000-4000-8000-000000000001'),
    'CI', '123', '', 'LA_PAZ',
    'Maria Elena', 'Ficticia', 'Principal', '1995-04-10',
    'F', 'SINGLE', 'O+', 'Arquitecta',
    '+59170000001', '+59170000002', 'maria.demo@example.invalid',
    UNHEX('01020304'), 'La Paz', 'LA_PAZ',
    'Contacto Uno', '+59170000003', 'Madre',
    UUID_TO_BIN('30000000-0000-4000-8000-000000000001'),
    '2026-08-25 14:00:00', 'ACTIVE', NULL
),
(
    UUID_TO_BIN('10000000-0000-4000-8000-000000000002'),
    NULL,
    'CI', '123', 'A', 'LA_PAZ',
    'Ana Complemento', 'Demostracion', NULL, '1996-05-11',
    'F', 'MARRIED', 'A+', 'Ingeniera',
    '+59170000004', NULL, 'ana.complemento@example.invalid',
    NULL, 'El Alto', 'LA_PAZ',
    NULL, NULL, NULL,
    NULL, NULL, 'ACTIVE', NULL
),
(
    UUID_TO_BIN('10000000-0000-4000-8000-000000000004'),
    NULL,
    'PASSPORT', 'DEMO-PASS-004', '', 'FOREIGN',
    'Alex', 'Foreign', 'Sample', '1988-09-20',
    'UNDECLARED', 'UNDECLARED', 'UNKNOWN', 'Consultor',
    '+59170000006', NULL, 'alex.foreign@example.invalid',
    NULL, 'Santa Cruz', 'SANTA_CRUZ',
    NULL, NULL, NULL,
    NULL, NULL, 'ACTIVE', NULL
),
(
    UUID_TO_BIN('10000000-0000-4000-8000-000000000005'),
    NULL,
    'CI', 'DEMO-MERGED-005', '', 'COCHABAMBA',
    'Registro', 'Duplicado', 'Fusionado', '1995-04-10',
    'F', 'SINGLE', 'O+', NULL,
    NULL, NULL, NULL,
    NULL, 'Cochabamba', 'COCHABAMBA',
    NULL, NULL, NULL,
    NULL, NULL, 'INACTIVE', NULL
),
(
    UUID_TO_BIN('10000000-0000-4000-8000-000000000006'),
    NULL,
    'CI', 'DEMO-DECEASED-006', '', 'ORURO',
    'Paciente', 'Historico', 'Ejemplo', '1940-02-15',
    'M', 'WIDOWED', 'B-', 'Jubilado',
    NULL, NULL, NULL,
    NULL, 'Oruro', 'ORURO',
    NULL, NULL, NULL,
    NULL, NULL, 'DECEASED', '2020-11-08'
);

-- Paciente menor con una FK local hacia su tutor ficticio.
INSERT IGNORE INTO patient (
    public_id,
    document_type,
    document_number,
    document_complement,
    issued_department,
    given_names,
    paternal_surname,
    maternal_surname,
    birth_date,
    sex,
    marital_status,
    blood_type,
    city,
    department,
    emergency_contact_name,
    emergency_contact_phone,
    emergency_contact_relationship,
    guardian_patient_id,
    status
)
SELECT
    UUID_TO_BIN('10000000-0000-4000-8000-000000000003'),
    'BIRTH_CERTIFICATE', 'DEMO-BIRTH-003', '', 'LA_PAZ',
    'Lucas', 'Menor', 'Ficticio', '2015-07-12',
    'M', 'UNDECLARED', 'UNKNOWN', 'La Paz', 'LA_PAZ',
    'Maria Elena Ficticia Principal', '+59170000001', 'Tutora',
    guardian.id, 'ACTIVE'
FROM patient guardian
WHERE guardian.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');

-- ------------------------------------------------ preferencias de notificacion
INSERT IGNORE INTO notification_preference (
    patient_id, channel, category, opt_in, consent_source, opt_in_at, opt_out_at
)
SELECT id, 'WHATSAPP', 'CLINICAL', TRUE, 'RECEPTION', '2026-08-25 14:05:00', NULL
FROM patient
WHERE public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');

INSERT IGNORE INTO notification_preference (
    patient_id, channel, category, opt_in, consent_source, opt_in_at, opt_out_at
)
SELECT id, 'EMAIL', 'ADMINISTRATIVE', TRUE, 'WEB_FORM', '2026-08-25 14:06:00', NULL
FROM patient
WHERE public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');

INSERT IGNORE INTO notification_preference (
    patient_id, channel, category, opt_in, consent_source, opt_in_at, opt_out_at
)
SELECT id, 'WHATSAPP', 'ADMINISTRATIVE', FALSE, 'RECEPTION', NULL, NULL
FROM patient
WHERE public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000002');

-- ----------------------------------------------------- fusion ya documentada
INSERT IGNORE INTO patient_merge (
    surviving_patient_id,
    merged_patient_id,
    reason,
    merged_by_user_public_id,
    merged_at,
    event_public_id
)
SELECT
    surviving.id,
    merged.id,
    'Registro duplicado ficticio incluido en el seed local',
    UUID_TO_BIN('20000000-0000-4000-8000-000000000099'),
    '2026-08-25 14:10:00',
    UUID_TO_BIN('40000000-0000-4000-8000-000000000001')
FROM patient surviving
JOIN patient merged
  ON merged.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000005')
WHERE surviving.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');
