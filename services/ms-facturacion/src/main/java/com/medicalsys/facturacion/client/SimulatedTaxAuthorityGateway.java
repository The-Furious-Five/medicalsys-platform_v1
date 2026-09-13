package com.medicalsys.facturacion.client;

import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador fiscal simulado del entorno local.
 *
 * <p>Lo que este componente <b>hace</b>:
 * <ul>
 *   <li>Deriva CUIS, CUFD y CUF de un SHA-256 de los datos del documento. Los mismos datos producen
 *       siempre el mismo codigo, de modo que la demostracion es reproducible y las pruebas pueden
 *       afirmar un valor exacto.</li>
 *   <li>Devuelve el estado {@link InvoiceStatus#DEMO_ACCEPTED} y la leyenda no tributaria.</li>
 *   <li>Prefija todo codigo con {@code DEMO-} para que nadie lo confunda con uno real.</li>
 * </ul>
 *
 * <p>Lo que este componente <b>no hace</b>, por decision explicita del sprint:
 * <ul>
 *   <li>No abre ninguna conexion de red ni consulta el SIAT.</li>
 *   <li>No usa credenciales, token delegado ni certificado de firma.</li>
 *   <li>No implementa el algoritmo real de CUF ni su digito verificador: copiarlo sin validacion
 *       normativa daria una falsa sensacion de validez fiscal.</li>
 *   <li>No afirma en ningun campo que el documento sea valido ante la autoridad tributaria.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.tax-authority", name = "mode",
        havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedTaxAuthorityGateway implements TaxAuthorityGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTaxAuthorityGateway.class);

    private static final String SEPARATOR = "|";
    private static final int CUIS_HEX_LENGTH = 12;
    private static final int CONTROL_CODE_HEX_LENGTH = 8;
    private static final int RECEIPT_HEX_LENGTH = 16;

    private final String prefix;
    private final String caption;
    private final Duration cuisValidity;
    private final Duration cufdValidity;

    public SimulatedTaxAuthorityGateway(BillingProperties properties) {
        this.prefix = properties.taxAuthority().demoPrefix().toUpperCase(Locale.ROOT) + "-";
        this.caption = properties.taxAuthority().demoCaption();
        this.cuisValidity = properties.billing().demoSetup().cuisValidity();
        this.cufdValidity = properties.billing().demoSetup().cufdValidity();
        log.warn("El adaptador fiscal es una SIMULACION sin red ni credenciales. Las facturas se marcan "
                + "{} y llevan la leyenda: {}", InvoiceStatus.DEMO_ACCEPTED, caption);
    }

    @Override
    public CuisGrant requestCuis(PointOfSaleIdentity identity, Instant requestedAt) {
        LocalDate validUntil = requestedAt.atOffset(ZoneOffset.UTC).toLocalDate()
                .plusDays(cuisValidity.toDays());
        String code = prefix + digest(identity.nit(), identity.taxBranchCode(), identity.pointOfSaleCode(), "CUIS")
                .substring(0, CUIS_HEX_LENGTH);
        return new CuisGrant(code, validUntil);
    }

    @Override
    public CufdGrant requestCufd(PointOfSaleIdentity identity, String cuisCode, Instant validFrom) {
        // La ventana arranca en la hora exacta para que el codigo del dia sea reproducible.
        Instant from = validFrom.truncatedTo(ChronoUnit.HOURS);
        Instant until = from.plus(cufdValidity);
        String code = prefix + digest(identity.nit(), identity.taxBranchCode(), identity.pointOfSaleCode(),
                cuisCode, from.toString());
        String controlCode = prefix + digest(code).substring(0, CONTROL_CODE_HEX_LENGTH);
        return new CufdGrant(code, controlCode, from, until);
    }

    @Override
    public InvoiceReceipt submit(InvoiceSubmission submission) {
        PointOfSaleIdentity identity = submission.identity();
        String cuf = prefix + digest(
                identity.nit(),
                identity.taxBranchCode(),
                identity.pointOfSaleCode(),
                submission.taxDocumentType(),
                submission.invoiceNumber(),
                submission.cufdCode(),
                submission.customerDocumentNumber(),
                normalize(submission.totalAmount()),
                submission.issuedAt().toString());
        String receiptCode = prefix + digest(cuf, "RECEIPT").substring(0, RECEIPT_HEX_LENGTH);
        // Sin emision real no existe una direccion de verificacion que consultar: qrUrl queda nula
        // en lugar de apuntar a una URL inventada que aparente validez.
        return new InvoiceReceipt(cuf, InvoiceStatus.DEMO_ACCEPTED, receiptCode, caption, null,
                submission.issuedAt());
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    /** SHA-256 en hexadecimal de las partes recibidas, unidas por un separador inequivoco. */
    private String digest(Object... parts) {
        StringBuilder material = new StringBuilder();
        for (Object part : parts) {
            material.append(part).append(SEPARATOR);
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si faltara, el entorno esta roto.
            throw new IllegalStateException("La JVM no ofrece SHA-256", e);
        }
    }

    /** Normaliza el importe para que 150 y 150.00 produzcan el mismo codigo. */
    private String normalize(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
