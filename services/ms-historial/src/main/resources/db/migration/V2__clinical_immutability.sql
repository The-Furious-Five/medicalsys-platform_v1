-- =====================================================================================
-- ms-historial - garantia final de inmutabilidad clinica (tarea MVP-09, Dia 4).
--
-- La capa de servicio ya rechaza la edicion con 422 ENCOUNTER_NOT_EDITABLE para dar un
-- mensaje util. Esta migracion agrega la garantia definitiva en la base: aunque alguien
-- ejecute un UPDATE directo con el usuario del servicio, un encuentro firmado no se
-- sobrescribe. La unica transicion permitida sobre un encuentro SIGNED es su anulacion,
-- que conserva la fila y su trazabilidad (soft delete del diccionario).
--
-- La correccion posterior del contenido no se sobrescribe: se registra como
-- encounter_addendum, que a su vez es inmutable.
-- =====================================================================================

DELIMITER $$

CREATE TRIGGER trg_clinical_encounter_immutable_on_update
    BEFORE UPDATE ON clinical_encounter
    FOR EACH ROW
BEGIN
    IF OLD.status = 'SIGNED' THEN
        -- Anular es la unica transicion admitida despues de firmar.
        IF NEW.status = 'VOIDED' THEN
            IF NOT (NEW.medical_record_id            <=> OLD.medical_record_id
                AND NEW.public_id                    <=> OLD.public_id
                AND NEW.organization_public_id       <=> OLD.organization_public_id
                AND NEW.appointment_public_id        <=> OLD.appointment_public_id
                AND NEW.health_professional_public_id <=> OLD.health_professional_public_id
                AND NEW.branch_public_id             <=> OLD.branch_public_id
                AND NEW.type                         <=> OLD.type
                AND NEW.starts_at                    <=> OLD.starts_at
                AND NEW.ends_at                      <=> OLD.ends_at
                AND NEW.encrypted                    <=> OLD.encrypted
                AND NEW.encrypted_subjective_notes   <=> OLD.encrypted_subjective_notes
                AND NEW.encrypted_objective_notes    <=> OLD.encrypted_objective_notes
                AND NEW.encrypted_assessment         <=> OLD.encrypted_assessment
                AND NEW.encrypted_plan               <=> OLD.encrypted_plan
                AND NEW.encryption_key_public_id     <=> OLD.encryption_key_public_id
                AND NEW.content_hash                 <=> OLD.content_hash
                AND NEW.signed_at                    <=> OLD.signed_at
                AND NEW.digital_signature            <=> OLD.digital_signature
                AND NEW.digital_certificate_public_id <=> OLD.digital_certificate_public_id) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: al anular solo pueden cambiar status, voided_at, voided_by_user_public_id y cancellation_reason';
            END IF;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: un encuentro firmado no se sobrescribe; use encounter_addendum';
        END IF;
    END IF;

    -- Un encuentro anulado queda congelado por completo.
    IF OLD.status = 'VOIDED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: un encuentro anulado no admite modificaciones';
    END IF;
END$$

CREATE TRIGGER trg_clinical_encounter_no_delete
    BEFORE DELETE ON clinical_encounter
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: los registros clinicos se anulan, no se borran';
END$$

CREATE TRIGGER trg_encounter_addendum_no_update
    BEFORE UPDATE ON encounter_addendum
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ENCOUNTER_ADDENDUM_IMMUTABLE: un addendum es una adicion inmutable';
END$$

CREATE TRIGGER trg_encounter_addendum_no_delete
    BEFORE DELETE ON encounter_addendum
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ENCOUNTER_ADDENDUM_IMMUTABLE: un addendum es una adicion inmutable';
END$$

DELIMITER ;
