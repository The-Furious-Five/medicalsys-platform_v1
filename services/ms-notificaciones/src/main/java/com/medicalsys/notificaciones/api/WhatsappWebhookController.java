package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.security.PublicEndpoint;
import com.medicalsys.notificaciones.service.WhatsappWebhookService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications/webhooks/whatsapp")
public class WhatsappWebhookController {

    private final WhatsappWebhookService service;

    public WhatsappWebhookController(WhatsappWebhookService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @PublicEndpoint
    public String verify(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String token,
            @RequestParam(name = "hub.challenge") String challenge) {
        return service.verify(mode, token, challenge);
    }

    @PostMapping
    @PublicEndpoint
    public Mono<ResponseEntity<Void>> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {
        return service.handle(payload, signature).thenReturn(ResponseEntity.ok().build());
    }
}
