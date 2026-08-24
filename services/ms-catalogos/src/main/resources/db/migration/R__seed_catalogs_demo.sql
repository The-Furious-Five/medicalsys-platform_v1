-- =============================================================
-- ms-catalogos · catalogs_db
-- R__seed_catalogs_demo.sql  (repeatable, idempotente)
-- Datos de demostración para el entorno local.
-- Regla: INSERT IGNORE para no duplicar al reiniciar.
-- =============================================================

-- -------------------------------------------------------
-- Especialidades: 2 requeridas por la guía
-- -------------------------------------------------------
INSERT IGNORE INTO specialty (code, name, active) VALUES
    ('MEDICINA_GENERAL', 'Medicina General',  TRUE),
    ('RADIOLOGIA',       'Radiología',         TRUE);

-- -------------------------------------------------------
-- CIE-10: 3 códigos ficticios/educativos
-- -------------------------------------------------------
INSERT IGNORE INTO icd10_catalog
    (code, description, chapter, category, applicable_sex, active)
VALUES
    ('J00',   'Rinofaringitis aguda (resfriado común)',
              'X - Enfermedades del sistema respiratorio',
              'J00-J06 Infecciones agudas tracto respiratorio superior',
              'BOTH', TRUE),
    ('Z00.0', 'Examen médico general de adulto',
              'XXI - Factores que influyen en el estado de salud',
              'Z00-Z13 Consultas para examen y screening',
              'BOTH', TRUE),
    ('M54.5', 'Lumbago no especificado',
              'XIII - Enfermedades del sistema osteomuscular',
              'M50-M54 Dorsopatías',
              'BOTH', TRUE);

-- -------------------------------------------------------
-- study_type: X_RAY consumido por ms-historial
-- -------------------------------------------------------
INSERT IGNORE INTO study_type
    (code, name, category, requires_fasting, generates_image, active)
VALUES
    ('X_RAY', 'Radiografía (rayos X)', 'IMAGING', FALSE, TRUE, TRUE);

-- -------------------------------------------------------
-- tax_catalog: CONSULTA y RAYOS_X simulados (DEMO)
-- Descripción contiene "SIMULADO" como indica la guía.
-- -------------------------------------------------------
INSERT IGNORE INTO tax_catalog
    (catalog_type, code, description, active)
VALUES
    ('PRODUCT_SERVICE', 'CONSULTA',
     'SERVICIO: Consulta médica general - SIMULADO',
     TRUE),
    ('PRODUCT_SERVICE', 'RAYOS_X',
     'SERVICIO: Estudio de rayos X - SIMULADO',
     TRUE);
