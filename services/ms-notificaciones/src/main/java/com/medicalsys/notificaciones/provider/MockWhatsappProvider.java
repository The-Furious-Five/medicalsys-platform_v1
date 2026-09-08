package com.medicalsys.notificaciones.provider;

import com.medicalsys.notificaciones.domain.Notification;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Simulador local: permite probar todo el flujo sin llamar ni cobrar a Meta. */
@Component
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockWhatsappProvider implements NotificationProvider {

    @Override
    public Mono<ProviderResult> send(Notification notification) {
        return Mono.just(new ProviderResult("mock-wamid-" + UUID.randomUUID()));
    }
}
