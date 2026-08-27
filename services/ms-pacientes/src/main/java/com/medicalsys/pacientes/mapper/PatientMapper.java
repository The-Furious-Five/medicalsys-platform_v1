package com.medicalsys.pacientes.mapper;

import com.medicalsys.pacientes.common.DateTimes;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.web.dto.FiscalCustomerResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import org.springframework.stereotype.Component;

/**
 * Traduce la entidad a los DTO del contrato.
 *
 * <p>Cada consumidor recibe solo lo que su caso de uso necesita: facturacion no ve telefono ni
 * contacto de emergencia, y nadie ve la clave numerica interna.
 */
@Component
public class PatientMapper {

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getPublicId(),
                patient.getUserAccountPublicId(),
                patient.getDocumentType(),
                patient.getDocumentNumber(),
                patient.getDocumentComplement(),
                patient.getGivenNames(),
                patient.getPaternalSurname(),
                patient.getMaternalSurname(),
                patient.getFullName(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getPhoneE164(),
                patient.getAlternatePhone(),
                patient.getEmail(),
                patient.getCity(),
                patient.getDepartment(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getEmergencyContactRelationship(),
                patient.getStatus(),
                DateTimes.toOffset(patient.getCreatedAt()),
                DateTimes.toOffset(patient.getUpdatedAt()));
    }

    /** Proyeccion fiscal minima para ms-facturacion. */
    public FiscalCustomerResponse toFiscalCustomer(Patient patient) {
        return new FiscalCustomerResponse(
                patient.getPublicId(),
                patient.getDocumentType().name(),
                patient.getDocumentNumber(),
                patient.getDocumentComplement(),
                patient.getFullName(),
                patient.getEmail());
    }
}
