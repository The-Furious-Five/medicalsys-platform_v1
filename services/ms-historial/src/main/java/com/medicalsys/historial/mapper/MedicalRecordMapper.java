package com.medicalsys.historial.mapper;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.Allergy;
import com.medicalsys.historial.domain.model.MedicalBackground;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.service.NarrativeAccessPolicy;
import com.medicalsys.historial.web.dto.AllergyResponse;
import com.medicalsys.historial.web.dto.MedicalBackgroundResponse;
import com.medicalsys.historial.web.dto.MedicalRecordResponse;
import com.medicalsys.historial.web.dto.MedicalRecordSummaryResponse;
import org.springframework.stereotype.Component;

/** Traduce el agregado del expediente al contrato publico. */
@Component
public class MedicalRecordMapper {

    private final NarrativeAccessPolicy narrativeAccess;

    public MedicalRecordMapper(NarrativeAccessPolicy narrativeAccess) {
        this.narrativeAccess = narrativeAccess;
    }

    public MedicalRecordResponse toResponse(MedicalRecord record) {
        return new MedicalRecordResponse(
                record.getPublicId(),
                record.getPatientPublicId(),
                record.getRecordNumber(),
                record.getOpenedOn(),
                record.getOpeningBranchPublicId(),
                record.getStatus(),
                record.isSharesWithNetwork(),
                DateTimes.toOffset(record.getSharingAuthorizedAt()),
                record.getRetentionUntil(),
                DateTimes.toOffset(record.getCreatedAt()),
                DateTimes.toOffset(record.getUpdatedAt()));
    }

    /** Proyeccion minima para otros microservicios. */
    public MedicalRecordSummaryResponse toSummary(MedicalRecord record) {
        return new MedicalRecordSummaryResponse(record.getPublicId(), record.getPatientPublicId(),
                record.getRecordNumber(), record.getStatus());
    }

    public AllergyResponse toResponse(Allergy allergy) {
        return new AllergyResponse(
                allergy.getId(),
                allergy.getType(),
                allergy.getSubstance(),
                allergy.getMedicationCode(),
                allergy.getReaction(),
                allergy.getSeverity(),
                allergy.getStatus(),
                allergy.getRecordedByProfessionalPublicId(),
                DateTimes.toOffset(allergy.getRecordedAt()));
    }

    public MedicalBackgroundResponse toResponse(RequestContext context, MedicalBackground background) {
        return new MedicalBackgroundResponse(
                background.getId(),
                background.getType(),
                background.getTitle(),
                narrativeAccess.reveal(context, background.getMedicalRecord().getEncryptionKeyPublicId(),
                        background.getEncryptedDetail()),
                background.getIcd10Code(),
                background.getEventDate(),
                background.isActive(),
                background.getRecordedByProfessionalPublicId(),
                DateTimes.toOffset(background.getRecordedAt()));
    }
}
