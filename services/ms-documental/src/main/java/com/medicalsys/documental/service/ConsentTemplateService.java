package com.medicalsys.documental.service;

import com.medicalsys.documental.api.dto.ConsentTemplateResponse;
import com.medicalsys.documental.api.dto.CreateConsentTemplateRequest;
import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.ConsentTemplateStatus;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.ConsentTemplateRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentTemplateService {

    private final ConsentTemplateRepository repository;
    private final HashingService hashingService;
    private final ConsentMapper mapper;

    public ConsentTemplateService(ConsentTemplateRepository repository, HashingService hashingService,
            ConsentMapper mapper) {
        this.repository = repository;
        this.hashingService = hashingService;
        this.mapper = mapper;
    }

    @Transactional
    public ConsentTemplateResponse create(CreateConsentTemplateRequest request, UUID organizationId,
            UUID userId) {
        int nextVersion = repository
                .findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                        organizationId, request.code(), ConsentTemplateStatus.PUBLISHED)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        String bodyHash = hashingService.sha256(
                new ByteArrayInputStream(request.markdownBody().getBytes(StandardCharsets.UTF_8)));
        ConsentTemplate template = ConsentTemplate.draft(UUID.randomUUID(), organizationId, request.code(),
                nextVersion, request.title(), request.specialtyCode(), request.markdownBody(),
                request.variables(), request.requiresWitness(), request.requiresGuardianForMinor(),
                request.validFrom(), request.validUntil(), bodyHash, userId);
        return mapper.toResponse(repository.saveAndFlush(template));
    }

    @Transactional
    public ConsentTemplateResponse publish(UUID templateId, UUID organizationId) {
        ConsentTemplate template = find(templateId, organizationId);
        try {
            template.publish();
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.CONSENT_TEMPLATE_NOT_PUBLISHABLE, exception.getMessage());
        }
        return mapper.toResponse(repository.saveAndFlush(template));
    }

    @Transactional(readOnly = true)
    public List<ConsentTemplateResponse> list(UUID organizationId) {
        return repository.findAllByOrganizationPublicIdOrderByCodeAscVersionDesc(organizationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConsentTemplateResponse get(UUID templateId, UUID organizationId) {
        return mapper.toResponse(find(templateId, organizationId));
    }

    private ConsentTemplate find(UUID templateId, UUID organizationId) {
        return repository.findByPublicIdAndOrganizationPublicId(templateId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONSENT_TEMPLATE_NOT_FOUND,
                        "No existe una plantilla de consentimiento visible con el identificador indicado"));
    }
}
