package com.medicalsys.identidad.web.dto;

/**
 * Registro del segundo factor (IAM-07): el QR con la URI otpauth y el mismo secreto en Base32 para
 * cargarlo a mano. Se entrega solo dentro del desafío del primer ingreso y nunca se vuelve a mostrar.
 */
public record MfaEnrollmentResponse(String issuer, String accountName, String secret, String otpauthUri,
                                    String qrImage) {
}
