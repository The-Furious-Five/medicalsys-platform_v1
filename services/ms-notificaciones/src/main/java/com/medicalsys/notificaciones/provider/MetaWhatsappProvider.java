package com.medicalsys.notificaciones.provider;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.ConversationWindowStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "provider", havingValue = "meta")
public class MetaWhatsappProvider implements NotificationProvider, WhatsappMessageSender {

    private final WebClient webClient;
    private final MessageTemplateRepository templates;
    private final ConversationWindowStore conversations;
    private final ObjectMapper objectMapper;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;

    public MetaWhatsappProvider(WebClient.Builder builder, MessageTemplateRepository templates,
            ConversationWindowStore conversations, ObjectMapper objectMapper,
            @Value("${medicalsys.notifications.whatsapp.graph-base-url}") String graphBaseUrl,
            @Value("${medicalsys.notifications.whatsapp.api-version}") String apiVersion,
            @Value("${medicalsys.notifications.whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${medicalsys.notifications.whatsapp.access-token}") String accessToken) {
        this.webClient = builder.baseUrl(graphBaseUrl).build();
        this.templates = templates;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
    }

    @Override
    public Mono<ProviderResult> send(Notification notification) {
        return Mono.defer(() -> {
            validateConfiguration();
            Mono<MessageTemplate> template = notification.templateId() == null
                    ? Mono.empty() : templates.findById(notification.templateId());
            return template.flatMap(value -> value.approvalStatus() == TemplateApprovalStatus.APPROVED
                            && value.providerTemplateName() != null
                            ? post(templatePayload(notification, value))
                            : sendTextIfWindowIsOpen(notification))
                    .switchIfEmpty(Mono.defer(() -> sendTextIfWindowIsOpen(notification)));
        });
    }

    private Mono<ProviderResult> sendTextIfWindowIsOpen(Notification notification) {
        return conversationIsOpen(notification).flatMap(open -> {
            if (!open) {
                return Mono.error(new IllegalStateException(
                        "Fuera de la ventana de 24 horas se requiere una plantilla aprobada por Meta"));
            }
            return sendText(notification.destination(), notification.renderedBody());
        });
    }

    @Override
    public Mono<ProviderResult> sendText(String destination, String message) {
        return post(Map.of(
                "messaging_product", "whatsapp",
                "to", digits(destination),
                "type", "text",
                "text", Map.of("preview_url", false, "body", message)));
    }

    @Override
    public Mono<ProviderResult> sendTemplate(String destination, String templateName,
            String languageCode, List<String> parameters) {
        Map<String, Object> remoteTemplate = new LinkedHashMap<>();
        remoteTemplate.put("name", templateName);
        remoteTemplate.put("language", Map.of("code", languageCode));
        if (parameters != null && !parameters.isEmpty()) {
            remoteTemplate.put("components", List.of(Map.of("type", "body", "parameters",
                    parameters.stream().map(value -> Map.<String, Object>of("type", "text", "text", value)).toList())));
        }
        return post(Map.of(
                "messaging_product", "whatsapp",
                "to", digits(destination),
                "type", "template",
                "template", remoteTemplate));
    }

    private Mono<Boolean> conversationIsOpen(Notification notification) {
        if (notification.patientId() == null) {
            return Mono.just(false);
        }
        return conversations.isOpen(notification.organizationId(), notification.patientId());
    }

    private Map<String, Object> templatePayload(Notification notification, MessageTemplate template) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> values = values(notification.variables());
        for (String name : names(template.variables())) {
            parameters.add(Map.of("type", "text", "text", String.valueOf(values.getOrDefault(name, ""))));
        }
        Map<String, Object> remoteTemplate = new LinkedHashMap<>();
        remoteTemplate.put("name", template.providerTemplateName());
        remoteTemplate.put("language", Map.of("code", template.languageCode()));
        if (!parameters.isEmpty()) {
            remoteTemplate.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }
        return Map.of(
                "messaging_product", "whatsapp",
                "to", digits(notification.destination()),
                "type", "template",
                "template", remoteTemplate);
    }

    private Mono<ProviderResult> post(Map<String, Object> body) {
        return webClient.post()
                .uri("/{version}/{phoneNumberId}/messages", apiVersion, phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.path("messages").get(0).path("id").asText())
                .filter(id -> !id.isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException("Meta no devolvio un identificador de mensaje")))
                .map(ProviderResult::new);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> values(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Las variables guardadas no contienen JSON valido", exception);
        }
    }

    private List<String> names(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("La plantilla no contiene variables JSON validas", exception);
        }
    }

    private String digits(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    private void validateConfiguration() {
        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN son obligatorios para el proveedor meta");
        }
    }
}
