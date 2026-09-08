package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.domain.enums.AdministrationRoute;
import com.medicalsys.historial.domain.enums.AllergySeverity;
import com.medicalsys.historial.domain.enums.AllergyType;
import com.medicalsys.historial.domain.enums.BackgroundType;
import com.medicalsys.historial.domain.enums.DiagnosisType;
import com.medicalsys.historial.domain.enums.EncounterType;
import com.medicalsys.historial.domain.enums.Laterality;
import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyPriority;
import com.medicalsys.historial.domain.enums.StudyResultStatus;
import com.medicalsys.historial.domain.model.Allergy;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.Diagnosis;
import com.medicalsys.historial.domain.model.MedicalBackground;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.domain.model.Prescription;
import com.medicalsys.historial.domain.model.StudyOrder;
import com.medicalsys.historial.domain.model.StudyResult;
import com.medicalsys.historial.domain.model.VitalSign;
import com.medicalsys.historial.repository.AllergyRepository;
import com.medicalsys.historial.repository.ClinicalEncounterRepository;
import com.medicalsys.historial.repository.DiagnosisRepository;
import com.medicalsys.historial.repository.MedicalBackgroundRepository;
import com.medicalsys.historial.repository.MedicalRecordRepository;
import com.medicalsys.historial.repository.PrescriptionRepository;
import com.medicalsys.historial.repository.StudyOrderRepository;
import com.medicalsys.historial.repository.VitalSignRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expedientes de demostracion del entorno local.
 *
 * <p>Por que en Java y no en una migracion SQL: la narrativa clinica se persiste cifrada y un
 * script no puede producir ese ciphertext. La migracion repetible que existia sembraba un
 * expediente sin ninguna nota -y ademas apuntaba a un paciente que la semilla de ms-pacientes
 * nunca creo-, de modo que las pantallas de historial salian vacias. Aqui se usa el mismo
 * {@link NarrativeCipher} que emplea el servicio, asi que el texto se descifra igual que el que
 * escribe un medico y sigue siendo valido si cambia la clave o si se activa CRYPTO_MODE=REMOTE.
 *
 * <p>Los identificadores de paciente y de profesional son los que siembran ms-pacientes y
 * ms-profesionales. Son bases separadas sin claves foraneas entre si: el UUID acordado es lo
 * unico que hace que la cabecera del expediente muestre a una persona que existe.
 *
 * <p>Idempotencia por presencia: si el expediente del primer paciente ya esta, no se siembra
 * nada. No puede ser "borrar y reinsertar" porque un trigger de la base impide borrar un
 * encuentro clinico, que es justamente la garantia que el modulo quiere demostrar.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.demo", name = "seed-on-startup",
        havingValue = "true", matchIfMissing = true)
