package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AnesthesiaType;
import com.medicalsys.historial.domain.enums.ProcedureStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Procedimiento ejecutado durante el encuentro.
 *
 * @param serviceOfferingId    prestacion de ms-tenencia
 * @param physicalResourceId   quirofano o sala de ms-agenda, opcional
 * @param responsibleId        profesional responsable
 * @param startsAt             inicio del procedimiento
 * @param endsAt               fin del procedimiento, opcional
 * @param anesthesiaType       tipo de anestesia; por defecto NONE
 * @param description          descripcion en claro; se persiste cifrada
 * @param complications        complicaciones en claro; se persisten cifradas
 * @param informedConsentId    consentimiento informado de ms-documental, opcional
 * @param status               estado; por defecto SCHEDULED
 */
public record CreateProcedureRequest(
        @NotNull(message = "serviceOfferingId es obligatorio") UUID serviceOfferingId,
        UUID physicalResourceId,
        @NotNull(message = "responsibleId es obligatorio") UUID responsibleId,
        @NotNull(message = "startsAt es obligatorio") OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        AnesthesiaType anesthesiaType,
        @Size(max = 4000) String description,
        @Size(max = 2000) String complications,
        UUID informedConsentId,
        ProcedureStatus status) {
}
