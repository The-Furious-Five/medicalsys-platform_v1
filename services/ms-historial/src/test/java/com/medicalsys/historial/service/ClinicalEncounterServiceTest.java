package com.medicalsys.historial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.domain.enums.AddendumReason;
import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.EncounterAddendum;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.repository.ClinicalEncounterRepository;
import com.medicalsys.historial.repository.DiagnosisRepository;
import com.medicalsys.historial.repository.EncounterAddendumRepository;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.support.TestMappers;
import com.medicalsys.historial.web.dto.CreateAddendumRequest;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import com.medicalsys.historial.web.dto.UpdateNarrativeRequest;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.VoidEncounterRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Reglas del agregado encuentro: cifrado, unicidad por cita e inmutabilidad tras la firma. */
@ExtendWith(MockitoExtension.class)
class ClinicalEncounterServiceTest {

    @Mock
    private ClinicalEncounterRepository repository;

    @Mock
    private EncounterAddendumRepository addendumRepository;

    @Mock
    private DiagnosisRepository diagnosisRepository;

    @Mock
    private MedicalRecordService medicalRecords;

    @Mock
    private NarrativeCipher cipher;

    @Mock
    private CipherSession cipherSession;

    private ClinicalEncounterService service;
    private MedicalRecord record;
    private ClinicalEncounter encounter;

    @BeforeEach
    void setUp() {
        service = new ClinicalEncounterService(repository, addendumRepository, diagnosisRepository,
                medicalRecords, cipher, new ContentHasher(), TestMappers.clinicalEncounter(cipher));
        lenient().when(diagnosisRepository.findByClinicalEncounterIdOrderByPrimaryDescIdAsc(any()))
                .thenReturn(List.of());
        record = TestFixtures.medicalRecord();
        encounter = TestFixtures.draftEncounter(record);
    }

    @Nested
    @DisplayName("apertura")
    class Opening {

        @Test
        @DisplayName("abre el encuentro sobre el expediente del paciente")
        void opensEncounter() {
            when(medicalRecords.getOrCreateActiveRecord(any(), any(), any())).thenReturn(record);
            when(repository.existsByAppointmentPublicId(TestFixtures.APPOINTMENT_ID)).thenReturn(false);
            when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
            when(cipherSession.keyPublicId()).thenReturn(TestFixtures.KEY_ID);
            when(repository.saveAndFlush(any(ClinicalEncounter.class)))
                    .thenAnswer(call -> call.getArgument(0));

            ClinicalEncounterResponse created = service.open(TestFixtures.physicianContext(), request());

            assertThat(created.status()).isEqualTo(EncounterStatus.DRAFT);
            assertThat(created.medicalRecordId()).isEqualTo(record.getPublicId());
            assertThat(created.patientId()).isEqualTo(record.getPatientPublicId());
            assertThat(created.appointmentId()).isEqualTo(TestFixtures.APPOINTMENT_ID);
        }

