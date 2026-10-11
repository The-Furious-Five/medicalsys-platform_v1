package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.api.dto.SendWhatsappMessageRequest;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.security.RequiresPermission;
import com.medicalsys.notificaciones.service.WhatsappManualMessageService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications/whatsapp")
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "manual-send-enabled", havingValue = "true")
public class WhatsappManualMessageController {

    private final WhatsappManualMessageService service;

    public WhatsappManualMessageController(WhatsappManualMessageService service) {
        this.service = service;
    }

    @PostMapping("/send")
    @RequiresPermission("notifications.whatsapp.send")
    public Mono<NotificationResponse> send(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @Valid @RequestBody SendWhatsappMessageRequest request) {
        return service.send(RequestOrganization.parse(organizationHeader), request);
    }
}
