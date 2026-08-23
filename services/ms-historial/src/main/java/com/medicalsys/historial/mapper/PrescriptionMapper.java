package com.medicalsys.historial.mapper;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.Prescription;
import com.medicalsys.historial.domain.model.PrescriptionItem;
import com.medicalsys.historial.service.NarrativeAccessPolicy;
import com.medicalsys.historial.web.dto.PrescriptionItemResponse;
import com.medicalsys.historial.web.dto.PrescriptionResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traduce el agregado de la receta al contrato publico. */
@Component
public class PrescriptionMapper {

    private final NarrativeAccessPolicy narrativeAccess;

    public PrescriptionMapper(NarrativeAccessPolicy narrativeAccess) {
        this.narrativeAccess = narrativeAccess;
    }

    public PrescriptionResponse toResponse(RequestContext context, Prescription prescription) {
        UUID key = prescription.getClinicalEncounter().getEncryptionKeyPublicId();
        return new PrescriptionResponse(
                prescription.getPublicId(),
                prescription.getClinicalEncounter().getPublicId(),
                prescription.getFolio(),
                DateTimes.toOffset(prescription.getIssuedAt()),
                prescription.getValidityDays(),
                narrativeAccess.reveal(context, key, prescription.getEncryptedGeneralInstructions()),
                prescription.getStatus(),
                prescription.getPdfDocumentPublicId(),
                prescription.getSha256Hash(),
                DateTimes.toOffset(prescription.getSignedAt()),
                prescription.getItems().stream().map(item -> toResponse(context, key, item)).toList());
    }

    private PrescriptionItemResponse toResponse(RequestContext context, UUID key, PrescriptionItem item) {
        return new PrescriptionItemResponse(
                item.getItemNumber(),
                item.getMedicationCode(),
                item.getFreeTextDescription(),
                item.getDose(),
                item.getAdministrationRoute(),
                item.getFrequencyHours(),
                item.getDurationDays(),
                item.getQuantity(),
                item.getUnit(),
                narrativeAccess.reveal(context, key, item.getEncryptedInstructions()));
    }
}
