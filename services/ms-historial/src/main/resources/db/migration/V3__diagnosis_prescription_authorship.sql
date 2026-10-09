-- =====================================================================================
-- ms-historial - autoria propia de diagnosis y prescription.
--
-- Hasta ahora ninguna de las dos tablas registraba quien la genero: el autor se asumia
-- implicitamente igual al profesional que abrio el encuentro (clinical_encounter.
-- health_professional_public_id). Nicole confirmo que ese heredado sigue siendo la
-- fuente de verdad (no se agrega un profesional distinto por registro), pero cada fila
-- debe guardar su propio valor -- mismo criterio ya usado en allergy/study_order/
-- performed_procedure: "UUID publico sin FK, con indice propio" (ver cabecera de
-- V1__create_schema.sql).
--
-- El backfill copia el profesional del encuentro padre para las filas ya existentes
-- antes de exigir NOT NULL, por si la migracion corre contra una base con datos.
-- =====================================================================================

ALTER TABLE diagnosis
    ADD COLUMN diagnosed_by_professional_public_id BINARY(16) NULL AFTER clinical_encounter_id;

UPDATE diagnosis d
    JOIN clinical_encounter c ON c.id = d.clinical_encounter_id
    SET d.diagnosed_by_professional_public_id = c.health_professional_public_id
    WHERE d.diagnosed_by_professional_public_id IS NULL;

ALTER TABLE diagnosis
    MODIFY COLUMN diagnosed_by_professional_public_id BINARY(16) NOT NULL;

CREATE INDEX ix_diagnosis_diagnosed_by_professional_public_id ON diagnosis (diagnosed_by_professional_public_id);

ALTER TABLE prescription
    ADD COLUMN prescribed_by_professional_public_id BINARY(16) NULL AFTER clinical_encounter_id;

UPDATE prescription p
    JOIN clinical_encounter c ON c.id = p.clinical_encounter_id
    SET p.prescribed_by_professional_public_id = c.health_professional_public_id
    WHERE p.prescribed_by_professional_public_id IS NULL;

ALTER TABLE prescription
    MODIFY COLUMN prescribed_by_professional_public_id BINARY(16) NOT NULL;

CREATE INDEX ix_prescription_prescribed_by_professional_public_id ON prescription (prescribed_by_professional_public_id);
