package com.medicalsys.documental.service;

import com.medicalsys.documental.api.dto.ConsentEventResponse;
import com.medicalsys.documental.api.dto.ConsentTemplateResponse;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.domain.ConsentEvent;
import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.InformedConsent;
import org.springframework.stereotype.Component;

@Component
public class ConsentMapper {

    public ConsentTemplateResponse toResponse(ConsentTemplate template) {
        return new ConsentTemplateResponse(
                template.getPublicId(),
                template.getOrganizationPublicId(),
                template.getCode(),
                template.getVersion(),
                template.getTitle(),
                template.getSpecialtyCode(),
                template.getMarkdownBody(),
                template.getVariables(),
                template.isRequiresWitness(),
                template.isRequiresGuardianForMinor(),
                template.getValidFrom(),
                template.getValidUntil(),
                template.getStatus().name(),
                template.getCreatedAt());
    }

    public InformedConsentResponse toResponse(InformedConsent consent) {
        return new InformedConsentResponse(
                consent.getPublicId(),
                consent.getOrganizationPublicId(),
                consent.getTemplate().getCode(),
                consent.getTemplateVersion(),
                consent.getPatientPublicId(),
                consent.getMedicalRecordPublicId(),
                consent.getAppointmentPublicId(),
                consent.getClinicalEncounterPublicId(),
                consent.getHealthProfessionalPublicId(),
                consent.getServiceOfferingPublicId(),
                consent.getStatus().name(),
                consent.getSignatureMethod() == null ? null : consent.getSignatureMethod().name(),
                consent.getSignerType() == null ? null : consent.getSignerType().name(),
                consent.getSignerDocumentNumber(),
                consent.getPdfDocument() == null ? null : consent.getPdfDocument().getPublicId(),
                consent.getGeneratedAt(),
                consent.getSignedAt(),
                consent.getRevokedAt(),
                consent.getRevocationReason(),
                consent.getEvents().stream().map(this::toResponse).toList());
    }

    public ConsentEventResponse toResponse(ConsentEvent event) {
        return new ConsentEventResponse(event.getType().name(), event.getOccurredAt(),
                event.getUserAccountPublicId());
    }
}
