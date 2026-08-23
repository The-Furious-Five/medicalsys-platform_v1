package com.medicalsys.facturacion.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.facturacion.client.TaxAuthorityGateway.CufdGrant;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.CuisGrant;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceReceipt;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceSubmission;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.PointOfSaleIdentity;
import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.config.BillingProperties.Billing;
import com.medicalsys.facturacion.config.BillingProperties.Billing.DemoSetup;
import com.medicalsys.facturacion.config.BillingProperties.TaxAuthority;
import com.medicalsys.facturacion.config.BillingProperties.TaxAuthorityMode;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del adaptador fiscal simulado.
 *
 * <p>Comprueba lo que el sprint exige verificar: estado {@code DEMO_ACCEPTED}, leyenda no
 * tributaria, codigos deterministas y ausencia de cualquier afirmacion de validez fiscal.
 */
class SimulatedTaxAuthorityGatewayTest {

    private static final String CAPTION = "NO VALIDA TRIBUTARIAMENTE - DOCUMENTO DE DEMOSTRACION SIN VALOR FISCAL";

    private final SimulatedTaxAuthorityGateway gateway = new SimulatedTaxAuthorityGateway(properties());

    private final PointOfSaleIdentity identity = new PointOfSaleIdentity("0000000000", 0, 1);

    @Test
    @DisplayName("la factura se acepta con estado DEMO_ACEPTADA y leyenda no tributaria")
    void acceptsInvoiceAsDemo() {
        InvoiceReceipt receipt = gateway.submit(submission(1L));

        assertThat(receipt.status()).isEqualTo(InvoiceStatus.DEMO_ACCEPTED);
        assertThat(receipt.caption()).isEqualTo(CAPTION);
        assertThat(receipt.caption()).contains("NO VALIDA TRIBUTARIAMENTE");
        assertThat(gateway.isSimulated()).isTrue();
    }

    @Test
    @DisplayName("los mismos datos producen siempre el mismo CUF: el stub es determinista")
    void isDeterministic() {
        assertThat(gateway.submit(submission(1L)).cuf()).isEqualTo(gateway.submit(submission(1L)).cuf());
    }

    @Test
    @DisplayName("dos correlativos distintos producen CUF distintos")
    void differentNumbersProduceDifferentCuf() {
        assertThat(gateway.submit(submission(1L)).cuf()).isNotEqualTo(gateway.submit(submission(2L)).cuf());
    }

    @Test
    @DisplayName("todo codigo generado lleva el prefijo DEMO y cabe en su columna")
    void codesAreLabelledAsDemoAndFitTheSchema() {
        InvoiceReceipt receipt = gateway.submit(submission(1L));
        CuisGrant cuis = gateway.requestCuis(identity, TestFixtures.ISSUED_AT);
        CufdGrant cufd = gateway.requestCufd(identity, cuis.code(), TestFixtures.ISSUED_AT);

        assertThat(receipt.cuf()).startsWith("DEMO-").hasSizeLessThanOrEqualTo(80);
        assertThat(receipt.receiptCode()).startsWith("DEMO-").hasSizeLessThanOrEqualTo(50);
        assertThat(cuis.code()).startsWith("DEMO-").hasSizeLessThanOrEqualTo(30);
        assertThat(cufd.code()).startsWith("DEMO-").hasSizeLessThanOrEqualTo(200);
        assertThat(cufd.controlCode()).startsWith("DEMO-").hasSizeLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("sin emision real no se inventa una URL de verificacion")
    void doesNotFabricateAVerificationUrl() {
        assertThat(gateway.submit(submission(1L)).qrUrl()).isNull();
    }

    @Test
    @DisplayName("el CUFD tiene una ventana de vigencia valida")
    void cufdHasAValidWindow() {
        CuisGrant cuis = gateway.requestCuis(identity, TestFixtures.ISSUED_AT);
        CufdGrant cufd = gateway.requestCufd(identity, cuis.code(), TestFixtures.ISSUED_AT);

        assertThat(cufd.validUntil()).isAfter(cufd.validFrom());
        assertThat(cuis.validUntil()).isAfter(TestFixtures.ISSUED_AT.atZone(java.time.ZoneOffset.UTC)
                .toLocalDate());
    }

    private InvoiceSubmission submission(long invoiceNumber) {
        return new InvoiceSubmission(identity, TaxDocumentType.PURCHASE_SALE_INVOICE, invoiceNumber,
                "DEMO-CUFD0001", "12345678", new BigDecimal("150.00"), TestFixtures.ISSUED_AT);
    }

    private BillingProperties properties() {
        DemoSetup demoSetup = new DemoSetup(true, "0000000000", "CLINICA DEMO MEDICALSYS", "CAJA DEMO", 0, 1,
                "DIRECCION DE DEMOSTRACION - ENTORNO LOCAL", Duration.ofDays(365), Duration.ofHours(24));
        return new BillingProperties(null, null,
                new TaxAuthority(TaxAuthorityMode.SIMULATED, CAPTION, "DEMO"),
                new Billing("861020", "99100", demoSetup));
    }
}
