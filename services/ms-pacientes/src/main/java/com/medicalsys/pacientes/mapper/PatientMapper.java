package com.medicalsys.pacientes.mapper;

import com.medicalsys.pacientes.common.DateTimes;
import com.medicalsys.pacientes.domain.model.NotificationPreference;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientMerge;
import com.medicalsys.pacientes.web.dto.BillingPatientSummary;
import com.medicalsys.pacientes.web.dto.NotificationContactSummary;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceResponse;
import com.medicalsys.pacientes.web.dto.PatientMergeResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.SchedulingPatientSummary;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traduce entidades a contratos HTTP sin exponer identificadores BIGINT internos. */
@Component
public class PatientMapper {

    /** Respuesta completa; solo para el tenant dueño o con permiso autorizado por el paciente. */
    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getPublicId(),
                patient.getUserAccountPublicId(),
                patient.getDocumentType(),
                patient.getDocumentNumber(),
                patient.getDocumentComplement(),
                patient.getIssuedDepartment(),
                patient.getGivenNames(),
                patient.getPaternalSurname(),
                patient.getMaternalSurname(),
                patient.getFullName(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getMaritalStatus(),
                patient.getBloodType(),
                patient.getOccupation(),
                patient.getPhoneE164(),
                patient.getAlternatePhone(),
                patient.getEmail(),
                encode(patient.getEncryptedAddress()),
                patient.getCity(),
                patient.getDepartment(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getEmergencyContactRelationship(),
                publicId(patient.getGuardianPatient()),
                patient.getEncryptionKeyPublicId(),
                DateTimes.toOffset(patient.getDataConsentAt()),
                patient.getStatus(),
                patient.getDeathDate(),
                DateTimes.toOffset(patient.getCreatedAt()),
                DateTimes.toOffset(patient.getUpdatedAt()),
                true);
    }

    /**
     * Confirma que el paciente existe (para que recepcion pueda ofrecer "solicitar acceso") sin
     * exponer nada del resto de su ficha a un tenant que todavia no tiene permiso.
     */
    public PatientResponse toRestrictedResponse(Patient patient) {
        return new PatientResponse(
                patient.getPublicId(),      // patientId
                null,                       // userAccountId
                null,                       // documentType
                null,                       // documentNumber
                null,                       // documentComplement
                null,                       // issuedDepartment
                patient.getGivenNames(),    // givenNames
                patient.getPaternalSurname(), // paternalSurname
                patient.getMaternalSurname(), // maternalSurname
                patient.getFullName(),      // fullName
                null,                       // birthDate
                null,                       // sex
                null,                       // maritalStatus
                null,                       // bloodType
                null,                       // occupation
                null,                       // phoneE164
                null,                       // alternatePhone
                null,                       // email
                null,                       // encryptedAddressBase64
                null,                       // city
                null,                       // department
                null,                       // emergencyContactName
                null,                       // emergencyContactPhone
                null,                       // emergencyContactRelationship
                null,                       // guardianPatientId
                null,                       // encryptionKeyId
                null,                       // dataConsentAt
                patient.getStatus(),        // status
                null,                       // deathDate
                null,                       // createdAt
                null,                       // updatedAt
                false);                     // fullAccessGranted
    }

    public SchedulingPatientSummary toSchedulingSummary(Patient patient) {
        return new SchedulingPatientSummary(patient.getPublicId(), patient.getFullName(), patient.getStatus());
    }

    public NotificationContactSummary toNotificationContact(Patient patient) {
        return new NotificationContactSummary(
                patient.getPublicId(), patient.getGivenNames(), patient.getFullName(), patient.getPhoneE164());
    }

    public BillingPatientSummary toBillingSummary(Patient patient) {
        return new BillingPatientSummary(patient.getPublicId(), patient.getFullName(), patient.getDocumentType(),
                patient.getDocumentNumber(), patient.getDocumentComplement(), patient.getStatus());
    }

    public NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.getPatient().getPublicId(),
                preference.getChannel(),
                preference.getCategory(),
                preference.isOptIn(),
                preference.getConsentSource(),
                DateTimes.toOffset(preference.getOptInAt()),
                DateTimes.toOffset(preference.getOptOutAt()),
                DateTimes.toOffset(preference.getUpdatedAt()));
    }

    public PatientMergeResponse toMergeResponse(PatientMerge merge) {
        return new PatientMergeResponse(
                merge.getSurvivingPatient().getPublicId(),
                merge.getMergedPatient().getPublicId(),
                merge.getReason(),
                merge.getMergedByUserPublicId(),
                DateTimes.toOffset(merge.getMergedAt()),
                merge.getEventPublicId());
    }

    private String encode(byte[] value) {
        return value == null ? null : Base64.getEncoder().encodeToString(value);
    }

    private UUID publicId(Patient patient) {
        return patient == null ? null : patient.getPublicId();
    }
}
