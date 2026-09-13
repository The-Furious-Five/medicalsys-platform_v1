package com.medicalsys.facturacion.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.facturacion.common.error.ApiException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.config.BillingProperties.IntegrationMode;
import com.medicalsys.facturacion.config.BillingProperties.Tenancy;
import com.medicalsys.facturacion.config.BillingProperties.Tenancy.Local;
import com.medicalsys.facturacion.config.BillingProperties.Tenancy.Offering;
import com.medicalsys.facturacion.support.TestFixtures;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.InstanceOfAssertFactories;

/** El precio lo pone el catalogo, no quien emite la factura. */
class LocalServiceCatalogClientTest {

    private final LocalServiceCatalogClient client = new LocalServiceCatalogClient(properties());

    @Test
    @DisplayName("la prestacion de demostracion devuelve su codigo, descripcion y precio")
    void resolvesDemoOffering() {
        BillableService service = client.billableService(TestFixtures.ORGANIZATION_ID,
                TestFixtures.CONSULTATION_ID);

        assertThat(service.code()).isEqualTo("CONSULTATION");
        assertThat(service.basePriceBob()).isEqualByComparingTo(TestFixtures.CONSULTATION_PRICE);
        assertThat(service.taxProductCode()).isEqualTo("99100");
        assertThat(service.active()).isTrue();
    }

    @Test
    @DisplayName("una prestacion inexistente se rechaza como no facturable")
    void rejectsUnknownOffering() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000009ff");

        assertThatThrownBy(() -> client.billableService(TestFixtures.ORGANIZATION_ID, unknown))
                .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.SERVICE_OFFERING_NOT_BILLABLE);
    }

    private BillingProperties properties() {
        Local local = new Local(List.of(
                new Offering(TestFixtures.CONSULTATION_ID, "CONSULTATION",
                        "Consulta medica general (demostracion)", TestFixtures.CONSULTATION_PRICE, "99100", 58),
                new Offering(TestFixtures.X_RAY_ID, "X_RAY", "Estudio de rayos X (demostracion)",
                        TestFixtures.X_RAY_PRICE, "99200", 58)));
        return new BillingProperties(null,
                new Tenancy(IntegrationMode.LOCAL, "http://ms-tenencia:8082", Duration.ofSeconds(2),
                        Duration.ofSeconds(3), local),
                null, null);
    }
}
