package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.PatientStatus;
import java.util.UUID;

/** Proyección mínima que Agenda necesita para aceptar o rechazar una cita. */
public record SchedulingPatientSummary(UUID patientId, String fullName, PatientStatus status) {
}
