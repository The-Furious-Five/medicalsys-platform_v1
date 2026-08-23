package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Apertura de expediente.
 *
 * <p>La operacion es idempotente por organizacion y paciente: si el expediente ya existe se
 * devuelve el mismo, sin crear un duplicado.
 *
 * @param patientId paciente registrado en ms-pacientes
 * @param branchId  sucursal de apertura, opcional
 */
public record OpenMedicalRecordRequest(
        @NotNull(message = "patientId es obligatorio") UUID patientId,
        UUID branchId) {
}
