package com.medicalsys.notificaciones.provider;

import java.util.List;
import reactor.core.publisher.Mono;

/** Envio manual explicito usado por la consola local de WhatsApp. */
public interface WhatsappMessageSender {

    Mono<ProviderResult> sendText(String destination, String message);

    Mono<ProviderResult> sendTemplate(String destination, String templateName,
            String languageCode, List<String> parameters);
}
