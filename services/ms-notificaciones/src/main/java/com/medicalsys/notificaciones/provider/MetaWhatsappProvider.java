package com.medicalsys.notificaciones.provider;

import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.repository.ConversationWindowStore;
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
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "provider", havingValue = "meta")
public class MetaWhatsappProvider implements NotificationProvider, WhatsappMessageSender {

    private final WebClient webClient;
    private final ConversationWindowStore conversations;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;

    public MetaWhatsappProvider(WebClient.Builder builder,
            ConversationWindowStore conversations,
            @Value("${medicalsys.notifications.whatsapp.graph-base-url}") String graphBaseUrl,
            @Value("${medicalsys.notifications.whatsapp.api-version}") String apiVersion,
            @Value("${medicalsys.notifications.whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${medicalsys.notifications.whatsapp.access-token}") String accessToken) {
        this.webClient = builder.baseUrl(graphBaseUrl).build();
        this.conversations = conversations;
        this.apiVersion = apiVersion;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
    }

    @Override
    public Mono<ProviderResult> send(Notification notification) {
        return Mono.defer(() -> {
            validateConfiguration();
            return sendTextIfWindowIsOpen(notification);
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
