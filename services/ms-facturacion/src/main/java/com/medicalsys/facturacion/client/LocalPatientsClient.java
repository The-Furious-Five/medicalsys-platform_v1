package com.medicalsys.facturacion.client;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Resuelve el DTO fiscal con datos ficticios deterministas, sin salir del proceso.
 *
 * <p>Existe para que la rebanada vertical funcione mientras ms-pacientes no publique su ruta
 * interna. Los valores se derivan del propio UUID, de modo que el mismo paciente produce siempre la
 * misma factura y ninguna prueba depende de un dato inventado a mano.
 *
 * <p>Los datos son evidentemente ficticios ("PACIENTE DEMO ..."): no imitan a una persona real. Al
 * pasar a {@code PATIENTS_MODE=REMOTE} este componente deja de registrarse.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.patients", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalPatientsClient implements PatientsClient {

    private static final Logger log = LoggerFactory.getLogger(LocalPatientsClient.class);

    private static final int DOCUMENT_DIGITS = 8;

    public LocalPatientsClient() {
        log.warn("El cliente fiscal se resuelve con datos ficticios locales (PATIENTS_MODE=LOCAL). "
                + "Para consultar ms-pacientes use PATIENTS_MODE=REMOTE.");
    }

    @Override
    public FiscalCustomer fiscalCustomer(UUID organizationId, UUID patientId) {
        return new FiscalCustomer(patientId, "CI", documentNumber(patientId), null, legalName(patientId), null);
    }

    /** Numero de documento ficticio estable: los ultimos digitos del UUID, siempre de 8 cifras. */
    private String documentNumber(UUID patientId) {
        long seed = Math.abs(patientId.getLeastSignificantBits() % 100_000_000L);
        return String.format("%0" + DOCUMENT_DIGITS + "d", seed);
    }

    /** Nombre ficticio estable y reconocible como demostracion. */
    private String legalName(UUID patientId) {
        String suffix = patientId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "PACIENTE DEMO " + suffix;
    }
}