public class DemoMedicalRecordSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoMedicalRecordSeeder.class);

    private static final UUID ORGANIZATION = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID BRANCH_SOPOCACHI = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID BRANCH_MIRAFLORES = UUID.fromString("00000000-0000-4000-8000-0000000000a3");

    /** Pacientes sembrados por ms-pacientes. */
    private static final UUID P_MARIA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID P_JUAN = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID P_GREGORIO = UUID.fromString("10000000-0000-4000-8000-000000000005");
    private static final UUID P_MATEO = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID P_FREDDY = UUID.fromString("10000000-0000-4000-8000-000000000009");

    /** Profesionales sembrados por ms-profesionales. */
    private static final UUID DR_SALAZAR = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID DRA_NUNEZ = UUID.fromString("50000000-0000-4000-8000-000000000002");
    private static final UUID DR_ARISPE = UUID.fromString("50000000-0000-4000-8000-000000000004");

    private final MedicalRecordRepository records;
    private final ClinicalEncounterRepository encounters;
    private final AllergyRepository allergies;
    private final MedicalBackgroundRepository backgrounds;
    private final VitalSignRepository vitals;
    private final DiagnosisRepository diagnoses;
    private final PrescriptionRepository prescriptions;
    private final StudyOrderRepository studyOrders;
    private final NarrativeCipher cipher;
    private final ContentHasher hasher;

    public DemoMedicalRecordSeeder(MedicalRecordRepository records, ClinicalEncounterRepository encounters,
            AllergyRepository allergies, MedicalBackgroundRepository backgrounds, VitalSignRepository vitals,
            DiagnosisRepository diagnoses, PrescriptionRepository prescriptions, StudyOrderRepository studyOrders,
            NarrativeCipher cipher, ContentHasher hasher) {
        this.records = records;
        this.encounters = encounters;
        this.allergies = allergies;
        this.backgrounds = backgrounds;
        this.vitals = vitals;
        this.diagnoses = diagnoses;
        this.prescriptions = prescriptions;
        this.studyOrders = studyOrders;
        this.cipher = cipher;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (records.findByOrganizationPublicIdAndPatientPublicId(ORGANIZATION, P_MARIA).isPresent()) {
            return;
        }
        NarrativeCipher.CipherSession session = cipher.open(ORGANIZATION);
        UUID key = session.keyPublicId();
        Instant now = Instant.now();

        seedMaria(session, key, now);
        seedJuanCarlos(session, key, now);
        seedGregorio(session, key, now);
        seedMateo(session, key, now);
        seedFreddy(session, key, now);

        log.info("Expedientes de demostracion sembrados para cinco pacientes del entorno local");
    }

    /**
     * Control de salud del adulto: un encuentro firmado con signos vitales, diagnostico y una
     * orden de laboratorio ya informada, mas un encuentro en borrador para poder demostrar la
     * edicion y la firma en vivo.
     */
    private void seedMaria(NarrativeCipher.CipherSession session, UUID key, Instant now) {
        MedicalRecord record = records.save(MedicalRecord.open(
                ORGANIZATION, P_MARIA, "EXP-000001", BRANCH_SOPOCACHI, key));

        allergies.save(Allergy.record(record, AllergyType.MEDICATION, "Penicilina", "AMOXICILINA_500",
                "Urticaria generalizada a los 30 minutos de la primera dosis", AllergySeverity.MODERATE,
                DR_SALAZAR));
        backgrounds.save(MedicalBackground.record(record, BackgroundType.FAMILY,
                "Madre con diabetes mellitus tipo 2 diagnosticada a los 52 anos",
                session.encrypt("Madre en tratamiento con metformina desde 2018. Abuela materna fallecida "
                        + "por complicaciones de diabetes."),
                "E11.9", LocalDate.of(2018, 3, 1), DR_SALAZAR));
        backgrounds.save(MedicalBackground.record(record, BackgroundType.SURGICAL,
                "Cesarea segmentaria",
                session.encrypt("Cesarea sin complicaciones en 2017, anestesia raquidea. Sin secuelas."),
                null, LocalDate.of(2017, 9, 8), DR_SALAZAR));

        Instant startsAt = now.minus(42, ChronoUnit.DAYS);
        ClinicalEncounter firmado = ClinicalEncounter.open(record, null, DR_SALAZAR, BRANCH_SOPOCACHI,
                EncounterType.INITIAL_CONSULTATION, startsAt, key);
        firmado.replaceNarrative(
                session.encrypt("Control anual de salud. Paciente sin sintomas al momento de la consulta."),
                session.encrypt("Refiere cansancio matutino desde hace dos meses y sed aumentada. Antecedente "
                        + "materno de diabetes. No fuma, consumo de alcohol social."),
                session.encrypt("Buen estado general, consciente y orientada. Mucosas humedas. Cardiopulmonar "
                        + "sin alteraciones. Abdomen blando, depresible, sin visceromegalias."),
                session.encrypt("Examen medico general de adulto. Se solicita perfil basico para descartar "
                        + "alteracion del metabolismo de la glucosa dado el antecedente familiar."),
                session.encrypt("1. Hemograma, glucemia en ayunas y examen general de orina.\n"
                        + "2. Control en dos semanas con resultados.\n"
                        + "3. Recomendaciones de dieta y actividad fisica de 150 minutos semanales."), key);
        firmado.updateSchedule(startsAt.plus(35, ChronoUnit.MINUTES),
                LocalDate.now().plusDays(14), EncounterType.INITIAL_CONSULTATION);
        firmado = encounters.save(firmado);

        vitals.save(VitalSign.builder(firmado, startsAt.plus(5, ChronoUnit.MINUTES))
                .pressure(118, 76).rates(72, 16)
                .temperature(new BigDecimal("36.5")).oxygenSaturation(95)
                .anthropometry(new BigDecimal("64.50"), new BigDecimal("162.0"), new BigDecimal("82.0"))
                .painScale(0).recordedBy(DR_SALAZAR).build());

        diagnoses.save(Diagnosis.record(firmado, "Z00.0", DiagnosisType.FINAL, true, false,
                Laterality.NOT_APPLICABLE,
                session.encrypt("Control sin hallazgos patologicos. Pendiente laboratorio.")));

        StudyOrder laboratorio = StudyOrder.request(firmado, "LAB_GLUCOSE", StudyPriority.ROUTINE,
                "Ayuno de 8 horas. Tomar muestra antes de las 09:00.", "Z00.0", DR_SALAZAR);
        laboratorio.addResult(StudyResult.report(laboratorio, "Glucemia en ayunas", new BigDecimal("108.0000"),
                null, "mg/dL", new BigDecimal("70.0000"), new BigDecimal("100.0000"),
                StudyResultStatus.FINAL, null, DR_SALAZAR, key));
        laboratorio.markReported();
        studyOrders.save(laboratorio);

        firmado.sign(hasher.hashEncounter(firmado), null, null);
        encounters.save(firmado);

        // Borrador abierto: es el encuentro sobre el que la demostracion escribe y firma.
        Instant hoy = now.minus(2, ChronoUnit.HOURS);
        ClinicalEncounter borrador = ClinicalEncounter.open(record, null, DR_SALAZAR, BRANCH_SOPOCACHI,
                EncounterType.FOLLOW_UP_CONSULTATION, hoy, key);
        borrador.replaceNarrative(
                session.encrypt("Control de resultados de laboratorio."), null, null, null, null, key);
        encounters.save(borrador);
    }

    /** Hipertension en seguimiento: cronica, con receta activa y varias mediciones. */
    private void seedJuanCarlos(NarrativeCipher.CipherSession session, UUID key, Instant now) {
        MedicalRecord record = records.save(MedicalRecord.open(
                ORGANIZATION, P_JUAN, "EXP-000002", BRANCH_SOPOCACHI, key));

        backgrounds.save(MedicalBackground.record(record, BackgroundType.PATHOLOGICAL,
                "Hipertension arterial esencial desde 2019",
                session.encrypt("Diagnosticada en control laboral. En tratamiento con enalapril 10 mg/dia "
                        + "desde entonces, con buena adherencia."),
                "I10", LocalDate.of(2019, 6, 12), DR_ARISPE));
        backgrounds.save(MedicalBackground.record(record, BackgroundType.HABIT,
                "Tabaquismo suspendido",
                session.encrypt("Fumaba 10 cigarrillos diarios durante 15 anos. Suspendio en 2021."),
                null, LocalDate.of(2021, 1, 4), DR_ARISPE));

        Instant startsAt = now.minus(21, ChronoUnit.DAYS);
        ClinicalEncounter encuentro = ClinicalEncounter.open(record, null, DR_ARISPE, BRANCH_MIRAFLORES,
                EncounterType.FOLLOW_UP_CONSULTATION, startsAt, key);
        encuentro.replaceNarrative(
                session.encrypt("Control trimestral de hipertension arterial."),
                session.encrypt("Refiere cefalea occipital ocasional al despertar, dos episodios en el ultimo "
                        + "mes. Cumple el tratamiento. Dieta con sal reducida de forma irregular."),
                session.encrypt("PA 148/92 mmHg en brazo derecho, repetida a los cinco minutos 145/90. Ruidos "
                        + "cardiacos ritmicos, sin soplos. Sin edema de miembros inferiores."),
                session.encrypt("Hipertension esencial con control subóptimo. Se ajusta tratamiento y se "
                        + "solicita electrocardiograma para valorar repercusion."),
                session.encrypt("1. Losartan 50 mg cada 12 horas, sustituye a enalapril.\n"
                        + "2. Electrocardiograma de 12 derivaciones.\n"
                        + "3. Registro domiciliario de presion, dos tomas diarias.\n"
                        + "4. Control en un mes."), key);
        encuentro.updateSchedule(startsAt.plus(40, ChronoUnit.MINUTES),
                LocalDate.now().plusDays(9), EncounterType.FOLLOW_UP_CONSULTATION);
        encuentro = encounters.save(encuentro);

        vitals.save(VitalSign.builder(encuentro, startsAt.plus(4, ChronoUnit.MINUTES))
                .pressure(148, 92).rates(78, 17)
                .temperature(new BigDecimal("36.3")).oxygenSaturation(94)
                .anthropometry(new BigDecimal("88.20"), new BigDecimal("174.0"), new BigDecimal("102.5"))
                .painScale(2).recordedBy(DR_ARISPE).build());

        diagnoses.save(Diagnosis.record(encuentro, "I10", DiagnosisType.FINAL, true, true,
                Laterality.NOT_APPLICABLE,
                session.encrypt("Control subóptimo pese a adherencia. Se ajusta esquema.")));
        diagnoses.save(Diagnosis.record(encuentro, "E66.9", DiagnosisType.FINAL, false, true,
                Laterality.NOT_APPLICABLE,
                session.encrypt("IMC 29.1. Se deriva a nutricion.")));

        Prescription receta = Prescription.issue(encuentro, "REC-000001", 30,
                session.encrypt("Tomar con las comidas. No suspender sin indicacion medica. Controlar la "
                        + "presion antes de cada toma durante la primera semana."));
        receta.addItem("LOSARTAN_50", null, "1 comprimido", AdministrationRoute.ORAL, 12, 30,
                new BigDecimal("60.00"), "comprimidos",
                session.encrypt("Una toma en ayunas y otra a las 20:00."));
        receta.addItem("PARACETAMOL_500", null, "1 comprimido", AdministrationRoute.ORAL, 8, 3,
                new BigDecimal("9.00"), "comprimidos",
                session.encrypt("Solo si hay cefalea. No exceder tres tomas diarias."));
        prescriptions.save(receta);

        StudyOrder ecg = StudyOrder.request(encuentro, "ECG", StudyPriority.ROUTINE,
                "Trazado en reposo. Adjuntar lectura del cardiologo.", "I10", DR_ARISPE);
        // REQUESTED no salta a IN_PROGRESS: el ciclo obliga a pasar por la toma de la muestra.
        ecg.changeStatus(StudyOrderStatus.SAMPLE_COLLECTED);
        ecg.changeStatus(StudyOrderStatus.IN_PROGRESS);
        studyOrders.save(ecg);

        encuentro.sign(hasher.hashEncounter(encuentro), null, null);
        encounters.save(encuentro);
    }

    /** Adulto mayor con lumbalgia: orden de imagen todavia sin resultado. */
    private void seedGregorio(NarrativeCipher.CipherSession session, UUID key, Instant now) {
        MedicalRecord record = records.save(MedicalRecord.open(
                ORGANIZATION, P_GREGORIO, "EXP-000003", BRANCH_SOPOCACHI, key));

        backgrounds.save(MedicalBackground.record(record, BackgroundType.PATHOLOGICAL,
                "Neumoconiosis por exposicion laboral en mineria",
                session.encrypt("Veinticinco anos de trabajo en interior mina. Controles neumologicos anuales."),
                null, LocalDate.of(2009, 5, 20), DR_SALAZAR));
        allergies.save(Allergy.record(record, AllergyType.FOOD, "Mariscos", null,
                "Prurito y edema labial", AllergySeverity.MILD, DR_SALAZAR));

        Instant startsAt = now.minus(6, ChronoUnit.DAYS);
        ClinicalEncounter encuentro = ClinicalEncounter.open(record, null, DR_SALAZAR, BRANCH_SOPOCACHI,
                EncounterType.INITIAL_CONSULTATION, startsAt, key);
        encuentro.replaceNarrative(
                session.encrypt("Dolor lumbar de tres semanas de evolucion."),
                session.encrypt("Dolor lumbar bajo, sin irradiacion a miembros inferiores, que aumenta al "
                        + "levantarse. Sin fiebre, sin perdida de peso, sin alteracion de esfinteres."),
                session.encrypt("Marcha conservada. Dolor a la palpacion de musculatura paravertebral L4-L5. "
                        + "Lasegue negativo bilateral. Fuerza y sensibilidad conservadas."),
                session.encrypt("Lumbago mecanico sin signos de alarma. Se solicita radiografia por la edad "
                        + "del paciente y la duracion del cuadro."),
                session.encrypt("1. Radiografia de columna lumbosacra, frente y perfil.\n"
                        + "2. Ibuprofeno 400 mg cada 8 horas por cinco dias, con alimentos.\n"
                        + "3. Reposo relativo, evitar cargar peso.\n"
                        + "4. Control con el resultado."), key);
        encuentro.updateSchedule(startsAt.plus(25, ChronoUnit.MINUTES),
                LocalDate.now().plusDays(5), EncounterType.INITIAL_CONSULTATION);
        encuentro = encounters.save(encuentro);

        vitals.save(VitalSign.builder(encuentro, startsAt.plus(3, ChronoUnit.MINUTES))
                .pressure(136, 84).rates(70, 18)
                .temperature(new BigDecimal("36.2")).oxygenSaturation(91)
                .anthropometry(new BigDecimal("67.00"), new BigDecimal("165.0"), null)
                .painScale(6).recordedBy(DR_SALAZAR).build());

        diagnoses.save(Diagnosis.record(encuentro, "M54.5", DiagnosisType.PROVISIONAL, true, false,
                Laterality.NOT_APPLICABLE,
                session.encrypt("Sin banderas rojas. Reevaluar con imagen.")));

        Prescription receta = Prescription.issue(encuentro, "REC-000002", 15,
                session.encrypt("Tomar siempre despues de comer. Suspender si aparece dolor de estomago."));
        receta.addItem("IBUPROFENO_400", null, "1 comprimido", AdministrationRoute.ORAL, 8, 5,
                new BigDecimal("15.00"), "comprimidos", null);
        receta.addItem("OMEPRAZOL_20", null, "1 capsula", AdministrationRoute.ORAL, 24, 5,
                new BigDecimal("5.00"), "capsulas",
                session.encrypt("En ayunas, 30 minutos antes del desayuno."));
        prescriptions.save(receta);

        // Sin resultado todavia: es la orden que la demostracion informa en vivo.
        StudyOrder radiografia = StudyOrder.request(encuentro, "X_RAY", StudyPriority.ROUTINE,
                "Columna lumbosacra, proyecciones anteroposterior y lateral.", "M54.5", DR_SALAZAR);
        studyOrders.save(radiografia);

        encuentro.sign(hasher.hashEncounter(encuentro), null, null);
        encounters.save(encuentro);
    }

    /** Paciente pediatrico: el expediente del menor lo abre la pediatra. */
    private void seedMateo(NarrativeCipher.CipherSession session, UUID key, Instant now) {
        MedicalRecord record = records.save(MedicalRecord.open(
                ORGANIZATION, P_MATEO, "EXP-000004", BRANCH_SOPOCACHI, key));

        backgrounds.save(MedicalBackground.record(record, BackgroundType.IMMUNIZATION,
                "Esquema de vacunacion completo para la edad",
                session.encrypt("Carnet verificado en consulta. Pendiente refuerzo de influenza estacional."),
                null, LocalDate.of(2024, 4, 18), DRA_NUNEZ));

        Instant startsAt = now.minus(11, ChronoUnit.DAYS);
        ClinicalEncounter encuentro = ClinicalEncounter.open(record, null, DRA_NUNEZ, BRANCH_SOPOCACHI,
                EncounterType.INITIAL_CONSULTATION, startsAt, key);
        encuentro.replaceNarrative(
                session.encrypt("Cuadro respiratorio alto de cuatro dias."),
                session.encrypt("Madre refiere rinorrea clara, tos seca nocturna y fiebre de hasta 38.2 C que "
                        + "cede con paracetamol. Buena tolerancia oral, sin dificultad respiratoria."),
                session.encrypt("Activo y reactivo. Faringe congestiva sin exudado. Otoscopia normal. Pulmones "
                        + "con murmullo vesicular conservado, sin ruidos agregados."),
                session.encrypt("Rinofaringitis aguda de probable origen viral. Sin criterios de antibiotico."),
                session.encrypt("1. Paracetamol jarabe segun peso, cada 8 horas si hay fiebre.\n"
                        + "2. Hidratacion abundante y lavados nasales con suero fisiologico.\n"
                        + "3. Signos de alarma explicados a la madre.\n"
                        + "4. Control si persiste la fiebre mas de 72 horas."), key);
        encuentro.updateSchedule(startsAt.plus(20, ChronoUnit.MINUTES), null,
                EncounterType.INITIAL_CONSULTATION);
        encuentro = encounters.save(encuentro);

        vitals.save(VitalSign.builder(encuentro, startsAt.plus(2, ChronoUnit.MINUTES))
                .rates(104, 24).temperature(new BigDecimal("37.8")).oxygenSaturation(96)
                .anthropometry(new BigDecimal("28.40"), new BigDecimal("128.0"), null)
                .painScale(1).recordedBy(DRA_NUNEZ).build());

        diagnoses.save(Diagnosis.record(encuentro, "J00", DiagnosisType.FINAL, true, false,
                Laterality.NOT_APPLICABLE, null));

        encuentro.sign(hasher.hashEncounter(encuentro), null, null);
        encounters.save(encuentro);
    }

    /**
     * Encuentro anulado: se abrio sobre el paciente equivocado y se anulo en vez de borrarse.
     * Es el caso que hace visible la inmutabilidad clinica en la interfaz.
     */
    private void seedFreddy(NarrativeCipher.CipherSession session, UUID key, Instant now) {
        MedicalRecord record = records.save(MedicalRecord.open(
                ORGANIZATION, P_FREDDY, "EXP-000005", BRANCH_MIRAFLORES, key));

        Instant startsAt = now.minus(30, ChronoUnit.DAYS);
        ClinicalEncounter anulado = ClinicalEncounter.open(record, null, DR_SALAZAR, BRANCH_MIRAFLORES,
                EncounterType.FOLLOW_UP_CONSULTATION, startsAt, key);
        anulado.replaceNarrative(
                session.encrypt("Apertura por error de digitacion en el numero de documento."),
                null, null, null, null, key);
        anulado.updateSchedule(startsAt.plus(5, ChronoUnit.MINUTES), null,
                EncounterType.FOLLOW_UP_CONSULTATION);
        anulado = encounters.save(anulado);
        anulado.sign(hasher.hashEncounter(anulado), null, null);
        anulado = encounters.save(anulado);
        anulado.voidEncounter(DR_SALAZAR, "Encuentro abierto sobre el paciente equivocado");
        encounters.save(anulado);

        Instant reciente = now.minus(3, ChronoUnit.DAYS);
        ClinicalEncounter valido = ClinicalEncounter.open(record, null, DR_SALAZAR, BRANCH_MIRAFLORES,
                EncounterType.TELECONSULTATION, reciente, key);
        valido.replaceNarrative(
                session.encrypt("Teleconsulta por resultado de laboratorio."),
                session.encrypt("Refiere haber recibido los resultados por correo. Sin sintomas nuevos."),
                session.encrypt("Evaluacion remota por videollamada; no se realiza examen fisico."),
                session.encrypt("Glucemia en rango. Se refuerzan medidas higienico-dieteticas."),
                session.encrypt("1. Mantener control anual.\n2. Repetir glucemia en seis meses."), key);
        valido.updateSchedule(reciente.plus(15, ChronoUnit.MINUTES),
                LocalDate.now().plusMonths(6), EncounterType.TELECONSULTATION);
        valido = encounters.save(valido);
        valido.sign(hasher.hashEncounter(valido), null, null);
        encounters.save(valido);
    }
}
