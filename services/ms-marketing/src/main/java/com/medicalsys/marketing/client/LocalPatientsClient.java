package com.medicalsys.marketing.client;

import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deriva la demografia con datos ficticios deterministas, sin salir del proceso.
 *
 * <p>Existe para que la rebanada vertical funcione mientras ms-pacientes no publique su ruta interna
 * en el entorno de trabajo. Los valores se derivan del propio UUID, de modo que el mismo paciente
 * produce siempre la misma demografia y ninguna prueba depende de un dato inventado a mano.
 *
 * <p>Los nombres son evidentemente ficticios ("PACIENTE DEMO ..."): no imitan a una persona real. Al
 * pasar a {@code PATIENTS_MODE=REMOTE} este componente deja de registrarse.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.patients", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalPatientsClient implements PatientsClient {

    private static final Logger log = LoggerFactory.getLogger(LocalPatientsClient.class);

    /** Ciudades del entorno local; el eje de ciudad tiene que tener variedad para poder segmentar. */
    private static final List<String> CITIES = List.of("La Paz", "Cochabamba", "Santa Cruz", "Sucre");

    private static final List<Sex> SEXES = List.of(Sex.M, Sex.F, Sex.INTERSEX, Sex.UNDECLARED);

    /** Rango de edades ficticias: de 18 a 77 anos. */
    private static final int MIN_AGE = 18;
    private static final int AGE_SPAN = 60;

    public LocalPatientsClient() {
        log.warn("La demografia se resuelve con datos ficticios locales (PATIENTS_MODE=LOCAL). "
                + "Para consultar ms-pacientes use PATIENTS_MODE=REMOTE.");
    }

    @Override
    public Optional<PatientDemographics> demographics(UUID organizationId, UUID patientId) {
        long seed = Math.abs(patientId.getLeastSignificantBits());
        LocalDate birthDate = LocalDate.now()
                .minusYears(MIN_AGE + seed % AGE_SPAN)
                .minusDays(seed % 365);
        return Optional.of(new PatientDemographics(
                patientId,
                legalName(patientId),
                birthDate,
                SEXES.get((int) (seed % SEXES.size())),
                CITIES.get((int) (seed % CITIES.size())),
                "+591" + String.format("%08d", seed % 100_000_000L),
                PatientStatus.ACTIVE));
    }

    /** Nombre ficticio estable y reconocible como demostracion. */
    private String legalName(UUID patientId) {
        return "PACIENTE DEMO " + patientId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
