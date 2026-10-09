package com.medicalsys.facturacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.domain.model.Invoice;
import com.medicalsys.facturacion.domain.model.InvoiceItem;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import com.medicalsys.facturacion.support.TestFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariante monetaria del agregado: el total siempre es la suma de los subtotales.
 *
 * <p>Es una prueba pura, sin Spring ni base: la regla vive en el dominio y debe poder comprobarse
 * sin arrancar nada.
 */
class InvoiceTotalsTest {

    @Test
    @DisplayName("el subtotal de una linea es cantidad por precio menos descuento")
    void computesLineSubtotal() {
        InvoiceItem item = item(1, new BigDecimal("3"), new BigDecimal("150.00"), new BigDecimal("50.00"));

        assertThat(item.getSubtotal()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("el total de la factura coincide con la suma de su detalle")
    void totalMatchesSumOfItems() {
        Invoice invoice = emptyInvoice();
        invoice.addItem(item(1, BigDecimal.ONE, TestFixtures.CONSULTATION_PRICE, null));
        invoice.addItem(item(2, new BigDecimal("2"), TestFixtures.X_RAY_PRICE, new BigDecimal("100.00")));

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("550.00");
        assertThat(invoice.getTotalDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.getVatTaxableAmount()).isEqualByComparingTo(invoice.getTotalAmount());
        assertThat(invoice.totalsMatchItems()).isTrue();
    }

    @Test
    @DisplayName("una factura sin detalle totaliza cero y sigue cuadrando")
    void emptyInvoiceIsConsistent() {
        Invoice invoice = emptyInvoice();

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.totalsMatchItems()).isTrue();
    }

    @Test
    @DisplayName("el redondeo no descuadra la factura: la suma de subtotales es el total exacto")
    void roundingKeepsTheInvoiceBalanced() {
        Invoice invoice = emptyInvoice();
        invoice.addItem(item(1, new BigDecimal("3"), new BigDecimal("33.335"), null));
        invoice.addItem(item(2, new BigDecimal("7"), new BigDecimal("0.115"), null));

        assertThat(invoice.totalsMatchItems()).isTrue();
        assertThat(invoice.getTotalAmount())
                .isEqualByComparingTo(invoice.getItems().get(0).getSubtotal()
                        .add(invoice.getItems().get(1).getSubtotal()));
    }

    @Test
    @DisplayName("una cantidad no positiva se rechaza en el dominio")
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> item(1, BigDecimal.ZERO, new BigDecimal("150.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    @DisplayName("un descuento mayor que el importe se rechaza en el dominio")
    void rejectsDiscountAboveAmount() {
        assertThatThrownBy(() -> item(1, BigDecimal.ONE, new BigDecimal("150.00"), new BigDecimal("200.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descuento");
    }

    private Invoice emptyInvoice() {
        PointOfSale pointOfSale = TestFixtures.pointOfSale();
        return Invoice.open(TestFixtures.ORGANIZATION_ID, TestFixtures.BRANCH_ID, TestFixtures.PATIENT_ID,
                pointOfSale, TestFixtures.cufd(pointOfSale), TaxDocumentType.PURCHASE_SALE_INVOICE, 1L,
                new Invoice.Customer(1, "12345678", null, "PACIENTE DEMO UNO", null), null,
                TestFixtures.USER_ID, TestFixtures.ISSUED_AT);
    }

    private InvoiceItem item(int number, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {
        return InvoiceItem.of(number, TestFixtures.CONSULTATION_ID, null, null, "861020", "99100",
                "CONSULTATION", "Prestacion de demostracion", quantity, 58, unitPrice, discount);
    }
}
