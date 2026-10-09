package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.api.dto.SendWhatsappMessageRequest;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.provider.WhatsappMessageSender;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WhatsappManualMessageService {

    private final NotificationRepository notifications;
    private final WhatsappMessageSender sender;

    public WhatsappManualMessageService(NotificationRepository notifications, WhatsappMessageSender sender) {
        this.notifications = notifications;
        this.sender = sender;
    }

    public Mono<NotificationResponse> send(UUID organizationId, SendWhatsappMessageRequest request) {
        String message = trimToNull(request.message());
        String templateName = trimToNull(request.templateName());
        if ((message == null) == (templateName == null)) {
            return Mono.error(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WHATSAPP_MESSAGE",
                    "Debes indicar un mensaje de texto o una plantilla, pero no ambos"));
        }
        String languageCode = trimToNull(request.languageCode());
        if (templateName != null && languageCode == null) {
            return Mono.error(new ApiException(HttpStatus.BAD_REQUEST, "TEMPLATE_LANGUAGE_REQUIRED",
                    "El idioma es obligatorio al enviar una plantilla"));
        }

        LocalDateTime now = LocalDateTime.now();
        Notification queued = new Notification(null, UUID.randomUUID().toString(), null,
                "manual-whatsapp:" + UUID.randomUUID(), organizationId.toString(), null, null,
                NotificationChannel.WHATSAPP, NotificationCategory.SERVICE, null, null,
                "MANUAL_WHATSAPP", "MANUAL", null, request.destination(), null,
                message != null ? message : "Plantilla: " + templateName,
                NotificationStatus.QUEUED, now, 0, null, null, null, null, null, null, null,
                null, now, now);

        List<String> parameters = request.templateParameters() == null
                ? List.of() : request.templateParameters();
        return notifications.save(queued).flatMap(saved -> {
            Mono<com.medicalsys.notificaciones.provider.ProviderResult> delivery = message != null
                    ? sender.sendText(request.destination(), message)
                    : sender.sendTemplate(request.destination(), templateName, languageCode, parameters);
            return delivery
                    .flatMap(result -> notifications.save(saved.sent(result.providerMessageId(), LocalDateTime.now())))
                    .onErrorResume(error -> notifications.save(saved.failed("PROVIDER_ERROR",
                            safeMessage(error), null, LocalDateTime.now())));
        }).map(NotificationResponse::from);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "El proveedor rechazo el envio";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
