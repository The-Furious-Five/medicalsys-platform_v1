package com.medicalsys.identidad.web.dto;

import java.util.UUID;

/**
 * Reinicio de contraseña (IAM-05): la temporal se muestra una sola vez al administrador, que la entrega
 * por un canal seguro. Nunca se guarda ni se loguea; sirve para un único ingreso (UPDATE_PASSWORD). El
 * reinicio también borra el segundo factor: la persona vuelve a registrar el QR al entrar (IAM-07).
 */
public record TemporaryPasswordResponse(UUID publicId, String userCode, String username, String temporaryPassword) {
}
