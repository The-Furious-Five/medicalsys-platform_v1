package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.RecordStatus;
import java.util.UUID;

/**
 * Proyeccion minima del expediente para otros microservicios.
 *
 * <p>Ningun consumidor recibe mas de lo necesario: no incluye narrativa ni referencias de cifrado.
 *
 * @param id           UUID publico del expediente
 * @param patientId    paciente propietario
 * @param recordNumber correlativo legible
 * @param status       estado administrativo
 */
public record MedicalRecordSummaryResponse(UUID id, UUID patientId, String recordNumber, RecordStatus status) {
}
