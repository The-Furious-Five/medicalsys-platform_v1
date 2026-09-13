package com.medicalsys.historial.mapper;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.StudyOrder;
import com.medicalsys.historial.domain.model.StudyResult;
import com.medicalsys.historial.service.NarrativeAccessPolicy;
import com.medicalsys.historial.web.dto.StudyOrderResponse;
import com.medicalsys.historial.web.dto.StudyOrderSummaryResponse;
import com.medicalsys.historial.web.dto.StudyResultResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce el agregado de la orden de estudio al contrato publico. */
@Component
public class StudyOrderMapper {

    private final NarrativeAccessPolicy narrativeAccess;

    public StudyOrderMapper(NarrativeAccessPolicy narrativeAccess) {
        this.narrativeAccess = narrativeAccess;
    }

    public StudyOrderResponse toResponse(RequestContext context, StudyOrder order, List<StudyResult> results) {
        return new StudyOrderResponse(
                order.getPublicId(),
                order.getClinicalEncounter().getPublicId(),
                order.getClinicalEncounter().getMedicalRecord().getPublicId(),
                order.getClinicalEncounter().getMedicalRecord().getPatientPublicId(),
                order.getStudyTypeCode(),
                order.getPriority(),
                order.getInstructions(),
                order.getIcd10Code(),
                order.getStatus(),
                order.getRequestedByProfessionalPublicId(),
                DateTimes.toOffset(order.getRequestedAt()),
                order.getCollectionAppointmentPublicId(),
                results.stream().map(result -> toResponse(context, result)).toList());
    }

    /** Proyeccion minima para ms-agenda y ms-documental. */
    public StudyOrderSummaryResponse toSummary(StudyOrder order) {
        return new StudyOrderSummaryResponse(
                order.getPublicId(),
                order.getClinicalEncounter().getMedicalRecord().getPublicId(),
                order.getClinicalEncounter().getMedicalRecord().getPatientPublicId(),
                order.getStudyTypeCode(),
                order.getPriority(),
                order.getStatus(),
                order.getRequestedByProfessionalPublicId(),
                DateTimes.toOffset(order.getRequestedAt()),
                order.getCollectionAppointmentPublicId());
    }

    public StudyResultResponse toResponse(RequestContext context, StudyResult result) {
        return new StudyResultResponse(
                result.getId(),
                result.getParameter(),
                result.getNumericValue(),
                narrativeAccess.reveal(context, result.getEncryptionKeyPublicId(), result.getEncryptedTextValue()),
                result.getUnit(),
                result.getReferenceMin(),
                result.getReferenceMax(),
                result.isOutOfRange(),
                result.getStatus(),
                result.getDocumentPublicId(),
                result.getReportedByProfessionalPublicId(),
                DateTimes.toOffset(result.getReportedAt()),
                DateTimes.toOffset(result.getDeliveredToPatientAt()));
    }
}