        @Test
        @DisplayName("una cita no puede producir dos encuentros")
        void rejectsDuplicatedAppointment() {
            when(medicalRecords.getOrCreateActiveRecord(any(), any(), any())).thenReturn(record);
            when(repository.existsByAppointmentPublicId(TestFixtures.APPOINTMENT_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.open(TestFixtures.physicianContext(), request()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                    .isEqualTo(ErrorCode.ENCOUNTER_ALREADY_EXISTS_FOR_APPOINTMENT);

            verify(repository, never()).saveAndFlush(any(ClinicalEncounter.class));
        }

        private OpenEncounterRequest request() {
            return new OpenEncounterRequest(TestFixtures.PATIENT_ID, TestFixtures.APPOINTMENT_ID,
                    TestFixtures.PROFESSIONAL_ID, TestFixtures.BRANCH_ID, null, null);
        }
    }

    @Nested
    @DisplayName("narrativa")
    class Narrative {

        @Test
        @DisplayName("la narrativa se persiste cifrada y nunca en claro")
        void storesCiphertext() {
            byte[] ciphertext = "ciphertext-simulado".getBytes(StandardCharsets.UTF_8);
            givenEncounterFound();
            when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
            when(cipherSession.encrypt(any())).thenReturn(ciphertext);
            when(cipherSession.keyPublicId()).thenReturn(TestFixtures.KEY_ID);

            ClinicalEncounterResponse updated = service.updateNarrative(TestFixtures.physicianContext(),
                    encounter.getPublicId(), new UpdateNarrativeRequest("Cefalea", "Relato", "Examen",
                            "Analisis", "Plan", null, null, null));

            assertThat(updated.encrypted()).isTrue();
            assertThat(updated.encryptionKeyId()).isEqualTo(TestFixtures.KEY_ID);
            // La entidad conserva ciphertext, nunca el texto en claro.
            assertThat(encounter.getEncryptedSubjectiveNotes()).isEqualTo(ciphertext);
            assertThat(encounter.getEncryptedPlan()).isEqualTo(ciphertext);
        }
    }

    @Nested
    @DisplayName("inmutabilidad del encuentro firmado")
    class Immutability {

        @Test
        @DisplayName("firmar sella el contenido con un hash y una fecha")
        void signSealsContent() {
            givenEncounterFound();

            ClinicalEncounterResponse signed = service.sign(TestFixtures.physicianContext(),
                    encounter.getPublicId(), null);

            assertThat(signed.status()).isEqualTo(EncounterStatus.SIGNED);
            assertThat(signed.contentHash()).hasSize(64);
            assertThat(signed.signedAt()).isNotNull();
        }

        @Test
        @DisplayName("un encuentro firmado rechaza la edicion de su narrativa")
        void rejectsNarrativeUpdateAfterSigning() {
            encounter.sign("a".repeat(64), null, null);
            givenEncounterFound();

            assertThatThrownBy(() -> service.updateNarrative(TestFixtures.physicianContext(),
                    encounter.getPublicId(),
                    new UpdateNarrativeRequest("otro", null, null, null, null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                    .isEqualTo(ErrorCode.ENCOUNTER_NOT_EDITABLE);
        }

        @Test
        @DisplayName("un encuentro firmado no puede volver a firmarse")
        void rejectsDoubleSignature() {
            encounter.sign("a".repeat(64), null, null);
            givenEncounterFound();

            assertThatThrownBy(() -> service.sign(TestFixtures.physicianContext(), encounter.getPublicId(), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                    .isEqualTo(ErrorCode.ENCOUNTER_NOT_SIGNABLE);
        }

        @Test
        @DisplayName("un encuentro anulado no admite ninguna transicion")
        void rejectsChangesOnVoidedEncounter() {
            encounter.voidEncounter(TestFixtures.USER_ID, "Error de registro");
            givenEncounterFound();

            assertThatThrownBy(() -> service.voidEncounter(TestFixtures.physicianContext(),
                    encounter.getPublicId(), new VoidEncounterRequest("otra vez")))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("la correccion posterior se registra como addendum sin sobrescribir el encuentro")
        void correctionGoesToAddendum() {
            byte[] ciphertext = "ciphertext-addendum".getBytes(StandardCharsets.UTF_8);
            encounter.sign("a".repeat(64), null, null);
            byte[] originalNarrative = encounter.getEncryptedSubjectiveNotes();
            givenEncounterFound();
            when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
            when(cipherSession.encrypt(any())).thenReturn(ciphertext);
            when(cipherSession.keyPublicId()).thenReturn(TestFixtures.KEY_ID);
            var captor = org.mockito.ArgumentCaptor.forClass(EncounterAddendum.class);
            when(addendumRepository.saveAndFlush(any(EncounterAddendum.class)))
                    .thenAnswer(call -> call.getArgument(0));

            var addendum = service.addAddendum(TestFixtures.physicianContext(), encounter.getPublicId(),
                    new CreateAddendumRequest(TestFixtures.PROFESSIONAL_ID, AddendumReason.CORRECTION,
                            "Se corrige la dosis indicada"));

            verify(addendumRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getEncryptedText()).isEqualTo(ciphertext);
            assertThat(addendum.contentHash()).hasSize(64);
            assertThat(addendum.reason()).isEqualTo(AddendumReason.CORRECTION);
            // El encuentro original no se toca: la correccion es una adicion.
            assertThat(encounter.getEncryptedSubjectiveNotes()).isEqualTo(originalNarrative);
            assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.SIGNED);
        }

        @Test
        @DisplayName("un borrador no admite addendums: se edita el borrador")
        void rejectsAddendumOnDraft() {
            givenEncounterFound();

            assertThatThrownBy(() -> service.addAddendum(TestFixtures.physicianContext(),
                    encounter.getPublicId(), new CreateAddendumRequest(TestFixtures.PROFESSIONAL_ID,
                            AddendumReason.ADDITION, "texto")))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                    .isEqualTo(ErrorCode.ENCOUNTER_NOT_SIGNED);
        }
    }

    @Nested
    @DisplayName("aislamiento por organizacion")
    class TenantIsolation {

        @Test
        @DisplayName("un encuentro de otro tenant se responde como inexistente")
        void hidesEncountersOfOtherTenants() {
            lenient().when(repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID,
                    encounter.getPublicId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(TestFixtures.physicianContext(), encounter.getPublicId()))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                    .isEqualTo(ErrorCode.CLINICAL_ENCOUNTER_NOT_FOUND);
        }
    }

    private void givenEncounterFound() {
        when(repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID,
                encounter.getPublicId())).thenReturn(Optional.of(encounter));
    }
}
