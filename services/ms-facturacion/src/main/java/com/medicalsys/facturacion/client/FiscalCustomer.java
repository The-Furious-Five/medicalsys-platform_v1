package com.medicalsys.facturacion.client;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO fiscal minimo del paciente, tal como lo publica ms-pacientes.
 *
 * <p>Es deliberadamente pobre: identificador, documento y nombre. ms-facturacion no recibe ni
 * necesita telefono, direccion, alergias, diagnosticos ni ningun dato clinico.
 *
 * @param patientId          UUID publico del paciente
 * @param documentType       tipo de documento en el vocabulario de la plataforma (CI, CEX, PAS...)
 * @param documentNumber     numero de documento
 * @param documentComplement complemento del CI boliviano; puede ser nulo o vacio
 * @param legalName          nombre o razon social que se imprime en la factura
 * @param email              correo para enviar el documento; opcional
 */
public record FiscalCustomer(
        UUID patientId,
        String documentType,
        String documentNumber,
        String documentComplement,
        String legalName,
        String email) {

    public FiscalCustomer {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(legalName, "legalName");
    }

    /**
     * Traduce el tipo de documento al codigo numerico del catalogo del SIN.
     *
     * <p>1 = CI, 2 = CEX, 3 = PAS, 4 = OD (otro documento), 5 = NIT. Un tipo desconocido se factura
     * como "otro documento" en vez de rechazar el cobro.
     */
    public int identityDocumentTypeCode() {
        if (documentType == null) {
            return 1;
        }
        return switch (documentType.trim().toUpperCase(Locale.ROOT)) {
            case "CI" -> 1;
            case "CEX" -> 2;
            case "PAS", "PASSPORT" -> 3;
            case "NIT" -> 5;
            default -> 4;
        };
    }

    /** Complemento normalizado: la base guarda {@code NULL}, no cadena vacia. */
    public String normalizedComplement() {
        return documentComplement == null || documentComplement.isBlank() ? null : documentComplement.trim();
    }
}
