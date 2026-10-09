package com.medicalsys.notificaciones.provider;

import com.medicalsys.notificaciones.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Simulador local: permite probar todo el flujo sin llamar ni cobrar a Meta. */
@Component
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockWhatsappProvider implements NotificationProvider, WhatsappMessageSender {

    @Override
    public Mono<ProviderResult> send(Notification notification) {
        return Mono.just(new ProviderResult("mock-wamid-" + UUID.randomUUID()));
    }

    @Override
    public Mono<ProviderResult> sendText(String destination, String message) {
        return Mono.just(new ProviderResult("mock-wamid-" + UUID.randomUUID()));
    }

    @Override
    public Mono<ProviderResult> sendTemplate(String destination, String templateName,
            String languageCode, List<String> parameters) {
        return Mono.just(new ProviderResult("mock-wamid-" + UUID.randomUUID()));
    }
}
