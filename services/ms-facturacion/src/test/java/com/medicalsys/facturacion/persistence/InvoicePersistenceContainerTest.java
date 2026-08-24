package com.medicalsys.facturacion.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.facturacion.common.error.ApiException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.service.InvoiceService;
import com.medicalsys.facturacion.support.AbstractMySqlContainerTest;
import com.medicalsys.facturacion.support.TestFixtures;
import com.medicalsys.facturacion.web.dto.CreateInvoiceRequest;
import com.medicalsys.facturacion.web.dto.InvoiceItemResponse;
import com.medicalsys.facturacion.web.dto.InvoiceLineRequest;
import com.medicalsys.facturacion.web.dto.InvoiceResponse;
import com.medicalsys.facturacion.web.dto.PointOfSaleRef;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reglas criticas de la factura contra el motor real.
 *
 * <p>Cubre las verificaciones comprometidas en el Excel del sprint: el total coincide con la suma de
 * los detalles, el correlativo no se repite y la respuesta lleva la etiqueta de demostracion.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class InvoicePersistenceContainerTest extends AbstractMySqlContainerTest {

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private DataSource dataSource;

    private final RequestContext context = TestFixtures.receptionistContext();

    @Test
    @DisplayName("la factura y su detalle quedan persistidos y el total cuadra con la suma")
    void persistsInvoiceAndItemsWithMatchingTotal() {
        InvoiceResponse invoice = issue(UUID.randomUUID(),
                line(TestFixtures.CONSULTATION_ID, null, null),
                line(TestFixtures.X_RAY_ID, new BigDecimal("2"), new BigDecimal("50.00")));

        BigDecimal sumOfItems = invoice.items().stream()
                .map(InvoiceItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(invoice.totalAmount()).isEqualByComparingTo("600.00").isEqualByComparingTo(sumOfItems);

        BigDecimal persistedTotal = jdbc()
                .sql("SELECT total_amount FROM invoice WHERE public_id = UUID_TO_BIN(?)")
                .param(1, invoice.id().toString())
                .query(BigDecimal.class).single();
        BigDecimal persistedItems = jdbc().sql("""
                        SELECT SUM(i.subtotal) FROM invoice_item i
                        JOIN invoice f ON f.id = i.invoice_id
                        WHERE f.public_id = UUID_TO_BIN(?)
                        """)
                .param(1, invoice.id().toString())
                .query(BigDecimal.class).single();

        assertThat(persistedTotal).isEqualByComparingTo(persistedItems);
    }

    @Test
    @DisplayName("el correlativo avanza de uno en uno y no se repite dentro del punto de venta")
    void invoiceNumberIsSequentialAndUnique() {
        InvoiceResponse first = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));
        InvoiceResponse second = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        assertThat(second.invoiceNumber()).isEqualTo(first.invoiceNumber() + 1);
        assertThat(second.pointOfSale()).isEqualTo(first.pointOfSale());

        Integer duplicates = jdbc().sql("""
                        SELECT COUNT(*) FROM (
                            SELECT point_of_sale_id, tax_document_type, invoice_number
                            FROM invoice
                            GROUP BY point_of_sale_id, tax_document_type, invoice_number
                            HAVING COUNT(*) > 1
                        ) repetidos
                        """)
                .query(Integer.class).single();

        assertThat(duplicates).isZero();
    }

    @Test
    @DisplayName("la base rechaza un correlativo repetido aunque se escriba por fuera del servicio")
    void databaseRejectsARepeatedNumber() {
        InvoiceResponse existing = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        assertThatThrownBy(() -> jdbc().sql("""
                        INSERT INTO invoice (public_id, organization_public_id, branch_public_id,
                                             patient_public_id, point_of_sale_id, cufd_id, invoice_number,
                                             cuf, customer_document_number, customer_legal_name,
                                             total_amount, vat_taxable_amount, currency_total_amount, issued_at)
                        SELECT UUID_TO_BIN(?), organization_public_id, branch_public_id, patient_public_id,
                               point_of_sale_id, cufd_id, invoice_number, ?, customer_document_number,
                               customer_legal_name, total_amount, vat_taxable_amount, currency_total_amount,
                               issued_at
                        FROM invoice WHERE public_id = UUID_TO_BIN(?)
                        """)
                .param(1, UUID.randomUUID().toString())
                .param(2, "DEMO-CUF-DUPLICADO")
                .param(3, existing.id().toString())
                .update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("la factura sale marcada como demostracion, con leyenda no tributaria")
    void invoiceIsLabelledAsDemo() {
        InvoiceResponse invoice = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DEMO_ACCEPTED);
        assertThat(invoice.demo()).isTrue();
        assertThat(invoice.caption()).contains("NO VALIDA TRIBUTARIAMENTE");
        assertThat(invoice.cuf()).startsWith("DEMO-");

        String persistedStatus = jdbc().sql("SELECT status FROM invoice WHERE public_id = UUID_TO_BIN(?)")
                .param(1, invoice.id().toString())
                .query(String.class).single();

        assertThat(persistedStatus).isEqualTo("DEMO_ACCEPTED");
    }

    @Test
    @DisplayName("la consulta devuelve total, correlativo y etiqueta listos para la coleccion E2E")
    void getReturnsTheContractOfTheDemoCollection() {
        InvoiceResponse issued = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        InvoiceResponse fetched = invoices.get(context, issued.id());

        assertThat(fetched.id()).isEqualTo(issued.id());
        assertThat(fetched.invoiceNumber()).isEqualTo(issued.invoiceNumber());
        assertThat(fetched.totalAmount()).isEqualByComparingTo(issued.totalAmount());
        assertThat(fetched.status()).isEqualTo(InvoiceStatus.DEMO_ACCEPTED);
        assertThat(fetched.demo()).isTrue();
        assertThat(fetched.caption()).isEqualTo(issued.caption());
        assertThat(fetched.items()).hasSize(1);
        assertThat(fetched.customer().legalName()).startsWith("PACIENTE DEMO ");
    }

    @Test
    @DisplayName("una factura de otra organizacion no es visible ni por identificador")
    void isolatesInvoicesPerOrganization() {
        InvoiceResponse own = issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        assertThatThrownBy(() -> invoices.get(TestFixtures.otherTenantContext(), own.id()))
                .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.INVOICE_NOT_FOUND);
    }

    @Test
    @DisplayName("el listado por paciente devuelve solo sus facturas")
    void listsInvoicesByPatient() {
        UUID patientId = UUID.randomUUID();
        issue(patientId, line(TestFixtures.CONSULTATION_ID, null, null));
        issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        var page = invoices.list(context, patientId, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.patientId()).isEqualTo(patientId);
            assertThat(summary.demo()).isTrue();
        });
    }

    @Test
    @DisplayName("un punto de venta inexistente se responde como no encontrado")
    void rejectsAnUnknownPointOfSale() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(UUID.randomUUID(), TestFixtures.BRANCH_ID,
                new PointOfSaleRef(9, 9), null, List.of(line(TestFixtures.CONSULTATION_ID, null, null)));

        assertThatThrownBy(() -> invoices.issue(context, request))
                .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.POINT_OF_SALE_NOT_FOUND);
    }

    @Test
    @DisplayName("el alta automatica de demostracion crea configuracion, caja y CUFD una sola vez")
    void demoSetupIsCreatedOnce() {
        issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));
        issue(UUID.randomUUID(), line(TestFixtures.CONSULTATION_ID, null, null));

        Integer configurations = count("billing_configuration");
        Integer pointsOfSale = count("point_of_sale");
        Integer cufdCodes = count("cufd");

        assertThat(configurations).isEqualTo(1);
        assertThat(pointsOfSale).isEqualTo(1);
        assertThat(cufdCodes).isEqualTo(1);
    }

    private InvoiceResponse issue(UUID patientId, InvoiceLineRequest... lines) {
        return invoices.issue(context, new CreateInvoiceRequest(patientId, TestFixtures.BRANCH_ID, null, null,
                List.of(lines)));
    }

    private InvoiceLineRequest line(UUID serviceOfferingId, BigDecimal quantity, BigDecimal discount) {
        return new InvoiceLineRequest(serviceOfferingId, quantity, discount, TestFixtures.APPOINTMENT_ID,
                TestFixtures.ENCOUNTER_ID);
    }

    private Integer count(String table) {
        return jdbc().sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
