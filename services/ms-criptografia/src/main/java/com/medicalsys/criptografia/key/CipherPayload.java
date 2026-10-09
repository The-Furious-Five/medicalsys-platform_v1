package com.medicalsys.criptografia.key;

import java.util.UUID;

/** Respuesta de cifrado alineada con ms-historial. */
public record CipherPayload(UUID keyPublicId, String ciphertext) {
}
