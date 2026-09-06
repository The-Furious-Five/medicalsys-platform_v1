package com.medicalsys.marketing.service;

import com.medicalsys.marketing.client.PatientDemographics;
import com.medicalsys.marketing.client.PatientsClient;
import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.domain.enums.DiscountType;
import com.medicalsys.marketing.domain.model.LoyaltyProgram;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.Promotion;
import com.medicalsys.marketing.repository.LoyaltyProgramRepository;
import com.medicalsys.marketing.repository.PatientActivityRepository;
import com.medicalsys.marketing.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * <p>Los pacientes sembrados son deterministas y evidentemente ficticios ("PACIENTE DEMO ..."): se
 * derivan de UUID fijos, de modo que dos arranques producen la misma poblacion y la demostracion es
 * reproducible. La siembra es idempotente: lo que ya existe no se toca.
 *
 * <p>En un entorno real se desactiva con {@code MARKETING_DEMO_SEED=false} y los datos llegan por
 * eventos, como corresponde.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.demo", name = "seed-on-startup", havingValue = "true",
        matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Prefijo de los UUID sembrados: reconocible a simple vista como dato de demostracion. */
    private static final String PATIENT_UUID_TEMPLATE = "00000000-0000-4000-8000-0000000007%02d";

    /** Reparto de la actividad ficticia, para que los ejes de segmentacion tengan variedad. */
    private static final int VISITS_MODULUS = 5;
    private static final BigDecimal SPEND_STEP_BOB = new BigDecimal("120.00");

    private final MarketingProperties properties;
    private final ReferenceDataService referenceData;
    private final PatientsClient patientsClient;
    private final LoyaltyProgramRepository programs;
    private final PromotionRepository promotions;
    private final PatientActivityRepository activities;
    private final Clock clock;

    public DemoDataSeeder(MarketingProperties properties, ReferenceDataService referenceData,
            PatientsClient patientsClient, LoyaltyProgramRepository programs, PromotionRepository promotions,
            PatientActivityRepository activities, Clock clock) {
        this.properties = properties;
        this.referenceData = referenceData;
        this.patientsClient = patientsClient;
        this.programs = programs;
        this.promotions = promotions;
        this.activities = activities;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        MarketingProperties.Demo demo = properties.demo();
        UUID organizationId = demo.organizationId();
        log.warn("Se sembraran datos de demostracion en marketing_db (MARKETING_DEMO_SEED=true). "
                + "Desactivelo en cualquier entorno que no sea local.");

        referenceData.applyOrganization(organizationId, demo.organizationName(), true, 0L);
        seedLoyaltyProgram(organizationId, demo);
        seedCoupon(organizationId, demo);
        seedPatients(organizationId, demo);
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

        for (int index = 1; index <= demo.patients(); index++) {
            UUID patientId = UUID.fromString(String.format(PATIENT_UUID_TEMPLATE, index));
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
