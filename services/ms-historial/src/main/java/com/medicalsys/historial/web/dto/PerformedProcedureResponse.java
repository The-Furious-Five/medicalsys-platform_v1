package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AnesthesiaType;
import com.medicalsys.historial.domain.enums.ProcedureStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Procedimiento realizado durante el encuentro.
 *
 * @param id                 identificador local
 * @param serviceOfferingId  prestacion de ms-tenencia
 * @param physicalResourceId quirofano o sala
 * @param responsibleId      profesional responsable
 * @param startsAt           inicio
 * @param endsAt             fin
 * @param anesthesiaType     tipo de anestesia
 * @param description        descripcion descifrada, sujeta a rol
 * @param complications      complicaciones descifradas, sujetas a rol
 * @param informedConsentId  consentimiento informado
 * @param status             estado
 * @param createdAt          instante del registro
 */
public record PerformedProcedureResponse(
        Long id,
        UUID serviceOfferingId,
        UUID physicalResourceId,
        UUID responsibleId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        AnesthesiaType anesthesiaType,
        String description,
        String complications,
        UUID informedConsentId,
        ProcedureStatus status,
        OffsetDateTime createdAt) {
}
