package com.medicalsys.identidad.web.dto;

/**
 * Respuesta del login con contraseña válida (202): falta el segundo factor. {@code status} es
 * MFA_ENROLLMENT_REQUIRED si la cuenta todavía no registró su aplicación autenticadora y MFA_REQUIRED
 * si ya la tiene. {@code mfaToken} solo sirve para ese paso y vence en {@code expiresInSeconds}.
 */
public record MfaChallengeResponse(String status, String mfaToken, long expiresInSeconds, String username) {
}
