package com.medicalsys.historial.web.dto;

import java.util.UUID;

/**
 * Firma del encuentro.
 *
 * @param certificateId certificado digital usado, opcional en el MVP
 */
public record SignEncounterRequest(UUID certificateId) {
}
