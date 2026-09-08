package com.medicalsys.notificaciones.provider;

import com.medicalsys.notificaciones.domain.Notification;
import reactor.core.publisher.Mono;

public interface NotificationProvider {
    Mono<ProviderResult> send(Notification notification);
}
