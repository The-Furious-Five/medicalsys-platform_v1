package com.medicalsys.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.facturacion.client.PatientsClient;
import com.medicalsys.facturacion.client.ServiceCatalogClient;
import com.medicalsys.facturacion.client.TaxAuthorityGateway;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceReceipt;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceSubmission;
import com.medicalsys.facturacion.common.error.ApiException;
import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.config.BillingProperties.Billing;
import com.medicalsys.facturacion.config.BillingProperties.Billing.DemoSetup;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.domain.model.Cufd;
import com.medicalsys.facturacion.domain.model.Invoice;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import com.medicalsys.facturacion.mapper.InvoiceMapper;
import com.medicalsys.facturacion.repository.InvoiceRepository;
import com.medicalsys.facturacion.service.FiscalSetupService.FiscalContext;
import com.medicalsys.facturacion.support.TestFixtures;
import com.medicalsys.facturacion.web.dto.CreateInvoiceRequest;
import com.medicalsys.facturacion.web.dto.InvoiceLineRequest;
import com.medicalsys.facturacion.web.dto.InvoiceResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Reglas del caso de uso de emision, sin base ni red.
 *
 * <p>Las dos verificaciones del Excel se comprueban aqui a nivel de servicio y otra vez contra el
 * motor real en {@code InvoicePersistenceContainerTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    private static final String CAPTION = "NO VALIDA TRIBUTARIAMENTE - DOCUMENTO DE DEMOSTRACION SIN VALOR FISCAL";

    @Mock
    private InvoiceRepository invoices;

    @Mock
    private FiscalSetupService fiscalSetup;

    @Mock
    private InvoiceNumberGenerator invoiceNumbers;

    @Mock
    private PatientsClient patients;

    @Mock
    private ServiceCatalogClient serviceCatalog;

    @Mock
    private TaxAuthorityGateway taxAuthority;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        PointOfSale pointOfSale = TestFixtures.pointOfSale();
        Cufd cufd = TestFixtures.cufd(pointOfSale);

        when(patients.fiscalCustomer(eq(TestFixtures.ORGANIZATION_ID), any()))
                .thenReturn(TestFixtures.fiscalCustomer());
        when(serviceCatalog.billableService(any(), eq(TestFixtures.CONSULTATION_ID)))
                .thenReturn(TestFixtures.consultation());
        when(serviceCatalog.billableService(any(), eq(TestFixtures.X_RAY_ID)))
                .thenReturn(TestFixtures.xRay());
        when(fiscalSetup.resolve(any(), any(), any()))
                .thenReturn(new FiscalContext(TestFixtures.billingConfiguration(), pointOfSale, cufd));
        when(invoiceNumbers.next(anyLong(), eq(TaxDocumentType.PURCHASE_SALE_INVOICE))).thenReturn(7L);
        when(taxAuthority.submit(any())).thenAnswer(call -> {
            InvoiceSubmission submission = call.getArgument(0);
            return new InvoiceReceipt("DEMO-CUF-" + submission.invoiceNumber(), InvoiceStatus.DEMO_ACCEPTED,
                    "DEMO-RECEIPT", CAPTION, null, submission.issuedAt());
        });
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));

        service = new InvoiceService(invoices, fiscalSetup, invoiceNumbers, patients, serviceCatalog,
                taxAuthority, properties(), new InvoiceMapper(),
                Clock.fixed(TestFixtures.ISSUED_AT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("el total de la factura es la suma de los subtotales del detalle")
    void totalMatchesTheSumOfItems() {
        InvoiceResponse invoice = service.issue(TestFixtures.receptionistContext(), request(
                line(TestFixtures.CONSULTATION_ID, null, null),
                line(TestFixtures.X_RAY_ID, new BigDecimal("2"), new BigDecimal("50.00"))));

        assertThat(invoice.items()).hasSize(2);
        assertThat(invoice.totalAmount()).isEqualByComparingTo("600.00");
        assertThat(invoice.totalAmount()).isEqualByComparingTo(invoice.items().stream()
                .map(item -> item.subtotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(invoice.totalDiscountAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("el precio lo pone el catalogo: el solicitante solo elige que y cuanto")
    void priceComesFromTheCatalog() {
        InvoiceResponse invoice = service.issue(TestFixtures.receptionistContext(),
                request(line(TestFixtures.CONSULTATION_ID, null, null)));

        assertThat(invoice.items().getFirst().unitPrice())
                .isEqualByComparingTo(TestFixtures.CONSULTATION_PRICE);
        assertThat(invoice.items().getFirst().description())
                .isEqualTo("Consulta medica general (demostracion)");
    }

    @Test
    @DisplayName("la factura toma el correlativo del contador y se marca como demostracion")
    void sealsInvoiceWithNumberAndDemoStatus() {
        InvoiceResponse invoice = service.issue(TestFixtures.receptionistContext(),
                request(line(TestFixtures.CONSULTATION_ID, null, null)));

        assertThat(invoice.invoiceNumber()).isEqualTo(7L);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DEMO_ACCEPTED);
        assertThat(invoice.demo()).isTrue();
        assertThat(invoice.caption()).contains("NO VALIDA TRIBUTARIAMENTE");
        assertThat(invoice.cuf()).isEqualTo("DEMO-CUF-7");
    }

    @Test
    @DisplayName("el cliente se congela desde ms-pacientes, no desde el cuerpo de la solicitud")
    void freezesCustomerFromPatientsService() {
        InvoiceResponse invoice = service.issue(TestFixtures.receptionistContext(),
                request(line(TestFixtures.CONSULTATION_ID, null, null)));

        assertThat(invoice.customer().legalName()).isEqualTo("PACIENTE DEMO UNO");
        assertThat(invoice.customer().documentNumber()).isEqualTo("12345678");
        assertThat(invoice.customer().patientId()).isEqualTo(TestFixtures.PATIENT_ID);
    }

    @Test
    @DisplayName("una prestacion invalida no consume correlativo")
    void doesNotConsumeANumberWhenAServiceIsInvalid() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000009ff");
        when(serviceCatalog.billableService(any(), eq(unknown))).thenThrow(
                new BusinessRuleException(ErrorCode.SERVICE_OFFERING_NOT_BILLABLE, "no existe"));

        assertThatThrownBy(() -> service.issue(TestFixtures.receptionistContext(), request(line(unknown, null, null))))
                .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.SERVICE_OFFERING_NOT_BILLABLE);

        verify(invoiceNumbers, never()).next(anyLong(), any());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("una cantidad no facturable se traduce a una regla de negocio, no a un 500")
    void translatesDomainRejectionToBusinessRule() {
        assertThatThrownBy(() -> service.issue(TestFixtures.receptionistContext(),
                request(line(TestFixtures.CONSULTATION_ID, new BigDecimal("1"), new BigDecimal("999.00")))))
                .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.INVOICE_TOTALS_NOT_CONSISTENT);
    }

    private CreateInvoiceRequest request(InvoiceLineRequest... lines) {
        return new CreateInvoiceRequest(TestFixtures.PATIENT_ID, TestFixtures.BRANCH_ID, null, null,
                List.of(lines));
    }

    private InvoiceLineRequest line(UUID serviceOfferingId, BigDecimal quantity, BigDecimal discount) {
        return new InvoiceLineRequest(serviceOfferingId, quantity, discount, TestFixtures.APPOINTMENT_ID,
                TestFixtures.ENCOUNTER_ID);
    }

    private BillingProperties properties() {
        DemoSetup demoSetup = new DemoSetup(true, "0000000000", "CLINICA DEMO MEDICALSYS", "CAJA DEMO", 0, 1,
                "DIRECCION DE DEMOSTRACION - ENTORNO LOCAL", Duration.ofDays(365), Duration.ofHours(24));
        return new BillingProperties(null, null, null, new Billing("861020", "99100", demoSetup));
    }
}
