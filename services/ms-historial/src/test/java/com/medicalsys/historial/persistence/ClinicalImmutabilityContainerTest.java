package com.medicalsys.historial.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.AddendumReason;
import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.repository.ClinicalEncounterRepository;
import com.medicalsys.historial.repository.EncounterAddendumRepository;
import com.medicalsys.historial.service.ClinicalEncounterService;
import com.medicalsys.historial.support.AbstractMySqlContainerTest;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.CreateAddendumRequest;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import com.medicalsys.historial.web.dto.UpdateNarrativeRequest;
import com.medicalsys.historial.web.dto.VoidEncounterRequest;
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
 * Inmutabilidad del encuentro firmado, verificada contra el motor real (tarea MVP-09, Dia 4).
 *
 * <p>La entrega comprometida es "UPDATE rechazado": no basta con que la capa de servicio se niegue,
 * la base tiene que impedirlo aunque alguien ejecute SQL directo con el usuario del servicio.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class ClinicalImmutabilityContainerTest extends AbstractMySqlContainerTest {

    @Autowired
    private ClinicalEncounterService encounters;

    @Autowired
    private ClinicalEncounterRepository repository;

    @Autowired
    private EncounterAddendumRepository addendumRepository;

    @Autowired
    private DataSource dataSource;

    private final RequestContext context = TestFixtures.physicianContext();

    @Test
    @DisplayName("un UPDATE directo sobre la narrativa de un encuentro firmado es rechazado por la base")
    void databaseRejectsDirectUpdateOfSignedEncounter() {
        UUID signedId = signedEncounter().id();

        assertThatThrownBy(() -> jdbc().sql("""
                        UPDATE clinical_encounter
                        SET encrypted_subjective_notes = ?
                        WHERE public_id = UUID_TO_BIN(?)
                        """)
                .param(1, "narrativa-sobrescrita".getBytes())
                .param(2, signedId.toString())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CLINICAL_ENCOUNTER_IMMUTABLE");
    }

    @Test
    @DisplayName("un DELETE directo sobre un encuentro es rechazado: los registros clinicos se anulan")
    void databaseRejectsDeleteOfEncounter() {
        UUID signedId = signedEncounter().id();

        assertThatThrownBy(() -> jdbc().sql("DELETE FROM clinical_encounter WHERE public_id = UUID_TO_BIN(?)")
                .param(1, signedId.toString())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CLINICAL_ENCOUNTER_IMMUTABLE");
    }

    @Test
    @DisplayName("la capa de servicio rechaza la edicion con 422 antes de llegar a la base")
    void serviceRejectsNarrativeUpdateAfterSigning() {
        UUID signedId = signedEncounter().id();

        assertThatThrownBy(() -> encounters.updateNarrative(context, signedId,
                new UpdateNarrativeRequest("otro motivo", null, null, null, null, null, null, null)))
                .hasMessageContaining("no admite cambios");
    }

    @Test
    @DisplayName("anular sigue siendo posible y conserva la narrativa sellada")
    void voidingKeepsSealedContent() {
        ClinicalEncounterResponse signed = signedEncounter();
        byte[] sealedNarrative = entity(signed.id()).getEncryptedSubjectiveNotes();

        encounters.voidEncounter(context, signed.id(), new VoidEncounterRequest("Registro erroneo"));

        ClinicalEncounter reloaded = entity(signed.id());
        assertThat(reloaded.getStatus()).isEqualTo(EncounterStatus.VOIDED);
        assertThat(reloaded.getCancellationReason()).isEqualTo("Registro erroneo");
        assertThat(reloaded.getEncryptedSubjectiveNotes()).isEqualTo(sealedNarrative);
        assertThat(reloaded.getContentHash()).isEqualTo(signed.contentHash());
    }

    @Test
    @DisplayName("la correccion posterior se guarda como addendum y no sobrescribe el encuentro")
    void correctionIsAppendedAsAddendum() {
        ClinicalEncounterResponse signed = signedEncounter();
        byte[] sealedNarrative = entity(signed.id()).getEncryptedSubjectiveNotes();

        encounters.addAddendum(context, signed.id(), new CreateAddendumRequest(TestFixtures.PROFESSIONAL_ID,
                AddendumReason.CORRECTION, "Se aclara la dosis indicada"));

        assertThat(entity(signed.id()).getEncryptedSubjectiveNotes()).isEqualTo(sealedNarrative);
        assertThat(encounters.listAddendums(context, signed.id())).hasSize(1);
    }

    @Test
    @DisplayName("un UPDATE directo sobre un addendum es rechazado")
    void databaseRejectsAddendumUpdate() {
        ClinicalEncounterResponse signed = signedEncounter();
        encounters.addAddendum(context, signed.id(), new CreateAddendumRequest(TestFixtures.PROFESSIONAL_ID,
                AddendumReason.ADDITION, "Resultado tardio recibido"));
        Long addendumId = addendumRepository
                .findByClinicalEncounterIdOrderByCreatedAtAsc(entity(signed.id()).getId())
                .getFirst().getId();

        assertThatThrownBy(() -> jdbc()
                .sql("UPDATE encounter_addendum SET content_hash = ? WHERE id = ?")
                .param(1, "0".repeat(64))
                .param(2, addendumId)
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ENCOUNTER_ADDENDUM_IMMUTABLE");
    }

    /** Crea un encuentro real con narrativa cifrada y lo firma. */
    private ClinicalEncounterResponse signedEncounter() {
        ClinicalEncounterResponse encounter = encounters.open(context, new OpenEncounterRequest(
                UUID.randomUUID(), UUID.randomUUID(), TestFixtures.PROFESSIONAL_ID, TestFixtures.BRANCH_ID,
                null, null));
        encounters.updateNarrative(context, encounter.id(), new UpdateNarrativeRequest(
                "Cefalea de tres dias", "Relato del paciente ficticio", "Examen sin hallazgos",
                "Cefalea tensional", "Analgesia y control", null, null, null));
        return encounters.sign(context, encounter.id(), null);
    }

    private ClinicalEncounter entity(UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID, publicId)
                .orElseThrow();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
