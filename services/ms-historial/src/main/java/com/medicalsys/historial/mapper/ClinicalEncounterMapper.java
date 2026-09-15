package com.medicalsys.historial.mapper;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.Diagnosis;
import com.medicalsys.historial.domain.model.EncounterAddendum;
import com.medicalsys.historial.domain.model.PerformedProcedure;
import com.medicalsys.historial.domain.model.VitalSign;
import com.medicalsys.historial.service.NarrativeAccessPolicy;
import com.medicalsys.historial.web.dto.AddendumResponse;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.DiagnosisResponse;
import com.medicalsys.historial.web.dto.EncounterNarrativeResponse;
import com.medicalsys.historial.web.dto.EncounterSummaryResponse;
import com.medicalsys.historial.web.dto.PerformedProcedureResponse;
import com.medicalsys.historial.web.dto.VitalSignResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce el agregado del encuentro al contrato publico, aplicando la politica de narrativa. */
@Component
public class ClinicalEncounterMapper {

    private final NarrativeAccessPolicy narrativeAccess;

    public ClinicalEncounterMapper(NarrativeAccessPolicy narrativeAccess) {
        this.narrativeAccess = narrativeAccess;
    }

    /**
     * Encuentro completo.
     *
     * @param diagnoses diagnosticos ya cargados por el servicio, para no disparar consultas perezosas
     */
    public ClinicalEncounterResponse toResponse(RequestContext context, ClinicalEncounter encounter,
            List<Diagnosis> diagnoses) {
        return new ClinicalEncounterResponse(
                encounter.getPublicId(),
                encounter.getMedicalRecord().getPublicId(),
                encounter.getMedicalRecord().getPatientPublicId(),
                encounter.getAppointmentPublicId(),
                encounter.getHealthProfessionalPublicId(),
                encounter.getBranchPublicId(),
                encounter.getType(),
                encounter.getStatus(),
                DateTimes.toOffset(encounter.getStartsAt()),
                DateTimes.toOffset(encounter.getEndsAt()),
                encounter.getSuggestedNextAppointmentAt(),
                toNarrative(context, encounter),
                hasCiphertext(encounter),
                encounter.getEncryptionKeyPublicId(),
                encounter.getContentHash(),
                DateTimes.toOffset(encounter.getSignedAt()),
                DateTimes.toOffset(encounter.getVoidedAt()),
                encounter.getCancellationReason(),
                diagnoses.stream().map(diagnosis -> toResponse(context, diagnosis)).toList(),
                DateTimes.toOffset(encounter.getCreatedAt()),
                DateTimes.toOffset(encounter.getUpdatedAt()));
    }

    public EncounterSummaryResponse toSummary(ClinicalEncounter encounter) {
        return new EncounterSummaryResponse(
                encounter.getPublicId(),
                encounter.getMedicalRecord().getPublicId(),
                encounter.getMedicalRecord().getPatientPublicId(),
                encounter.getAppointmentPublicId(),
                encounter.getHealthProfessionalPublicId(),
                encounter.getType(),
                encounter.getStatus(),
                DateTimes.toOffset(encounter.getStartsAt()),
                DateTimes.toOffset(encounter.getEndsAt()));
    }

    public AddendumResponse toResponse(RequestContext context, EncounterAddendum addendum) {
        return new AddendumResponse(
                addendum.getId(),
                addendum.getHealthProfessionalPublicId(),
                addendum.getReason(),
                narrativeAccess.reveal(context, addendum.getEncryptionKeyPublicId(), addendum.getEncryptedText()),
                addendum.getContentHash(),
                DateTimes.toOffset(addendum.getCreatedAt()));
    }

    public VitalSignResponse toResponse(VitalSign vitalSign) {
        return new VitalSignResponse(
                vitalSign.getId(),
                DateTimes.toOffset(vitalSign.getMeasuredAt()),
                vitalSign.getSystolicPressure(),
                vitalSign.getDiastolicPressure(),
                vitalSign.getHeartRate(),
                vitalSign.getRespiratoryRate(),
                vitalSign.getTemperatureC(),
                vitalSign.getOxygenSaturation(),
                vitalSign.getBloodGlucoseMgDl(),
                vitalSign.getWeightKg(),
                vitalSign.getHeightCm(),
                vitalSign.getBmi(),
                vitalSign.getAbdominalCircumferenceCm(),
                vitalSign.getPainScale(),
                vitalSign.getRecordedByUserPublicId());
    }

    public DiagnosisResponse toResponse(RequestContext context, Diagnosis diagnosis) {
        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getDiagnosedByProfessionalPublicId(),
                diagnosis.getIcd10Code(),
                diagnosis.getType(),
                diagnosis.isPrimary(),
                diagnosis.isChronic(),
                diagnosis.getLaterality(),
                narrativeAccess.reveal(context, diagnosis.getClinicalEncounter().getEncryptionKeyPublicId(),
                        diagnosis.getEncryptedObservation()),
                diagnosis.getStatus(),
                DateTimes.toOffset(diagnosis.getCreatedAt()));
    }

    public PerformedProcedureResponse toResponse(RequestContext context, PerformedProcedure procedure) {
        var keyPublicId = procedure.getClinicalEncounter().getEncryptionKeyPublicId();
        return new PerformedProcedureResponse(
                procedure.getId(),
                procedure.getServiceOfferingPublicId(),
                procedure.getPhysicalResourcePublicId(),
                procedure.getResponsibleProfessionalPublicId(),
                DateTimes.toOffset(procedure.getStartsAt()),
                DateTimes.toOffset(procedure.getEndsAt()),
                procedure.getAnesthesiaType(),
                narrativeAccess.reveal(context, keyPublicId, procedure.getEncryptedDescription()),
                narrativeAccess.reveal(context, keyPublicId, procedure.getEncryptedComplications()),
                procedure.getInformedConsentPublicId(),
                procedure.getStatus(),
                DateTimes.toOffset(procedure.getCreatedAt()));
    }

    private EncounterNarrativeResponse toNarrative(RequestContext context, ClinicalEncounter encounter) {
        if (!narrativeAccess.canReadNarrative(context)) {
            return null;
        }
        var key = encounter.getEncryptionKeyPublicId();
        return new EncounterNarrativeResponse(
                narrativeAccess.reveal(context, key, encounter.getEncrypted()),
                narrativeAccess.reveal(context, key, encounter.getEncryptedSubjectiveNotes()),
                narrativeAccess.reveal(context, key, encounter.getEncryptedObjectiveNotes()),
                narrativeAccess.reveal(context, key, encounter.getEncryptedAssessment()),
                narrativeAccess.reveal(context, key, encounter.getEncryptedPlan()));
    }

    /** Indica si la base conserva ciphertext para el encuentro; util como evidencia de la demo. */
    private boolean hasCiphertext(ClinicalEncounter encounter) {
        return isPresent(encounter.getEncrypted())
                || isPresent(encounter.getEncryptedSubjectiveNotes())
                || isPresent(encounter.getEncryptedObjectiveNotes())
                || isPresent(encounter.getEncryptedAssessment())
                || isPresent(encounter.getEncryptedPlan());
    }

    private boolean isPresent(byte[] value) {
        return value != null && value.length > 0;
    }
}
