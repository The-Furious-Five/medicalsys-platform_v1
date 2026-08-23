-- =====================================================================================
-- Semilla de demostracion de ms-historial. Solo se aplica con el perfil "demo".
--
-- Reglas del sprint:
--   * Datos ficticios evidentes; ninguna persona, CI ni telefono real.
--   * Migracion repetible e idempotente: reejecutarla no duplica filas.
--   * No se siembra narrativa clinica: el ciphertext lo produce ms-criptografia en
--     tiempo de ejecucion, nunca un script.
--
-- Los UUID de organizacion, paciente, sucursal y clave son los valores acordados para el
-- entorno local. Si el equipo cambia las semillas de tenencia, pacientes o criptografia,
-- se actualizan aqui y se reejecuta la migracion repetible.
-- =====================================================================================

SET @organization_public_id = UUID_TO_BIN('00000000-0000-4000-8000-0000000000a1');
SET @patient_public_id      = UUID_TO_BIN('00000000-0000-4000-8000-0000000000b1');
SET @branch_public_id       = UUID_TO_BIN('00000000-0000-4000-8000-0000000000a2');
SET @encryption_key_id      = UUID_TO_BIN('00000000-0000-4000-8000-0000000000c4');
SET @record_public_id       = UUID_TO_BIN('00000000-0000-4000-8000-0000000000d1');

INSERT INTO medical_record (public_id, organization_public_id, patient_public_id, record_number,
                            opened_on, opening_branch_public_id, encryption_key_public_id, status)
VALUES (@record_public_id, @organization_public_id, @patient_public_id, 'EXP-DEMO-000001',
        CURRENT_DATE, @branch_public_id, @encryption_key_id, 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

SET @record_id = (SELECT id FROM medical_record WHERE public_id = @record_public_id);

INSERT INTO allergy (medical_record_id, type, substance, reaction, severity, status)
VALUES (@record_id, 'MEDICATION', 'PENICILINA (DEMO)', 'Urticaria simulada', 'MODERATE', 'ACTIVE')
ON DUPLICATE KEY UPDATE severity = VALUES(severity);

INSERT INTO medical_background (medical_record_id, type, title, icd10_code, active)
SELECT @record_id, 'FAMILY', 'Antecedente familiar de demostracion', NULL, TRUE
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM medical_background
    WHERE medical_record_id = @record_id
      AND type = 'FAMILY'
      AND title = 'Antecedente familiar de demostracion');
