package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import com.medicalsys.notificaciones.repository.WhatsappWebhookStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WhatsappWebhookService {

    private final ObjectMapper objectMapper;
    private final NotificationRepository notifications;
    private final ReferenceSyncService references;
    private final WhatsappWebhookStore store;
    private final String verifyToken;
    private final String appSecret;
    private final String provider;
    private final Clock clock;

    @Autowired
    public WhatsappWebhookService(ObjectMapper objectMapper, NotificationRepository notifications,
            ReferenceSyncService references, WhatsappWebhookStore store,
            @Value("${medicalsys.notifications.whatsapp.verify-token:local-verify-token}") String verifyToken,
            @Value("${medicalsys.notifications.whatsapp.app-secret:}") String appSecret,
            @Value("${medicalsys.notifications.provider:mock}") String provider) {
        this(objectMapper, notifications, references, store, verifyToken, appSecret, provider, Clock.systemUTC());
    }

    WhatsappWebhookService(ObjectMapper objectMapper, NotificationRepository notifications,
            ReferenceSyncService references, WhatsappWebhookStore store, String verifyToken,
            String appSecret, Clock clock) {
        this(objectMapper, notifications, references, store, verifyToken, appSecret, "mock", clock);
    }

    WhatsappWebhookService(ObjectMapper objectMapper, NotificationRepository notifications,
            ReferenceSyncService references, WhatsappWebhookStore store, String verifyToken,
            String appSecret, String provider, Clock clock) {
        this.objectMapper = objectMapper;
        this.notifications = notifications;
        this.references = references;
        this.store = store;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.provider = provider;
        this.clock = clock;
        if ("meta".equalsIgnoreCase(provider) && (appSecret == null || appSecret.isBlank())) {
            throw new IllegalStateException("WHATSAPP_APP_SECRET es obligatorio cuando NOTIFICATION_PROVIDER=meta");
        }
    }

    public String verify(String mode, String token, String challenge) {
        if (!"subscribe".equals(mode) || !MessageDigest.isEqual(
                verifyToken.getBytes(StandardCharsets.UTF_8), safe(token).getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WEBHOOK_VERIFICATION_FAILED",
                    "Meta no pudo verificar el webhook");
        }
        return challenge;
    }

    public Mono<Void> handle(String payload, String signature) {
        return Mono.defer(() -> {
            verifySignature(payload, signature);
            JsonNode root = read(payload);
            return Flux.fromIterable(root.path("entry"))
                    .concatMap(entry -> Flux.fromIterable(entry.path("changes")))
                    .concatMap(change -> processValue(change.path("value"), payload))
                    .then();
        });
    }

    private Flux<Void> processValue(JsonNode value, String rawPayload) {
        Flux<Void> statuses = Flux.fromIterable(value.path("statuses"))
                .concatMap(status -> handleStatus(status, rawPayload));
        Flux<Void> messages = Flux.fromIterable(value.path("messages"))
                .concatMap(message -> handleIncoming(message));
        return Flux.concat(statuses, messages);
    }

    private Mono<Void> handleStatus(JsonNode statusNode, String rawPayload) {
        String providerId = text(statusNode, "id");
        if (providerId == null) {
            return Mono.empty();
        }
        String rawStatus = safe(text(statusNode, "status")).toUpperCase(Locale.ROOT);
        NotificationStatus status = switch (rawStatus) {
            case "SENT" -> NotificationStatus.SENT;
            case "DELIVERED" -> NotificationStatus.DELIVERED;
            case "READ" -> NotificationStatus.READ;
            case "FAILED", "DELETED" -> NotificationStatus.FAILED;
            default -> null;
        };
        if (status == null) {
            return Mono.empty();
        }
        LocalDateTime occurredAt = timestamp(statusNode.path("timestamp").asText());
        String errorCode = statusNode.path("errors").isArray() && !statusNode.path("errors").isEmpty()
                ? statusNode.path("errors").get(0).path("code").asText(null) : null;
        return notifications.findByProviderMessageId(providerId).flatMap(notification -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Mono<?> save = status == NotificationStatus.FAILED
                    ? notifications.save(notification.failed(errorCode == null ? "PROVIDER_FAILED" : errorCode,
                            "El proveedor reporto el mensaje como fallido", now.plusMinutes(2), now))
                    : notifications.save(notification.providerStatus(status, occurredAt, errorCode, null, now));
            return save.then(store.insertStatusEvent(notification.id(), rawStatus, occurredAt, errorCode, rawPayload));
        }).then();
    }

    private Mono<Void> handleIncoming(JsonNode message) {
        String providerId = text(message, "id");
        String source = normalizePhone(text(message, "from"));
        String body = text(message.path("text"), "body");
        LocalDateTime receivedAt = timestamp(message.path("timestamp").asText());
        if (providerId == null || source == null) {
            return Mono.empty();
        }
        return references.findPatientByWhatsapp(source)
                .flatMap(target -> store.insertIncoming(providerId, source, body, intent(body), receivedAt,
                                target.organizationId(), target.patientId())
                        .then(store.openConversation(source, receivedAt, target.organizationId(), target.patientId()))
                        .then(handleOptOut(body, target.patientId()))
                        .thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> store.insertIncoming(
                                providerId, source, body, intent(body), receivedAt, null, null)
                        .thenReturn(false)))
                .then();
    }

    private Mono<Void> handleOptOut(String body, String patientId) {
        if (!"MARKETING_OPT_OUT".equals(intent(body))) {
            return Mono.empty();
        }
        return references.upsertPreference(new PreferenceReferenceRequest(UUID.fromString(patientId),
                NotificationChannel.WHATSAPP, NotificationCategory.MARKETING, false, null,
                LocalDateTime.now(clock)));
    }

    private void verifySignature(String payload, String signature) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE",
                    "El webhook no tiene un secreto configurado para validar la firma");
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE",
                    "El webhook no contiene una firma valida");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.substring(7).getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE",
                        "La firma del webhook no coincide");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo verificar la firma del webhook", exception);
        }
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_PAYLOAD",
                    "El cuerpo del webhook no contiene JSON valido");
        }
    }

    private LocalDateTime timestamp(String value) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return LocalDateTime.now(clock);
        }
    }

    private String intent(String body) {
        String normalized = safe(body).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONFIRMAR", "CONFIRMO", "SI" -> "CONFIRM";
            case "CANCELAR", "CANCELO" -> "CANCEL";
            case "REPROGRAMAR" -> "RESCHEDULE";
            case "BAJA", "SALIR", "STOP", "NO QUIERO RECIBIR MENSAJES" -> "MARKETING_OPT_OUT";
            case "" -> "UNRECOGNIZED";
            default -> normalized.contains("?") ? "QUESTION" : "OTHER";
        };
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : "+" + digits;
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
