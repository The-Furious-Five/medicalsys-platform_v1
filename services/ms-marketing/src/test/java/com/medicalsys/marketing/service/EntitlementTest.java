package com.medicalsys.marketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.marketing.common.error.ApiException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.support.AbstractMySqlContainerTest;
import com.medicalsys.marketing.support.TestFixtures;
import com.medicalsys.marketing.repository.RefOrganizationRepository;
import com.medicalsys.marketing.web.dto.CreateSegmentRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba el criterio C5: el modulo es opcional por contrato.
 *
 * <p>La opcionalidad seria un argumento de diseno sin consecuencias si el servicio atendiera igual a
 * un tenant cuyo plan no lo incluye. Estas pruebas verifican que no lo hace: el contenedor puede
 * estar desplegado y sano y aun asi negarse, que es exactamente lo que significa
 * {@code incluye_marketing = 0}.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class EntitlementTest extends AbstractMySqlContainerTest {

    @Autowired
    private MarketingEntitlementService entitlement;
    @Autowired
    private ReferenceDataService referenceData;
    @Autowired
    private SegmentService segments;
    @Autowired
    private RefOrganizationRepository organizations;

    @Test
    @DisplayName("un tenant con includes_marketing = 0 recibe 403 en las rutas de gestion")
    void deniesTenantWithoutMarketingInPlan() {
        referenceData.applyOrganization(TestFixtures.OTHER_ORGANIZATION_ID, "CLINICA SIN MARKETING", false, 1L);

        assertThatThrownBy(() -> segments.create(TestFixtures.otherAdministrator(),
                new CreateSegmentRequest("Cualquiera", null, Map.of("minAge", 18))))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.MARKETING_NOT_INCLUDED_IN_PLAN);
    }

    @Test
    @DisplayName("un tenant con el modulo contratado pasa la comprobacion")
    void allowsTenantWithMarketingInPlan() {
        referenceData.applyOrganization(TestFixtures.ORGANIZATION_ID, "CLINICA DEMO", true, 1L);

        assertThat(entitlement.isEntitled(TestFixtures.ORGANIZATION_ID)).isTrue();
        assertThatCode(() -> entitlement.requireEntitled(TestFixtures.ORGANIZATION_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tenant cuyo plan aun no se replico se resuelve con el valor por defecto")
    void fallsBackToTheConfiguredDefault() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

        // defaultIncluded = true en el perfil de pruebas: el tenant pasa sin que la comprobacion
        // escriba nada. Crear la replica es tarea de ReferenceDataService, en los casos de escritura.
        assertThat(entitlement.isEntitled(unknown)).isTrue();
        assertThatCode(() -> entitlement.requireEntitled(unknown)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la comprobacion no escribe: no registra al tenant desconocido que consulta")
    void checkingEntitlementDoesNotWrite() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000000fe");
        entitlement.requireEntitled(unknown);

        // Importa porque la mitad de las llamadas ocurren en transacciones readOnly, donde una
        // escritura no se descartaria con un error sino en silencio.
        assertThat(organizations.findById(unknown)).isEmpty();
    }
}
