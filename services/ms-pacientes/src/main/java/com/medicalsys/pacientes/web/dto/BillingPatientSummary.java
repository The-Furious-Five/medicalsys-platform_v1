package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import java.util.UUID;

/** Proyección mínima que Facturación necesita para emitir un comprobante. */
public record BillingPatientSummary(
        UUID patientId,
        String fullName,
        DocumentType documentType,
        String documentNumber,
        String documentComplement,
        PatientStatus status) {
}
