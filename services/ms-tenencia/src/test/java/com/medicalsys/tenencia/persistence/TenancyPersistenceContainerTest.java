package com.medicalsys.tenencia.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.tenencia.common.error.ApiException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import com.medicalsys.tenencia.service.BranchService;
import com.medicalsys.tenencia.service.OrganizationService;
import com.medicalsys.tenencia.service.ServiceOfferingService;
import com.medicalsys.tenencia.support.AbstractMySqlContainerTest;
import com.medicalsys.tenencia.support.TestFixtures;
import com.medicalsys.tenencia.web.dto.BillableServiceResponse;
import com.medicalsys.tenencia.web.dto.BranchResponse;
import com.medicalsys.tenencia.web.dto.ChangeBranchStatusRequest;
import com.medicalsys.tenencia.web.dto.ChangeOrganizationStatusRequest;
import com.medicalsys.tenencia.web.dto.OrganizationResponse;
import com.medicalsys.tenencia.web.dto.ServiceOfferingResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Reglas criticas de la tenencia contra el motor real.
 *
 * <p>Cubre lo que el Excel del sprint compromete para este servicio: el codigo de una prestacion es
 * unico dentro de la organizacion, el limite de sucursales del plan se aplica, el precio se publica
 * como DECIMAL y ninguna organizacion alcanza los datos de otra.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class TenancyPersistenceContainerTest extends AbstractMySqlContainerTest {

    /** Sufijo unico por caso, para que cada prueba cree su propio tenant sin chocar con otro. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private BranchService branches;

    @Autowired
    private ServiceOfferingService offerings;

    @Nested
    @DisplayName("alta de la organizacion")
    class Onboarding {

        @Test
        @DisplayName("el alta crea el tenant con su UUID publico, su suscripcion y sus contadores")
        void createsTenantWithSubscriptionAndCounters() {
            OrganizationResponse organization = newTenant();

            assertThat(organization.id()).isNotNull();
            assertThat(organization.status()).isEqualTo(OrganizationStatus.ONBOARDING);
            assertThat(organization.timeZone()).isEqualTo("America/La_Paz");
            assertThat(organization.currency()).isEqualTo("BOB");
        }

        @Test
        @DisplayName("un codigo de organizacion repetido devuelve 409")
        void rejectsDuplicatedCode() {
            String code = uniqueCode("DUP");
            organizations.create(TestFixtures.newOrganization(code, uniqueNit()));

            assertThatThrownBy(() -> organizations.create(TestFixtures.newOrganization(code, uniqueNit())))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.ORGANIZATION_CODE_ALREADY_USED);
        }

        @Test
        @DisplayName("un NIT repetido devuelve 409")
        void rejectsDuplicatedNit() {
            String nit = uniqueNit();
            organizations.create(TestFixtures.newOrganization(uniqueCode("NIT"), nit));

            assertThatThrownBy(() -> organizations.create(TestFixtures.newOrganization(uniqueCode("NIT"), nit)))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.ORGANIZATION_NIT_ALREADY_USED);
        }
    }

    @Nested
    @DisplayName("catalogo de prestaciones")
    class Offerings {

        @Test
        @DisplayName("el codigo de una prestacion es unico dentro de la organizacion")
        void rejectsDuplicatedCodeWithinTheOrganization() {
            RequestContext context = activeTenant();
            offerings.create(context, TestFixtures.newOffering("CONSULTA", new BigDecimal("150.00")));

            assertThatThrownBy(() -> offerings.create(context,
                    TestFixtures.newOffering("CONSULTA", new BigDecimal("200.00"))))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.SERVICE_OFFERING_CODE_ALREADY_USED);
        }

        @Test
        @DisplayName("dos organizaciones distintas pueden usar el mismo codigo de prestacion")
        void allowsTheSameCodeInAnotherOrganization() {
            RequestContext first = activeTenant();
            RequestContext second = activeTenant();

            offerings.create(first, TestFixtures.newOffering("CONSULTA", new BigDecimal("150.00")));
            ServiceOfferingResponse other = offerings.create(second,
                    TestFixtures.newOffering("CONSULTA", new BigDecimal("200.00")));

            assertThat(other.code()).isEqualTo("CONSULTA");
            assertThat(other.organizationId()).isEqualTo(second.organizationId());
        }

        @Test
        @DisplayName("el precio se persiste como DECIMAL con dos decimales, nunca como coma flotante")
        void persistsPriceAsDecimal() {
            RequestContext context = activeTenant();

            ServiceOfferingResponse offering = offerings.create(context,
                    TestFixtures.newOffering("PRECIO", new BigDecimal("150.005")));

            assertThat(offering.basePriceBob()).isEqualByComparingTo("150.01");
            assertThat(offering.basePriceBob().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("una prestacion de otra organizacion no es visible ni conociendo su UUID")
        void doesNotLeakOfferingsFromAnotherTenant() {
            RequestContext owner = activeTenant();
            RequestContext intruder = activeTenant();
            ServiceOfferingResponse offering = offerings.create(owner,
                    TestFixtures.newOffering("PRIVADA", new BigDecimal("300.00")));

            assertThatThrownBy(() -> offerings.get(intruder, offering.id()))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.SERVICE_OFFERING_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("contrato interno con ms-facturacion")
    class BillableContract {

        @Test
        @DisplayName("la ruta interna publica codigo, descripcion y precio de la prestacion")
        void publishesTheBillableSnapshot() {
            RequestContext context = activeTenant();
            ServiceOfferingResponse offering = offerings.create(context,
                    TestFixtures.newOffering("CONSULTATION", new BigDecimal("150.00")));

            BillableServiceResponse billable = offerings.getBillable(context.organizationId(), offering.id());

            assertThat(billable.id()).isEqualTo(offering.id());
            assertThat(billable.code()).isEqualTo("CONSULTATION");
            assertThat(billable.basePriceBob()).isEqualByComparingTo("150.00");
            assertThat(billable.active()).isTrue();
        }

        @Test
        @DisplayName("la prestacion sembrada para la demo conserva el precio que espera facturacion")
        void seededDemoOfferingsMatchTheBillingCatalog() {
            BillableServiceResponse consultation = offerings.getBillable(TestFixtures.DEMO_ORGANIZATION_ID,
                    TestFixtures.CONSULTATION_ID);
            BillableServiceResponse xRay = offerings.getBillable(TestFixtures.DEMO_ORGANIZATION_ID,
                    TestFixtures.X_RAY_ID);

            assertThat(consultation.code()).isEqualTo("CONSULTATION");
            assertThat(consultation.basePriceBob()).isEqualByComparingTo(TestFixtures.CONSULTATION_PRICE);
            assertThat(consultation.taxUnitCode()).isEqualTo(58);
            assertThat(xRay.code()).isEqualTo("X_RAY");
            assertThat(xRay.basePriceBob()).isEqualByComparingTo(TestFixtures.X_RAY_PRICE);
        }

        @Test
        @DisplayName("otra organizacion no puede leer el precio de una prestacion ajena")
        void doesNotLeakPricesAcrossTenants() {
            assertThatThrownBy(() -> offerings.getBillable(TestFixtures.OTHER_ORGANIZATION_ID,
                    TestFixtures.CONSULTATION_ID))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.SERVICE_OFFERING_NOT_FOUND);
        }

        @Test
        @DisplayName("una prestacion desactivada sigue siendo legible y se marca como no vigente")
        void reportsDeactivatedOfferings() {
            RequestContext context = activeTenant();
            ServiceOfferingResponse offering = offerings.create(context,
                    TestFixtures.newOffering("BAJA", new BigDecimal("100.00")));

            offerings.changeActive(context,
                    offering.id(),
                    new com.medicalsys.tenencia.web.dto.ChangeServiceOfferingActiveRequest(false));

            assertThat(offerings.getBillable(context.organizationId(), offering.id()).active()).isFalse();
        }
    }

    @Nested
    @DisplayName("limite de sucursales del plan")
    class BranchQuota {

        @Test
        @DisplayName("el plan DEMO permite una sola sucursal y la segunda devuelve 422")
        void enforcesTheBranchLimitOfThePlan() {
            RequestContext context = activeTenant();
            branches.create(context, TestFixtures.newBranch("CENTRAL", true));

            assertThatThrownBy(() -> branches.create(context, TestFixtures.newBranch("SUR", false)))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.PLAN_LIMIT_REACHED);
        }

        @Test
        @DisplayName("dar de baja una sucursal libera cuota y permite registrar otra")
        void releasesQuotaWhenABranchIsDeactivated() {
            RequestContext context = activeTenant();
            BranchResponse first = branches.create(context, TestFixtures.newBranch("CENTRAL", true));

            branches.changeStatus(context, first.id(), new ChangeBranchStatusRequest(BranchStatus.INACTIVE));
            BranchResponse second = branches.create(context, TestFixtures.newBranch("SUR", false));

            assertThat(second.code()).isEqualTo("SUR");
            assertThat(second.status()).isEqualTo(BranchStatus.ACTIVE);
        }

        @Test
        @DisplayName("el codigo de sucursal es unico dentro de la organizacion")
        void rejectsDuplicatedBranchCode() {
            RequestContext context = activeTenant();
            branches.create(context, TestFixtures.newBranch("CENTRAL", false));

            assertThatThrownBy(() -> branches.create(context, TestFixtures.newBranch("CENTRAL", false)))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.BRANCH_CODE_ALREADY_USED);
        }

        @Test
        @DisplayName("una sucursal de otra organizacion no es visible ni conociendo su UUID")
        void doesNotLeakBranchesFromAnotherTenant() {
            RequestContext owner = activeTenant();
            RequestContext intruder = activeTenant();
            BranchResponse branch = branches.create(owner, TestFixtures.newBranch("CENTRAL", true));

            assertThatThrownBy(() -> branches.get(intruder, branch.id()))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.BRANCH_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("estado de la organizacion")
    class Status {

        @Test
        @DisplayName("una organizacion en ONBOARDING no admite configuracion nueva")
        void rejectsConfigurationWhileOnboarding() {
            OrganizationResponse organization =
                    organizations.create(TestFixtures.newOrganization(uniqueCode("ONB"), uniqueNit()));
            RequestContext context = TestFixtures.contextFor(organization.id());

            assertThatThrownBy(() -> branches.create(context, TestFixtures.newBranch("CENTRAL", true)))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("un tenant inexistente devuelve 404 y no revela nada mas")
        void reportsUnknownTenantAsNotFound() {
            RequestContext context = TestFixtures.contextFor(UUID.randomUUID());

            assertThatThrownBy(() -> organizations.getCurrent(context))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::code)
                    .isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND);
        }
    }

    /** Crea un tenant nuevo en ONBOARDING con su suscripcion de prueba. */
    private OrganizationResponse newTenant() {
        return organizations.create(TestFixtures.newOrganization(uniqueCode("ORG"), uniqueNit()));
    }

    /** Crea un tenant nuevo y lo activa, que es el estado en que se configura la clinica. */
    private RequestContext activeTenant() {
        OrganizationResponse organization = newTenant();
        RequestContext context = TestFixtures.contextFor(organization.id());
        organizations.changeStatus(context, new ChangeOrganizationStatusRequest(OrganizationStatus.ACTIVE));
        return context;
    }

    private String uniqueCode(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }

    /** NIT ficticio de diez digitos, distinto en cada llamada. */
    private String uniqueNit() {
        return String.format("9%09d", SEQUENCE.incrementAndGet());
    }
}
