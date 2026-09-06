package com.medicalsys.marketing.domain.enums;

/**
 * Canal por el que ms-notificaciones entregara el mensaje.
 *
 * <p>ms-marketing no envia nada: decide a quien y con que plantilla, y publica el evento. Quien
 * tiene credenciales del proveedor es ms-notificaciones.
 */
public enum CampaignChannel {
    WHATSAPP,
    SMS,
    EMAIL,
    PORTAL
}
