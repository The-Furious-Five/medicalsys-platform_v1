package com.medicalsys.marketing.service;

import com.medicalsys.marketing.client.PatientDemographics;
import com.medicalsys.marketing.client.PatientsClient;
import com.medicalsys.marketing.common.error.DependencyUnavailableException;
import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.domain.enums.CampaignChannel;
import com.medicalsys.marketing.domain.enums.CampaignType;
import com.medicalsys.marketing.domain.enums.DiscountType;
import com.medicalsys.marketing.domain.model.Advertisement;
import com.medicalsys.marketing.domain.model.AudienceSegment;
import com.medicalsys.marketing.domain.model.Campaign;
import com.medicalsys.marketing.domain.model.LoyaltyProgram;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.RefServiceOffering;
import com.medicalsys.marketing.domain.model.Promotion;
import com.medicalsys.marketing.privacy.SegmentCriteria;
import com.medicalsys.marketing.repository.AdvertisementRepository;
import com.medicalsys.marketing.repository.AudienceSegmentRepository;
import com.medicalsys.marketing.repository.CampaignRepository;
import com.medicalsys.marketing.repository.LoyaltyProgramRepository;
import com.medicalsys.marketing.repository.PatientActivityRepository;
import com.medicalsys.marketing.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra los datos minimos para que el servicio sea usable desde una base vacia.
 *
 * <p>Sin esto, un {@code docker compose up} deja un servicio correcto pero inutil: no hay tenant
 * replicado, no hay pacientes con actividad y por tanto ningun segmento devuelve a nadie. Con esto,
 * el recorrido de humo funciona sin ningun paso manual, que es lo que ms-facturacion resuelve con su
 * {@code demo-setup}.
 *
 * <p>Los pacientes sembrados son los del padron real de ms-pacientes, no una poblacion inventada.
 * La version anterior derivaba doce UUID de un patron propio ({@code ...-0000000007NN}) que no
 * existia en patients_db: con PATIENTS_MODE=LOCAL producia nombres como "PACIENTE DEMO 00000000",
 * y con REMOTE no encontraba a nadie y la siembra quedaba vacia. Usando los UUID que ms-pacientes
 * publica, la audiencia de una campana muestra a las mismas personas que la tabla de pacientes.
 *
 * <p>Ademas del programa y el cupon se siembran un segmento, dos campanas y dos anuncios: sin
 * ellos, cuatro de las siete pantallas del modulo abren vacias aunque el servicio funcione.
 *
 * <p>La siembra es idempotente: lo que ya existe no se toca.
 *
 * <p>En un entorno real se desactiva con {@code MARKETING_DEMO_SEED=false} y los datos llegan por
 * eventos, como corresponde.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.demo", name = "seed-on-startup", havingValue = "true",
        matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /**
     * Padron replicado: son los UUID que siembra ms-pacientes, en su mismo orden.
     *
     * <p>No se derivan de una plantilla porque no son de marketing: pertenecen a otro servicio y
     * marketing solo guarda una replica. Un patron generado volveria a producir la poblacion
     * fantasma que este seeder tenia antes.
     */
    private static final List<String> PATIENT_IDS = List.of(
            "10000000-0000-4000-8000-000000000001",
            "10000000-0000-4000-8000-000000000002",
            "10000000-0000-4000-8000-000000000003",
            "10000000-0000-4000-8000-000000000004",
            "10000000-0000-4000-8000-000000000005",
            "10000000-0000-4000-8000-000000000007",
            "10000000-0000-4000-8000-000000000009",
            "10000000-0000-4000-8000-00000000000a",
            "10000000-0000-4000-8000-00000000000b",
            "10000000-0000-4000-8000-00000000000c",
            "10000000-0000-4000-8000-00000000000d",
            "10000000-0000-4000-8000-00000000000e");

    private static final String SEGMENT_REACTIVATION = "Pacientes sin consulta hace mas de un ano";
    private static final String SEGMENT_ANNUAL_CHECKUP = "Adultos de 40 anos o mas para control anual";
    private static final String AD_PREVENTIVE = "Control anual de salud";
    private static final String COUPON_CODE = "DEMO10";

    /** Autor de la siembra: la cuenta de administracion del centro en ms-identidad. */
    private static final UUID DEMO_MARKETING_USER = UUID.fromString("00000000-0000-4000-8000-000000000901");

    /** Reparto de la actividad ficticia, para que los ejes de segmentacion tengan variedad. */
    private static final int VISITS_MODULUS = 5;
    private static final BigDecimal SPEND_STEP_BOB = new BigDecimal("120.00");

    private final MarketingProperties properties;
    private final ReferenceDataService referenceData;
    private final PatientsClient patientsClient;
    private final LoyaltyProgramRepository programs;
    private final PromotionRepository promotions;
    private final PatientActivityRepository activities;
    private final AudienceSegmentRepository segments;
    private final CampaignRepository campaigns;
    private final AdvertisementRepository advertisements;
    private final Clock clock;

    public DemoDataSeeder(MarketingProperties properties, ReferenceDataService referenceData,
            PatientsClient patientsClient, LoyaltyProgramRepository programs, PromotionRepository promotions,
            PatientActivityRepository activities, AudienceSegmentRepository segments,
            CampaignRepository campaigns, AdvertisementRepository advertisements, Clock clock) {
        this.properties = properties;
        this.referenceData = referenceData;
        this.patientsClient = patientsClient;
        this.programs = programs;
        this.promotions = promotions;
        this.activities = activities;
        this.segments = segments;
        this.campaigns = campaigns;
        this.advertisements = advertisements;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        MarketingProperties.Demo demo = properties.demo();
        UUID organizationId = demo.organizationId();
        log.warn("Se sembraran datos de demostracion en marketing_db (MARKETING_DEMO_SEED=true). "
                + "Desactivelo en cualquier entorno que no sea local.");

        try {
            referenceData.applyOrganization(organizationId, demo.organizationName(), true, 0L);
            seedLoyaltyProgram(organizationId, demo);
            seedCoupon(organizationId, demo);
            seedPatients(organizationId, demo);
            seedSegmentsAndCampaigns(organizationId);
            seedAdvertisements(organizationId);
        } catch (DependencyUnavailableException e) {
            // En PATIENTS_MODE=REMOTE la siembra consulta a ms-pacientes, que puede no haber
            // terminado de arrancar. Que falte la poblacion de demostracion no es motivo para
            // dejar el servicio caido: el resto del modulo funciona y la siembra se completa en
            // el siguiente arranque, porque es idempotente.
            log.warn("La siembra de demostracion quedo incompleta: {}. "
                    + "Reinicie ms-marketing cuando ms-pacientes responda.", e.getMessage());
        }
    }

    /**
     * Un segmento y dos campanas sobre el.
     *
     * <p>Las campanas quedan en estados distintos a proposito: una en borrador -sin audiencia
     * congelada- y otra ya lanzada con sus destinatarios. Con las dos en el mismo estado, ni el
     * tablero de campanas ni el panel de audiencia muestran la mitad de lo que saben hacer.
     */
    private void seedSegmentsAndCampaigns(UUID organizationId) {
        if (segments.existsByOrganizationPublicIdAndName(organizationId, SEGMENT_REACTIVATION)) {
            return;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        AudienceSegment reactivacion = segments.save(AudienceSegment.define(organizationId,
                SEGMENT_REACTIVATION,
                "Personas de 18 a 75 anos de La Paz y El Alto que no vuelven al centro hace mas de un ano.",
                new SegmentCriteria(18, 75, null, List.of("La Paz", "El Alto"),
                        today.minusMonths(12), null, 1, null, null, null, null, null, null),
                DEMO_MARKETING_USER));

        AudienceSegment control = segments.save(AudienceSegment.define(organizationId,
                SEGMENT_ANNUAL_CHECKUP,
                "Adultos de 40 anos o mas con al menos dos atenciones, para el control anual preventivo.",
                new SegmentCriteria(40, null, null, null, null, null, 2, null, null, null, null, null, null),
                DEMO_MARKETING_USER));

        Promotion cupon = promotions.findByOrganizationPublicIdAndCouponCode(organizationId, COUPON_CODE)
                .orElse(null);

        campaigns.save(Campaign.draft(organizationId,
                "Reactivacion de pacientes inactivos",
                CampaignType.REACTIVATION,
                "Recuperar a quienes no vuelven hace mas de un ano ofreciendo el descuento de bienvenida.",
                CampaignChannel.WHATSAPP, null, reactivacion, cupon,
                today.plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(33).atStartOfDay().toInstant(ZoneOffset.UTC),
                new BigDecimal("1200.00"), DEMO_MARKETING_USER));

        campaigns.save(Campaign.draft(organizationId,
                "Control anual preventivo",
                CampaignType.PREVENTIVE,
                "Recordar el chequeo anual a los pacientes de 40 anos o mas.",
                CampaignChannel.EMAIL, null, control, null,
                today.minusDays(10).atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(20).atStartOfDay().toInstant(ZoneOffset.UTC),
                new BigDecimal("800.00"), DEMO_MARKETING_USER));

        log.info("Dos segmentos y dos campanas de demostracion sembrados");
    }

    /**
     * Anuncios del portal del paciente.
     *
     * <p>Uno publicado con metricas y otro en borrador: la pantalla de anuncios ordena por estado
     * y por prioridad, y con un solo anuncio no hay orden que ver.
     */
    private void seedAdvertisements(UUID organizationId) {
        if (advertisements.existsByOrganizationPublicIdAndTitle(organizationId, AD_PREVENTIVE)) {
            return;
        }
        Instant now = clock.instant();

        // La prestacion anunciada tiene FK contra ref_service_offering: hay que replicarla desde
        // ms-tenencia antes de referenciarla. Si esa prestacion no existe alla, el anuncio se
        // siembra sin ella en vez de romper el arranque; el enlace es informativo.
        UUID perfilLaboratorio = hydratedOffering(organizationId, "00000000-0000-4000-8000-00000000050a");
        UUID vacunaInfluenza = hydratedOffering(organizationId, "00000000-0000-4000-8000-00000000050d");

        Advertisement preventivo = Advertisement.draft(organizationId, AD_PREVENTIVE,
                "Agende su control anual de salud. Incluye consulta de medicina general, hemograma "
                        + "y glucemia en ayunas por Bs 180.",
                null, "https://sangabriel.clinica.invalid/control-anual",
                perfilLaboratorio,
                AdvertisementPlacement.PATIENT_PORTAL, 1, null,
                now.minus(20, ChronoUnit.DAYS), now.plus(70, ChronoUnit.DAYS), DEMO_MARKETING_USER);
        preventivo.publish();
        advertisements.save(preventivo);

        advertisements.save(Advertisement.draft(organizationId,
                "Campana de vacunacion contra la influenza",
                "Vacunacion antigripal estacional en las tres sedes, sin cita previa, de 08:00 a 12:00.",
                null, null, vacunaInfluenza,
                AdvertisementPlacement.WAITING_ROOM, 3, null,
                now.plus(5, ChronoUnit.DAYS), now.plus(65, ChronoUnit.DAYS), DEMO_MARKETING_USER));

        log.info("Dos anuncios de demostracion sembrados");
    }

    /** Replica la prestacion desde ms-tenencia; devuelve null si alla no existe. */
    private UUID hydratedOffering(UUID organizationId, String serviceOfferingId) {
        return referenceData.findOrHydrateServiceOffering(organizationId, UUID.fromString(serviceOfferingId))
                .map(RefServiceOffering::getPublicId)
                .orElse(null);
    }

    private void seedLoyaltyProgram(UUID organizationId, MarketingProperties.Demo demo) {
        if (programs.existsByOrganizationPublicIdAndName(organizationId, demo.loyaltyProgramName())) {
            return;
        }
        // 1 punto por boliviano, 1 centavo por punto: un 1 % de retorno, facil de verificar a mano.
        programs.save(LoyaltyProgram.create(organizationId, demo.loyaltyProgramName(),
                BigDecimal.ONE, new BigDecimal("0.0100"), 12));
        log.info("Programa de fidelidad de demostracion '{}' sembrado", demo.loyaltyProgramName());
    }

    private void seedCoupon(UUID organizationId, MarketingProperties.Demo demo) {
        if (promotions.existsByOrganizationPublicIdAndCouponCode(organizationId, demo.couponCode())) {
            return;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        promotions.save(Promotion.create(organizationId, demo.couponCode(),
                "Descuento de demostracion del 10 %",
                "Cupon ficticio del entorno local. No tiene validez comercial.",
                DiscountType.PERCENTAGE, BigDecimal.TEN, null, 100L, 1,
                today.minusDays(1), today.plusYears(1), null));
        log.info("Cupon de demostracion '{}' sembrado", demo.couponCode());
    }

    /**
     * Siembra pacientes con actividad repartida.
     *
     * <p>El reparto no es decorativo: sin variedad en visitas y gasto, cualquier criterio devolveria
     * o todos o ninguno, y la vista previa de un segmento no ensenaria nada.
     */
    private void seedPatients(UUID organizationId, MarketingProperties.Demo demo) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int seeded = 0;

        int limit = Math.min(demo.patients(), PATIENT_IDS.size());
        for (int index = 1; index <= limit; index++) {
            UUID patientId = UUID.fromString(PATIENT_IDS.get(index - 1));
            Optional<PatientDemographics> demographics = patientsClient.demographics(organizationId, patientId);
            if (demographics.isEmpty()) {
                continue;
            }
            PatientDemographics person = demographics.get();
            referenceData.applyPatient(patientId, person.fullName(), person.birthDate(), person.sex(),
                    person.city(), person.phoneE164(), person.status(), 0L);

            if (activities.findByIdPatientPublicIdAndIdOrganizationPublicId(patientId, organizationId)
                    .isPresent()) {
                continue;
            }
            PatientActivity activity = referenceData.requireActivity(organizationId, patientId);

            // Uno de cada cinco no ha vuelto en mas de un ano: es la poblacion de reactivacion.
            int visits = 1 + index % VISITS_MODULUS;
            LocalDate lastVisit = index % VISITS_MODULUS == 0
                    ? today.minusMonths(14)
                    : today.minusDays(15L * index);
            for (int visit = 0; visit < visits; visit++) {
                activity.registerAttendedAppointment(lastVisit.minusMonths(visit));
            }
            if (index % 4 == 0) {
                activity.registerNoShow();
            }
            activity.registerBilledAmount(SPEND_STEP_BOB.multiply(BigDecimal.valueOf(visits)), null);
            if (index % 3 == 0) {
                activity.setSuggestedNextVisit(today.plusMonths(1));
            }
            seeded++;
        }
        if (seeded > 0) {
            log.info("{} paciente(s) de demostracion sembrados con su actividad comercial", seeded);
        }
    }
}
