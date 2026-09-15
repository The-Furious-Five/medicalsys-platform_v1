package com.medicalsys.documental.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medicalsys.documental.config.ConsentIntegrationProperties;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Envia el codigo OTP por WhatsApp usando el envio manual de ms-notificaciones
 * ({@code POST /api/v1/notifications/whatsapp/send}), no el pipeline de reglas automaticas: ese
 * envio no depende de la ventana de 24h de la conversacion (solo aplica al proveedor Meta real, no
 * al mock de este entorno), asi que el codigo llega aunque el paciente nunca le haya escrito antes a
 * la clinica.
 */
@Component
public class NotificationsClient {

    private final RestClient client;

    public NotificationsClient(ConsentIntegrationProperties properties, RestClient.Builder builder) {
        this.client = builder.clone().baseUrl(properties.notificationsBaseUrl()).build();
    }

    public void sendWhatsappText(String organizationId, String destination, String message) {
        try {
            client.post()
                    .uri("/api/v1/notifications/whatsapp/send")
                    .header("X-Organization-Id", organizationId)
                    .body(new SendRequest(destination, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new ApiException(ErrorCode.NOTIFICATION_DELIVERY_FAILED,
                    "ms-notificaciones no esta disponible: " + exception.getMessage());
        } catch (HttpClientErrorException exception) {
            throw new ApiException(ErrorCode.NOTIFICATION_DELIVERY_FAILED,
                    "ms-notificaciones rechazo el envio del codigo: " + exception.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendRequest(String destination, String message) {
    }
}
