package com.medicalsys.pacientes.api.dto;

import java.util.UUID;

public record BillingPatientSummary(
        UUID patientId,
        String fullName,
        String documentType,
        String documentNumber,
        String documentComplement,
        String status) {
}
