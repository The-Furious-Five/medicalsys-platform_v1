package com.medicalsys.pacientes.api.dto;

import java.util.UUID;

public record SchedulingPatientSummary(
        UUID patientId,
        String fullName,
        String status) {
}
