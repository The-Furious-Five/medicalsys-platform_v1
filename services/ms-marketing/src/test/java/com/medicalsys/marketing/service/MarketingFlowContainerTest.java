package com.medicalsys.marketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.marketing.common.error.ApiException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.domain.enums.CampaignChannel;
import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.CampaignType;
import com.medicalsys.marketing.domain.enums.DiscountType;
import com.medicalsys.marketing.domain.enums.LoyaltyMovementType;
import com.medicalsys.marketing.domain.enums.OutboxStatus;
import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.RecipientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import com.medicalsys.marketing.event.EventIngestionService;
import com.medicalsys.marketing.event.MarketingEventPublisher;
import com.medicalsys.marketing.repository.OutboxEventRepository;
import com.medicalsys.marketing.support.AbstractMySqlContainerTest;
import com.medicalsys.marketing.support.TestFixtures;
import com.medicalsys.marketing.web.dto.CampaignResponse;
import com.medicalsys.marketing.web.dto.CouponRedemptionResponse;
import com.medicalsys.marketing.web.dto.CouponValidationResponse;
import com.medicalsys.marketing.web.dto.CreateCampaignRequest;
import com.medicalsys.marketing.web.dto.CreateLoyaltyProgramRequest;
import com.medicalsys.marketing.web.dto.CreatePromotionRequest;
import com.medicalsys.marketing.web.dto.CreateSegmentRequest;
import com.medicalsys.marketing.web.dto.EventAcceptedResponse;
import com.medicalsys.marketing.web.dto.IncomingEventRequest;
import com.medicalsys.marketing.web.dto.LoyaltyBalanceResponse;
import com.medicalsys.marketing.web.dto.LoyaltyMovementRequest;
import com.medicalsys.marketing.web.dto.MaterializeAudienceResponse;
import com.medicalsys.marketing.web.dto.PatientActivityResponse;
import com.medicalsys.marketing.web.dto.RedeemCouponRequest;
import com.medicalsys.marketing.web.dto.SegmentPreviewResponse;
import com.medicalsys.marketing.web.dto.SegmentResponse;
import com.medicalsys.marketing.web.dto.ValidateCouponRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Recorrido funcional de ms-marketing contra el motor real.
 *
 * <p>Cubre el camino completo del servicio -- replicar, segmentar, materializar, lanzar, cobrar con
 * cupon, acumular puntos y medir -- y los casos que dan sentido a su arquitectura: el 422 ante un
 * criterio clinico, la exclusion por opt-out registrada con su motivo, la idempotencia del canje y
 * la de la acumulacion, y el aislamiento entre tenants.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class MarketingFlowContainerTest extends AbstractMySqlContainerTest {

    @Autowired
    private SegmentService segments;
    @Autowired
    private CampaignService campaigns;
    @Autowired
    private PromotionService promotions;
    @Autowired
    private LoyaltyService loyalty;
    @Autowired
    private PatientActivityService activities;
    @Autowired
    private ReferenceDataService referenceData;
    @Autowired
    private EventIngestionService events;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private Clock clock;
    @Autowired
    private DataSource dataSource;

    private RequestContext context;
    private LocalDate today;

    /**
     * Cada prueba parte de una base vacia.
     *
     * <p>El contenedor MySQL es un singleton compartido por toda la JVM y estas pruebas <b>no</b> se
     * ejecutan dentro de una transaccion que se deshaga al terminar: hacerlo escondería justo lo que
     * hay que comprobar -- que el outbox recibio el evento al confirmar, que la restriccion UNIQUE
     * del canje actua en el motor. Como el estado se confirma de verdad, se limpia de verdad.
     */
    @BeforeEach
    void setUp() {
        context = TestFixtures.administrator();
        today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        truncateAll();
        referenceData.applyOrganization(TestFixtures.ORGANIZATION_ID, "CLINICA DEMO", true, 0L);
    }

    /** Vacia las tablas en un orden cualquiera, con las FK desactivadas durante la limpieza. */
    private void truncateAll() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 0").update();
        for (String table : List.of("campaign_recipient", "promotion_redemption", "loyalty_points_movement",
                "campaign", "advertisement", "promotion", "audience_segment", "loyalty_program",
                "patient_activity", "ref_service_offering", "ref_patient", "ref_organization",
                "outbox_event", "processed_event")) {
            jdbc.sql("TRUNCATE TABLE " + table).update();
        }
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 1").update();
    }

    // ------------------------------------------------------------------------------- segmentacion

    @Test
    @DisplayName("un criterio clinico se rechaza con 422 y el segmento no llega a existir")
    void rejectsClinicalCriteria() {
        CreateSegmentRequest request = new CreateSegmentRequest("Asmaticos", "Intento prohibido",
                Map.of("diagnosticoCie10", "J45"));

        assertThatThrownBy(() -> segments.create(context, request))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CLINICAL_DATA_NOT_ALLOWED);

        assertThat(segments.list(context, null, PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("un segmento demografico se evalua sobre la demografia replicada")
    void evaluatesDemographicSegment() {
        replicatePatient(patient(1), "MUJER DEMO 1", today.minusYears(40), Sex.F, "La Paz");
        replicatePatient(patient(2), "HOMBRE DEMO 2", today.minusYears(25), Sex.M, "La Paz");
        replicatePatient(patient(3), "MUJER DEMO 3", today.minusYears(70), Sex.F, "Sucre");

        SegmentResponse segment = segments.create(context, new CreateSegmentRequest(
                "Mujeres pacenas de 30 a 60",
                "Segmentacion demografica pura",
                Map.of("sex", List.of("F"), "cities", List.of("La Paz"), "minAge", 30, "maxAge", 60)));

        assertThat(segment.usesClinicalData()).isFalse();
        assertThat(segment.estimatedSize()).isEqualTo(1L);

        SegmentPreviewResponse preview = segments.evaluate(context, segment.id());
        assertThat(preview.matched()).isEqualTo(1L);
        assertThat(preview.sample()).singleElement()
                .satisfies(member -> assertThat(member.fullName()).isEqualTo("MUJER DEMO 1"));
    }

    @Test
    @DisplayName("el criterio de reactivacion usa la actividad comercial, no el motivo de la visita")
    void evaluatesReactivationSegment() {
        UUID recent = patient(10);
        UUID lapsed = patient(11);
        replicatePatient(recent, "PACIENTE RECIENTE", today.minusYears(35), Sex.M, "La Paz");
        replicatePatient(lapsed, "PACIENTE PERDIDO", today.minusYears(35), Sex.M, "La Paz");

        // La actividad se construye por el mismo camino que en produccion -- un evento de agenda --
        // y no tocando la entidad a mano: asi la prueba ejercita la proyeccion real.
        events.apply(visitEvent(recent, today.minusDays(10)));
        events.apply(visitEvent(lapsed, today.minusMonths(18)));

        SegmentPreviewResponse preview = segments.previewRaw(context,
                Map.of("lastVisitBefore", today.minusMonths(12).toString()));

        assertThat(preview.matched()).isEqualTo(1L);
        assertThat(preview.sample()).singleElement()
                .satisfies(member -> assertThat(member.patientId()).isEqualTo(lapsed));
    }

    // ----------------------------------------------------------------------------------- campana

    @Test
    @DisplayName("la campana materializa su audiencia, deja constancia del opt-out y publica el evento")
    void materializesLaunchesAndPublishes() {
        UUID reachable = patient(20);
        UUID optedOut = patient(21);
        replicatePatient(reachable, "PACIENTE ALCANZABLE", today.minusYears(30), Sex.F, "La Paz");
        replicatePatient(optedOut, "PACIENTE EXCLUIDO", today.minusYears(31), Sex.F, "La Paz");
        activities.setOptOut(context, optedOut, true);

        SegmentResponse segment = segments.create(context, new CreateSegmentRequest("Pacenas adultas", null,
                Map.of("cities", List.of("La Paz"), "minAge", 18)));

        CampaignResponse campaign = campaigns.create(context, new CreateCampaignRequest(
                "Chequeo preventivo", CampaignType.PREVENTIVE, "Promover el control anual",
                CampaignChannel.WHATSAPP, null, segment.id(), null,
                OffsetDateTime.now(clock), null, new BigDecimal("500.00")));
        assertThat(campaign.status()).isEqualTo(CampaignStatus.DRAFT);

        MaterializeAudienceResponse audience = campaigns.materializeAudience(context, campaign.id());
        assertThat(audience.matched()).isEqualTo(2L);
        assertThat(audience.reachable()).isEqualTo(1L);
        assertThat(audience.excludedOptOut()).isEqualTo(1L);

        // El excluido queda registrado con su motivo, no simplemente omitido: es la evidencia de que
        // la exclusion se evaluo.
        assertThat(campaigns.listRecipients(context, campaign.id(), RecipientStatus.EXCLUDED_OPT_OUT,
                        PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(recipient -> {
                    assertThat(recipient.patientId()).isEqualTo(optedOut);
                    assertThat(recipient.exclusionReason()).contains("no recibir comunicaciones");
                });

        CampaignResponse launched = campaigns.launch(context, campaign.id());
        assertThat(launched.status()).isEqualTo(CampaignStatus.RUNNING);
        assertThat(launched.totalRecipients()).isEqualTo(1L);

        // El evento se escribio en la misma transaccion que el cambio de estado.
        assertThat(outbox.findByAggregatePublicIdOrderByIdAsc(campaign.id()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(MarketingEventPublisher.CAMPAIGN_EVENT_TYPE);
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    // MySQL normaliza el JSON al guardarlo -- reordena las claves y espacia los
                    // dos puntos --, asi que se comparan los valores y no el texto literal.
                    assertThat(event.getPayload())
                            .contains("\"LANZADA\"")
                            .contains("/internal/v1/campanas/" + campaign.id() + "/destinatarios")
                            .contains("\"totalRecipients\": 1");
                });
    }

    @Test
    @DisplayName("lanzar sin audiencia materializada se rechaza con 422")
    void refusesToLaunchWithoutAudience() {
        SegmentResponse segment = segments.create(context, new CreateSegmentRequest("Vacio", null,
                Map.of("minAge", 120)));
        CampaignResponse campaign = campaigns.create(context, new CreateCampaignRequest(
                "Campana sin audiencia", CampaignType.PROMOTION, null, CampaignChannel.SMS, null,
                segment.id(), null, OffsetDateTime.now(clock), null, null));

        assertThatThrownBy(() -> campaigns.launch(context, campaign.id()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CAMPAIGN_WITHOUT_AUDIENCE);
    }

    @Test
    @DisplayName("una campana de otro tenant responde 404 y no 403: no se revela su existencia")
    void hidesCampaignsFromOtherTenants() {
        SegmentResponse segment = segments.create(context, new CreateSegmentRequest("Propio", null,
                Map.of("minAge", 18)));
        CampaignResponse campaign = campaigns.create(context, new CreateCampaignRequest(
                "Campana del tenant A", CampaignType.PROMOTION, null, null, null, segment.id(), null,
                OffsetDateTime.now(clock), null, null));

        referenceData.applyOrganization(TestFixtures.OTHER_ORGANIZATION_ID, "OTRA CLINICA", true, 0L);
        assertThatThrownBy(() -> campaigns.get(TestFixtures.otherAdministrator(), campaign.id()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CAMPAIGN_NOT_FOUND);
    }

    // ------------------------------------------------------------------------------------ cupones

    @Test
    @DisplayName("el cupon se valida sin consumirse y se canja una sola vez por factura")
    void validatesAndRedeemsCouponIdempotently() {
        UUID patientId = patient(30);
        replicatePatient(patientId, "PACIENTE CUPON", today.minusYears(45), Sex.M, "Sucre");
        promotions.create(context, new CreatePromotionRequest("VERANO25", "Descuento de verano", null,
                DiscountType.PERCENTAGE, new BigDecimal("25"), null, 2L, 1,
                today.minusDays(1), today.plusDays(30)));

        // Validar no gasta cupo: es lo que permite que la caja muestre el descuento antes de cobrar.
        CouponValidationResponse validation = promotions.validate(context,
                new ValidateCouponRequest("verano25", patientId, null, new BigDecimal("200.00")));
        assertThat(validation.valid()).isTrue();
        assertThat(validation.discountAmountBob()).isEqualByComparingTo("50.00");
        assertThat(validation.netAmountBob()).isEqualByComparingTo("150.00");
        assertThat(promotions.get(context, validation.promotionId()).currentRedemptions()).isZero();

        UUID invoiceId = UUID.randomUUID();
        CouponRedemptionResponse first = promotions.redeem(context, new RedeemCouponRequest("VERANO25",
                patientId, null, new BigDecimal("200.00"), invoiceId, null));
        assertThat(first.alreadyRedeemed()).isFalse();
        assertThat(first.discountAmountBob()).isEqualByComparingTo("50.00");

        // Reintentar con la misma factura devuelve el canje existente en vez de gastar otro uso.
        CouponRedemptionResponse retry = promotions.redeem(context, new RedeemCouponRequest("VERANO25",
                patientId, null, new BigDecimal("200.00"), invoiceId, null));
        assertThat(retry.alreadyRedeemed()).isTrue();
        assertThat(retry.redemptionId()).isEqualTo(first.redemptionId());
        assertThat(promotions.get(context, first.promotionId()).currentRedemptions()).isEqualTo(1L);
    }

    @Test
    @DisplayName("el limite por paciente se aplica: el segundo intento del mismo paciente falla")
    void enforcesPerPatientLimit() {
        UUID patientId = patient(31);
        replicatePatient(patientId, "PACIENTE REPETIDOR", today.minusYears(50), Sex.F, "Sucre");
        promotions.create(context, new CreatePromotionRequest("UNICO", "Un solo uso por paciente", null,
                DiscountType.FIXED_AMOUNT, new BigDecimal("30.00"), null, null, 1,
                today.minusDays(1), today.plusDays(10)));

        promotions.redeem(context, new RedeemCouponRequest("UNICO", patientId, null,
                new BigDecimal("100.00"), UUID.randomUUID(), null));

        assertThatThrownBy(() -> promotions.redeem(context, new RedeemCouponRequest("UNICO", patientId, null,
                new BigDecimal("100.00"), UUID.randomUUID(), null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.PROMOTION_NOT_APPLICABLE);
    }

    @Test
    @DisplayName("un cupon vencido responde valid=false con su motivo, no un error HTTP")
    void expiredCouponIsAnAnswerNotAnError() {
        UUID patientId = patient(32);
        replicatePatient(patientId, "PACIENTE TARDIO", today.minusYears(33), Sex.M, "La Paz");
        promotions.create(context, new CreatePromotionRequest("CADUCO", "Cupon vencido", null,
                DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, 1,
                today.minusDays(30), today.minusDays(1)));

        CouponValidationResponse validation = promotions.validate(context,
                new ValidateCouponRequest("CADUCO", patientId, null, new BigDecimal("100.00")));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("vigencia");
        assertThat(validation.netAmountBob()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("el descuento nunca supera la base: el importe neto no queda negativo")
    void discountNeverExceedsTheBase() {
        UUID patientId = patient(33);
        replicatePatient(patientId, "PACIENTE BARATO", today.minusYears(28), Sex.F, "La Paz");
        promotions.create(context, new CreatePromotionRequest("GRANDE", "Descuento mayor que la base", null,
                DiscountType.FIXED_AMOUNT, new BigDecimal("500.00"), null, null, 1,
                today.minusDays(1), today.plusDays(10)));

        CouponValidationResponse validation = promotions.validate(context,
                new ValidateCouponRequest("GRANDE", patientId, null, new BigDecimal("80.00")));

        assertThat(validation.discountAmountBob()).isEqualByComparingTo("80.00");
        assertThat(validation.netAmountBob()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------------------- fidelidad

    @Test
    @DisplayName("una factura acumula puntos una sola vez aunque el evento llegue dos veces")
    void accruesPointsOncePerEvent() {
        UUID patientId = patient(40);
        replicatePatient(patientId, "PACIENTE FIEL", today.minusYears(38), Sex.M, "La Paz");
        loyalty.createProgram(context, new CreateLoyaltyProgramRequest("PUNTOS DEMO", BigDecimal.ONE,
                new BigDecimal("0.0100"), 12));

        UUID eventId = UUID.randomUUID();
        IncomingEventRequest invoiceEvent = invoiceEvent(eventId, patientId, "250.00");

        EventAcceptedResponse first = events.apply(invoiceEvent);
        assertThat(first.duplicate()).isFalse();

        EventAcceptedResponse repeat = events.apply(invoiceEvent);
        assertThat(repeat.duplicate()).isTrue();

        LoyaltyBalanceResponse balance = loyalty.balance(context, patientId, null);
        assertThat(balance.balancePoints()).isEqualTo(250);
        assertThat(balance.balanceValueBob()).isEqualByComparingTo("2.50");

        // El gasto tampoco se duplico: es lo que protege la audiencia de un segmento por gasto.
        assertThat(activities.get(context, patientId).totalBilledBob()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("no se puede canjear mas puntos de los que hay")
    void refusesToRedeemMorePointsThanAvailable() {
        UUID patientId = patient(41);
        replicatePatient(patientId, "PACIENTE SIN PUNTOS", today.minusYears(29), Sex.F, "Sucre");
        loyalty.createProgram(context, new CreateLoyaltyProgramRequest("PUNTOS ESCASOS", BigDecimal.ONE,
                new BigDecimal("0.0100"), null));

        LoyaltyMovementRequest request = new LoyaltyMovementRequest(patientId,
                LoyaltyMovementType.REDEMPTION, 100, "Canje sin saldo");

        assertThatThrownBy(() -> loyalty.registerMovement(context, request, null))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.LOYALTY_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("la acumulacion no se admite por la ruta manual: solo llega por evento")
    void refusesManualAccrual() {
        UUID patientId = patient(42);
        replicatePatient(patientId, "PACIENTE MANUAL", today.minusYears(44), Sex.M, "La Paz");
        loyalty.createProgram(context, new CreateLoyaltyProgramRequest("PUNTOS MANUALES", BigDecimal.ONE,
                new BigDecimal("0.0100"), null));

        LoyaltyMovementRequest request = new LoyaltyMovementRequest(patientId,
                LoyaltyMovementType.ACCRUAL, 500, "Intento de acumular a mano");

        assertThatThrownBy(() -> loyalty.registerMovement(context, request, null))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    // ----------------------------------------------------------------------------------- eventos

    @Test
    @DisplayName("del encuentro firmado solo entra la fecha de control, jamas el motivo")
    void signedEncounterBringsOnlyTheControlDate() {
        UUID patientId = patient(50);
        replicatePatient(patientId, "PACIENTE CON CONTROL", today.minusYears(60), Sex.F, "La Paz");

        events.apply(new IncomingEventRequest(UUID.randomUUID(), "historial.encuentro.firmado",
                OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("patientId", patientId.toString(),
                        "suggestedNextVisitOn", today.plusMonths(3).toString())));

        assertThat(activities.get(context, patientId).suggestedNextVisitOn())
                .isEqualTo(today.plusMonths(3));

        // Con el motivo dentro, el evento entero se rechaza: no se proyecta a medias.
        assertThatThrownBy(() -> events.apply(new IncomingEventRequest(UUID.randomUUID(),
                "historial.encuentro.firmado", OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("patientId", patientId.toString(),
                        "suggestedNextVisitOn", today.plusMonths(6).toString(),
                        "diagnosticoCie10", "J45"))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("una cita atribuida a la campana cuenta como conversion una sola vez")
    void attributesConversionOnce() {
        UUID patientId = patient(51);
        replicatePatient(patientId, "PACIENTE CONVERTIDO", today.minusYears(32), Sex.M, "La Paz");

        SegmentResponse segment = segments.create(context, new CreateSegmentRequest("Convertibles", null,
                Map.of("minAge", 18)));
        CampaignResponse campaign = campaigns.create(context, new CreateCampaignRequest("Campana medible",
                CampaignType.REACTIVATION, null, CampaignChannel.EMAIL, null, segment.id(), null,
                OffsetDateTime.now(clock), null, null));
        campaigns.materializeAudience(context, campaign.id());
        campaigns.launch(context, campaign.id());

        UUID appointmentId = UUID.randomUUID();
        events.apply(appointmentEvent(patientId, campaign.id(), appointmentId));
        events.apply(appointmentEvent(patientId, campaign.id(), appointmentId));

        assertThat(campaigns.get(context, campaign.id()).totalConverted()).isEqualTo(1L);
        assertThat(campaigns.performance(context, campaign.id()).totalConverted()).isEqualTo(1L);
    }

    @Test
    @DisplayName("un evento que marketing no consume se rechaza con 422 y no se anota como aplicado")
    void refusesUnsupportedEvents() {
        IncomingEventRequest request = new IncomingEventRequest(UUID.randomUUID(),
                "historial.diagnostico.registrado", OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("patientId", patient(52).toString()));

        assertThatThrownBy(() -> events.apply(request))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.EVENT_NOT_SUPPORTED);
    }

    // -------------------------------------------------------- actividad comercial

    @Test
    @DisplayName("la actividad se lista aunque el paciente todavia no haya comprado ninguna prestacion")
    void listsActivityForPatientsWithoutPurchases() {
        // Regresion: la busqueda del nombre de la ultima prestacion se hacia contra un mapa
        // inmutable, y Map.of() lanza NullPointerException al consultarlo con una clave nula en vez
        // de devolver nulo. Un paciente recien conocido -- sin compras -- tiraba el listado entero.
        UUID patientId = patient(60);
        replicatePatient(patientId, "PACIENTE SIN COMPRAS", today.minusYears(41), Sex.F, "La Paz");

        Page<PatientActivityResponse> page = activities.list(context, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.patientId()).isEqualTo(patientId);
            assertThat(row.lastServiceOfferingName()).isNull();
            assertThat(row.totalBilledBob()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    @DisplayName("la actividad muestra el nombre de la ultima prestacion cuando si hubo compra")
    void listsActivityWithLastOfferingName() {
        UUID patientId = patient(61);
        UUID offeringId = UUID.fromString("00000000-0000-4000-8000-000000000501");
        replicatePatient(patientId, "PACIENTE CON COMPRAS", today.minusYears(37), Sex.M, "Sucre");
        referenceData.applyServiceOffering(offeringId, TestFixtures.ORGANIZATION_ID,
                "Consulta general", new BigDecimal("150.00"), true, 0L);

        events.apply(new IncomingEventRequest(UUID.randomUUID(), "facturacion.factura.emitida",
                OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("invoiceId", UUID.randomUUID().toString(),
                        "patientId", patientId.toString(),
                        "totalAmountBob", "150.00",
                        "serviceOfferingId", offeringId.toString())));

        assertThat(activities.list(context, PageRequest.of(0, 20)).getContent())
                .singleElement()
                .satisfies(row -> assertThat(row.lastServiceOfferingName()).isEqualTo("Consulta general"));
    }

    // ------------------------------------------------------------------------------------ ayudas

    /** UUID de paciente estable y reconocible como dato de prueba. */
    private static UUID patient(int index) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-0000000009%02d", index));
    }

    private void replicatePatient(UUID patientId, String fullName, LocalDate birthDate, Sex sex, String city) {
        referenceData.applyPatient(patientId, fullName, birthDate, sex, city, "+59170000000",
                PatientStatus.ACTIVE, 0L);
        referenceData.requireActivity(TestFixtures.ORGANIZATION_ID, patientId);
    }

    private IncomingEventRequest invoiceEvent(UUID eventId, UUID patientId, String amount) {
        return new IncomingEventRequest(eventId, "facturacion.factura.emitida",
                OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("invoiceId", UUID.randomUUID().toString(),
                        "patientId", patientId.toString(),
                        "totalAmountBob", amount));
    }

    /** Cita atendida en la fecha indicada, sin campana asociada. */
    private IncomingEventRequest visitEvent(UUID patientId, LocalDate visitedOn) {
        return new IncomingEventRequest(UUID.randomUUID(), "agenda.cita.atendida",
                OffsetDateTime.now(clock), TestFixtures.ORGANIZATION_ID,
                Map.of("appointmentId", UUID.randomUUID().toString(),
                        "patientId", patientId.toString(),
                        "status", "ATTENDED",
                        "scheduledOn", visitedOn.toString()));
    }

    private IncomingEventRequest appointmentEvent(UUID patientId, UUID campaignId, UUID appointmentId) {
        return new IncomingEventRequest(UUID.randomUUID(), "agenda.cita.atendida",
                OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC), TestFixtures.ORGANIZATION_ID,
                Map.of("appointmentId", appointmentId.toString(),
                        "patientId", patientId.toString(),
                        "status", "ATTENDED",
                        "scheduledOn", today.toString(),
                        "campaignId", campaignId.toString()));
    }
}
