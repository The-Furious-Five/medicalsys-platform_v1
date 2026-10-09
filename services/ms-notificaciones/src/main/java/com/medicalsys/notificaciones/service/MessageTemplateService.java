package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.MessageTemplateRequest;
import com.medicalsys.notificaciones.api.dto.MessageTemplateResponse;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MessageTemplateService {

    private final MessageTemplateRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MessageTemplateService(MessageTemplateRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    MessageTemplateService(MessageTemplateRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Mono<MessageTemplateResponse> create(UUID organizationId, MessageTemplateRequest request) {
        String code = normalize(request.code());
        return repository.existsByOrganizationIdAndCodeAndLanguageCode(
                        organizationId.toString(), code, request.languageCode())
                .flatMap(exists -> exists
                        ? Mono.error(new ApiException(HttpStatus.CONFLICT, "TEMPLATE_ALREADY_EXISTS",
                                "Ya existe una plantilla con el mismo codigo e idioma"))
                        : saveNew(organizationId, code, request));
    }

    public Flux<MessageTemplateResponse> list(UUID organizationId) {
        return repository.findAllByOrganizationIdOrderByCodeAsc(organizationId.toString()).map(this::response);
    }

    public Mono<MessageTemplateResponse> get(UUID organizationId, UUID templateId) {
        return find(organizationId, templateId).map(this::response);
    }

    public Mono<MessageTemplateResponse> update(UUID organizationId, UUID templateId,
            MessageTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        return find(organizationId, templateId)
                .flatMap(current -> repository.save(current.update(normalize(request.code()), request.channel(),
                        request.category(), blankToNull(request.providerTemplateName()), request.languageCode(),
                        blankToNull(request.subject()), request.body(), json(request.variables()),
                        request.approvalStatus(), request.active(), now)))
                .map(this::response);
    }

    public Mono<MessageTemplate> findEntity(UUID organizationId, UUID templateId) {
        return find(organizationId, templateId);
    }

    private Mono<MessageTemplateResponse> saveNew(UUID organizationId, String code, MessageTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        MessageTemplate value = new MessageTemplate(null, UUID.randomUUID().toString(), organizationId.toString(),
                code, request.channel(), request.category(), blankToNull(request.providerTemplateName()),
                request.languageCode(), blankToNull(request.subject()), request.body(), json(request.variables()),
                request.approvalStatus(), request.active(), now, now);
        return repository.save(value).map(this::response);
    }

    private Mono<MessageTemplate> find(UUID organizationId, UUID templateId) {
        return repository.findByPublicIdAndOrganizationId(templateId.toString(), organizationId.toString())
                .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND",
                        "No existe la plantilla solicitada en esta organizacion")));
    }

    private MessageTemplateResponse response(MessageTemplate template) {
        if (template.variables() == null || template.variables().isBlank()) {
            return MessageTemplateResponse.from(template, List.of());
        }
        try {
            return MessageTemplateResponse.from(template,
                    Arrays.asList(objectMapper.readValue(template.variables(), String[].class)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("La plantilla contiene variables JSON invalidas", exception);
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_VARIABLES",
                    "Las variables de la plantilla no son JSON valido");
        }
    }

    private String normalize(String code) {
        return code.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
