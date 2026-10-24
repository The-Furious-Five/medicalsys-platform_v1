package com.medicalsys.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medicalsys.facturacion.client.TaxAuthorityGateway;
import com.medicalsys.facturacion.common.error.NotFoundException;
import com.medicalsys.facturacion.common.security.Role;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.domain.model.Invoice;
import com.medicalsys.facturacion.mapper.InvoiceMapper;
import com.medicalsys.facturacion.repository.InvoiceRepository;
import com.medicalsys.facturacion.support.TestFixtures;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceCancellationTest {
    private final UUID cashier2 = UUID.randomUUID();

    @Test
    void cancellationPreservesTheDocumentAndTheDemoLabelAndRecordsTheActor() {
        Invoice invoice = demoInvoice();
        invoice.voidInvoice(2, cashier2, TestFixtures.ISSUED_AT.plusSeconds(60));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(invoice.getCancellationReasonCode()).isEqualTo(2);
        assertThat(invoice.getVoidedAt()).isEqualTo(TestFixtures.ISSUED_AT.plusSeconds(60));
        assertThat(invoice.getVoidedByUserPublicId()).isEqualTo(cashier2);
        assertThat(invoice.isDemo()).isTrue();
        assertThat(invoice.getInvoiceNumber()).isEqualTo(1L);
        assertThat(invoice.getCuf()).isEqualTo("CUSTOM-DEMO-CUF");
    }

    @Test
    void theIssuerCannotCancelTheirOwnInvoiceAndACancelledInvoiceCannotBeCancelledAgain() {
        Invoice invoice = demoInvoice();
        assertThatThrownBy(() -> invoice.voidInvoice(1, TestFixtures.USER_ID, TestFixtures.ISSUED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invoice.voidInvoice(5, cashier2, TestFixtures.ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        invoice.voidInvoice(1, cashier2, TestFixtures.ISSUED_AT);
        assertThatThrownBy(() -> invoice.voidInvoice(1, cashier2, TestFixtures.ISSUED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceLocksWithinTheTenantAndReturnsTheCancellationMetadata() {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        TaxAuthorityGateway fiscal = mock(TaxAuthorityGateway.class);
        Invoice invoice = demoInvoice();
        UUID invoiceId = invoice.getPublicId();
        when(invoices.findForCancellation(TestFixtures.ORGANIZATION_ID, invoiceId)).thenReturn(Optional.of(invoice));
        when(invoices.saveAndFlush(any())).thenReturn(invoice);
        when(fiscal.isSimulated()).thenReturn(true);
        InvoiceService service = new InvoiceService(invoices, null, null, null, null, fiscal, null,
                new InvoiceMapper(), Clock.fixed(TestFixtures.ISSUED_AT, ZoneOffset.UTC));
        var response = service.voidInvoice(new RequestContext(TestFixtures.ORGANIZATION_ID, cashier2,
                Set.of(Role.BILLING_SUPERVISOR)), invoiceId, 3);
        assertThat(response.status()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(response.voidedByUserId()).isEqualTo(cashier2);
        assertThat(response.cancellationReasonCode()).isEqualTo(3);
        when(invoices.findForCancellation(TestFixtures.OTHER_ORGANIZATION_ID, invoiceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.voidInvoice(new RequestContext(TestFixtures.OTHER_ORGANIZATION_ID,
                cashier2, Set.of(Role.BILLING_SUPERVISOR)), invoiceId, 3)).isInstanceOf(NotFoundException.class);
    }

    private Invoice demoInvoice() {
        var point = TestFixtures.pointOfSale();
        Invoice invoice = Invoice.open(TestFixtures.ORGANIZATION_ID, TestFixtures.BRANCH_ID, TestFixtures.PATIENT_ID,
                point, TestFixtures.cufd(point), TaxDocumentType.PURCHASE_SALE_INVOICE, 1,
                new Invoice.Customer(1, "123456", null, "CLIENTE DEMO", null), 1, TestFixtures.USER_ID,
                TestFixtures.ISSUED_AT);
        invoice.seal("CUSTOM-DEMO-CUF", InvoiceStatus.DEMO_ACCEPTED, "DEMO-RECEIPT", "SIN VALOR FISCAL", null,
                TestFixtures.ISSUED_AT);
        return invoice;
    }
}
