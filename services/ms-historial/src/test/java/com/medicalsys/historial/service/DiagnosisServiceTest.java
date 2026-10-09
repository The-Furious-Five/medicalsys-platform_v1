package com.medicalsys.historial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.Diagnosis;
import com.medicalsys.historial.repository.DiagnosisRepository;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.support.TestMappers;
import com.medicalsys.historial.web.dto.CreateDiagnosisRequest;
import com.medicalsys.historial.web.dto.DiagnosisResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Un CIE-10 invalido se rechaza antes de tocar la base. */
@ExtendWith(MockitoExtension.class)
class DiagnosisServiceTest {

    @Mock
    private DiagnosisRepository repository;

    @Mock
    private ClinicalEncounterService encounters;

    @Mock
    private CatalogsClient catalogs;

    @Mock
    private NarrativeCipher cipher;

    @Mock
    private CipherSession cipherSession;

    private DiagnosisService service;
    private ClinicalEncounter encounter;

    @BeforeEach
    void setUp() {
        service = new DiagnosisService(repository, encounters, catalogs, cipher,
                TestMappers.clinicalEncounter(cipher));
        encounter = TestFixtures.draftEncounter(TestFixtures.medicalRecord());
    }

    @Test
    @DisplayName("registra el diagnostico normalizando el codigo y cifrando la observacion")
    void registersDiagnosis() {
        when(encounters.requireEditable(any(), any())).thenReturn(encounter);
        when(repository.existsByClinicalEncounterIdAndIcd10Code(encounter.getId(), "J00")).thenReturn(false);
        when(repository.findByClinicalEncounterIdAndPrimaryTrue(encounter.getId())).thenReturn(List.of());
        when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
        when(cipherSession.encrypt(any())).thenReturn("obs-cifrada".getBytes(StandardCharsets.UTF_8));
        when(repository.saveAndFlush(any(Diagnosis.class))).thenAnswer(call -> call.getArgument(0));

        DiagnosisResponse diagnosis = service.create(TestFixtures.physicianContext(),
                encounter.getPublicId(),
                new CreateDiagnosisRequest(" j00 ", null, true, false, null, "Cuadro leve"));

        assertThat(diagnosis.icd10Code()).isEqualTo("J00");
        assertThat(diagnosis.primary()).isTrue();
        verify(catalogs).requireIcd10Code("J00");
        verify(cipherSession).encrypt("Cuadro leve");
    }

    @Test
    @DisplayName("un CIE-10 inexistente se rechaza y no se persiste nada")
    void rejectsInvalidIcd10Code() {
        when(encounters.requireEditable(any(), any())).thenReturn(encounter);
        Mockito.doThrow(new BusinessRuleException(ErrorCode.ICD10_CODE_NOT_ALLOWED, "no existe"))
                .when(catalogs).requireIcd10Code("ZZZ999");

        assertThatThrownBy(() -> service.create(TestFixtures.physicianContext(), encounter.getPublicId(),
                new CreateDiagnosisRequest("ZZZ999", null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.ICD10_CODE_NOT_ALLOWED);

        verify(repository, never()).saveAndFlush(any(Diagnosis.class));
    }

    @Test
    @DisplayName("el mismo codigo no se registra dos veces en el encuentro")
    void rejectsDuplicatedCode() {
        when(encounters.requireEditable(any(), any())).thenReturn(encounter);
        when(repository.existsByClinicalEncounterIdAndIcd10Code(encounter.getId(), "J00")).thenReturn(true);

        assertThatThrownBy(() -> service.create(TestFixtures.physicianContext(), encounter.getPublicId(),
                new CreateDiagnosisRequest("J00", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("un nuevo diagnostico principal desplaza al anterior")
    void keepsSinglePrimaryDiagnosis() {
        Diagnosis previous = Diagnosis.record(encounter, "M54.5", null, true, false, null, null);
        when(encounters.requireEditable(any(), any())).thenReturn(encounter);
        when(repository.existsByClinicalEncounterIdAndIcd10Code(encounter.getId(), "J00")).thenReturn(false);
        when(repository.findByClinicalEncounterIdAndPrimaryTrue(encounter.getId()))
                .thenReturn(List.of(previous));
        when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
        when(repository.saveAndFlush(any(Diagnosis.class))).thenAnswer(call -> call.getArgument(0));

        service.create(TestFixtures.physicianContext(), encounter.getPublicId(),
                new CreateDiagnosisRequest("J00", null, true, null, null, null));

        assertThat(previous.isPrimary()).isFalse();
    }
}
