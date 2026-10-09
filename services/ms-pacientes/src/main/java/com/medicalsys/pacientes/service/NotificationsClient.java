package com.medicalsys.pacientes.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medicalsys.pacientes.common.error.ApiException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Envia el codigo OTP de autorizacion de acceso por WhatsApp usando el envio manual de
 * ms-notificaciones ({@code POST /api/v1/notifications/whatsapp/send}), no el pipeline de reglas
 * automaticas: ese envio no depende de la ventana de 24h de la conversacion, asi que el codigo le
 * llega al paciente aunque nunca le haya escrito antes a la clinica que pide el acceso.
 */
@Component
public class NotificationsClient {

    private final RestClient client;

    public NotificationsClient(@Value("${medicalsys.notifications.base-url}") String baseUrl,
            RestClient.Builder builder) {
        this.client = builder.clone().baseUrl(baseUrl).build();
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
