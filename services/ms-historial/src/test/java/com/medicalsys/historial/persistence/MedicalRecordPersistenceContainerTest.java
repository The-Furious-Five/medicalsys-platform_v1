package com.medicalsys.historial.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.repository.ClinicalEncounterRepository;
import com.medicalsys.historial.repository.MedicalRecordRepository;
import com.medicalsys.historial.service.ClinicalEncounterService;
import com.medicalsys.historial.service.MedicalRecordService;
import com.medicalsys.historial.service.StudyOrderService;
import com.medicalsys.historial.support.AbstractMySqlContainerTest;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.CreateStudyOrderRequest;
import com.medicalsys.historial.web.dto.MedicalRecordResponse;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import com.medicalsys.historial.web.dto.OpenMedicalRecordRequest;
import com.medicalsys.historial.web.dto.RegisterStudyResultRequest;
import com.medicalsys.historial.web.dto.StudyOrderResponse;
import com.medicalsys.historial.web.dto.UpdateNarrativeRequest;
import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reglas criticas del expediente y del flujo de estudio contra el motor real.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class MedicalRecordPersistenceContainerTest extends AbstractMySqlContainerTest {

    @Autowired
    private MedicalRecordService medicalRecords;

    @Autowired
    private ClinicalEncounterService encounters;

    @Autowired
    private StudyOrderService studyOrders;

    @Autowired
    private MedicalRecordRepository recordRepository;

    @Autowired
    private ClinicalEncounterRepository encounterRepository;

    @Autowired
    private DataSource dataSource;

    private final RequestContext context = TestFixtures.physicianContext();

    @Test
    @DisplayName("una segunda solicitud devuelve el mismo expediente")
    void openIsIdempotent() {
        UUID patientId = UUID.randomUUID();
        var request = new OpenMedicalRecordRequest(patientId, TestFixtures.BRANCH_ID);

        var first = medicalRecords.openOrGet(context, request);
        var second = medicalRecords.openOrGet(context, request);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.record().id()).isEqualTo(first.record().id());
        assertThat(second.record().recordNumber()).isEqualTo(first.record().recordNumber());
    }

    @Test
    @DisplayName("el mismo paciente en otra organizacion tiene un expediente distinto")
    void isolatesRecordsPerOrganization() {
        UUID patientId = UUID.randomUUID();
        var request = new OpenMedicalRecordRequest(patientId, TestFixtures.BRANCH_ID);
        RequestContext otherTenant = new RequestContext(TestFixtures.OTHER_ORGANIZATION_ID,
                TestFixtures.USER_ID, context.roles());

        MedicalRecordResponse own = medicalRecords.openOrGet(context, request).record();
        MedicalRecordResponse other = medicalRecords.openOrGet(otherTenant, request).record();

        assertThat(other.id()).isNotEqualTo(own.id());
        assertThat(recordRepository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID,
                other.id())).isEmpty();
    }

    @Test
    @DisplayName("el correlativo de expediente es unico dentro de la organizacion")
    void recordNumberIsUniquePerOrganization() {
        MedicalRecordResponse existing = medicalRecords
                .openOrGet(context, new OpenMedicalRecordRequest(UUID.randomUUID(), null)).record();

        assertThatThrownBy(() -> jdbc().sql("""
                        INSERT INTO medical_record (public_id, organization_public_id, patient_public_id,
                                                    record_number, encryption_key_public_id)
                        VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?))
                        """)
                .param(1, UUID.randomUUID().toString())
                .param(2, TestFixtures.ORGANIZATION_ID.toString())
                .param(3, UUID.randomUUID().toString())
                .param(4, existing.recordNumber())
                .param(5, TestFixtures.KEY_ID.toString())
                .update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("la narrativa queda cifrada en la base: no aparece el texto en claro")
    void narrativeIsStoredAsCiphertext() {
        ClinicalEncounterResponse encounter = openEncounter();
        encounters.updateNarrative(context, encounter.id(), new UpdateNarrativeRequest(
                null, "SECRETO-CLINICO-VISIBLE", null, null, null, null, null, null));

        Integer matches = jdbc().sql("""
                        SELECT COUNT(*) FROM clinical_encounter
                        WHERE public_id = UUID_TO_BIN(?)
                          AND encrypted_subjective_notes LIKE '%SECRETO-CLINICO-VISIBLE%'
                        """)
                .param(1, encounter.id().toString())
                .query(Integer.class).single();

        assertThat(matches).isZero();
    }

    @Test
    @DisplayName("el resultado guarda la referencia documental y deja la orden informada")
    void studyResultKeepsOnlyDocumentReference() {
        ClinicalEncounterResponse encounter = openEncounter();
        StudyOrderResponse order = studyOrders.create(context, encounter.id(),
                new CreateStudyOrderRequest("X_RAY", null, "Placa de torax", "J00"));

        assertThat(order.status()).isEqualTo(StudyOrderStatus.REQUESTED);
        assertThat(order.medicalRecordId()).isEqualTo(encounter.medicalRecordId());

        studyOrders.registerResult(context, order.id(), new RegisterStudyResultRequest(
                null, null, "Sin hallazgos patologicos", null, null, null, null,
                TestFixtures.DOCUMENT_ID, TestFixtures.PROFESSIONAL_ID));

        StudyOrderResponse reloaded = studyOrders.get(context, order.id());

        assertThat(reloaded.status()).isEqualTo(StudyOrderStatus.REPORTED);
        assertThat(reloaded.results()).singleElement().satisfies(result -> {
            assertThat(result.documentId()).isEqualTo(TestFixtures.DOCUMENT_ID);
            assertThat(result.textValue()).isEqualTo("Sin hallazgos patologicos");
        });
    }

    @Test
    @DisplayName("el indice de masa corporal lo calcula la base a partir de peso y talla")
    void bmiIsCalculatedByTheDatabase() {
        ClinicalEncounterResponse encounter = openEncounter();
        Long encounterId = entity(encounter.id()).getId();

        jdbc().sql("""
                        INSERT INTO vital_sign (clinical_encounter_id, measured_at, weight_kg, height_cm)
                        VALUES (?, UTC_TIMESTAMP(), 70.00, 175.0)
                        """)
                .param(1, encounterId)
                .update();

        BigDecimal bmi = jdbc().sql("SELECT bmi FROM vital_sign WHERE clinical_encounter_id = ?")
                .param(1, encounterId)
                .query(BigDecimal.class).single();

        assertThat(bmi).isEqualByComparingTo("22.86");
    }

    private ClinicalEncounterResponse openEncounter() {
        return encounters.open(context, new OpenEncounterRequest(UUID.randomUUID(), UUID.randomUUID(),
                TestFixtures.PROFESSIONAL_ID, TestFixtures.BRANCH_ID, null, null));
    }

    private ClinicalEncounter entity(UUID publicId) {
        return encounterRepository
                .findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID, publicId)
                .orElseThrow();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
