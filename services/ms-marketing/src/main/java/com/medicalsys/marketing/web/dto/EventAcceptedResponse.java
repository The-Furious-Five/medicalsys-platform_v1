package com.medicalsys.marketing.web.dto;

import java.util.UUID;

/**
 * Acuse de un evento entrante.
 *
 * <p>{@code duplicate} le dice al productor que el evento ya se habia aplicado. Es informacion util
 * y no un error: un reintento tras un timeout es el comportamiento correcto de un productor, y
 * responderle 409 lo empujaria a tratar como fallo algo que funciono.
 *
 * @param eventId   evento acusado
 * @param eventType tipo recibido
 * @param duplicate true si el inbox ya lo tenia registrado
 * @param detail    que se hizo con el evento
 */
public record EventAcceptedResponse(UUID eventId, String eventType, boolean duplicate, String detail) {
}
