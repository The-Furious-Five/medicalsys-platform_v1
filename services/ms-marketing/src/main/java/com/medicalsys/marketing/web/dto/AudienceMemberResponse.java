package com.medicalsys.marketing.web.dto;

import java.util.UUID;

/**
 * Miembro de una audiencia evaluada.
 *
 * <p>Solo lleva identificador, nombre y el resultado de la evaluacion. No hay ningun campo donde
 * pudiera aparecer un motivo clinico, ni siquiera para explicar por que entro en el segmento: el
 * criterio esta en el segmento, no en la persona.
 *
 * @param patientId       UUID publico del paciente
 * @param fullName        nombre completo replicado
 * @param status          resultado individual dentro de la audiencia
 * @param exclusionReason motivo de exclusion, cuando corresponde
 */
public record AudienceMemberResponse(UUID patientId, String fullName, String status, String exclusionReason) {
}
