package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Nunca expone {@code detail}: ahi es donde vive el hash del OTP mientras esta pendiente, y no hay
 * ninguna razon para que un cliente HTTP lo vea, ni siquiera hasheado.
 */
public record ConsentEventResponse(String type, Instant occurredAt, UUID userAccountId) {
}
