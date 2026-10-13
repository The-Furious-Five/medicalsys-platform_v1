package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.api.dto.SendWhatsappMessageRequest;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.security.InternalEndpoint;
import com.medicalsys.notificaciones.service.WhatsappManualMessageService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Envio de WhatsApp para otros servicios: el OTP de los consentimientos (ms-documental) y de las
 * solicitudes de acceso a la ficha de un paciente (ms-pacientes).
 *
 * <p>Hasta RBAC-04 esos servicios usaban la consola manual de /api, que ahora exige
 * notifications.whatsapp.send al usuario. El codigo lo pide el sistema, no una persona con ese
 * permiso, asi que va por esta ruta interna, que el gateway no publica. Depende del mismo flag que
 * la consola, como antes.
 */
@RestController
@RequestMapping("/internal/v1/notifications/whatsapp")
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "manual-send-enabled", havingValue = "true")
public class InternalWhatsappController {

    private final WhatsappManualMessageService service;

    public InternalWhatsappController(WhatsappManualMessageService service) {
        this.service = service;
    }

    @PostMapping("/send")
    @InternalEndpoint
    public Mono<NotificationResponse> send(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @Valid @RequestBody SendWhatsappMessageRequest request) {
        return service.send(RequestOrganization.parse(organizationHeader), request);
    }
}
