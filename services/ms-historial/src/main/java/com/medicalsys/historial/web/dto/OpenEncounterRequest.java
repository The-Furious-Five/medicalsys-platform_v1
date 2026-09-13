package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.EncounterType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Apertura de un encuentro clinico en borrador.
 *
 * <p>El expediente se recupera o se crea a partir del paciente, de modo que atender no dependa de
 * una llamada previa. La cita es opcional: una urgencia no tiene cita.
 *
 * @param patientId      paciente atendido
 * @param appointmentId  cita de ms-agenda que origina la atencion, opcional
 * @param professionalId profesional que atiende
 * @param branchId       sucursal donde ocurre la atencion
 * @param type           tipo de encuentro; por defecto INITIAL_CONSULTATION
 * @param startsAt       inicio de la atencion; por defecto el instante actual
 */
public record OpenEncounterRequest(
        @NotNull(message = "patientId es obligatorio") UUID patientId,
        UUID appointmentId,
        @NotNull(message = "professionalId es obligatorio") UUID professionalId,
        @NotNull(message = "branchId es obligatorio") UUID branchId,
        EncounterType type,
        OffsetDateTime startsAt) {
}
