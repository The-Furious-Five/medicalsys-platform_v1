package com.medicalsys.historial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.config.HistorialProperties.CatalogValidationMode;
import com.medicalsys.historial.config.HistorialProperties.CryptoMode;
import com.medicalsys.historial.domain.enums.RecordStatus;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.repository.MedicalRecordRepository;
import com.medicalsys.historial.service.MedicalRecordService.OpenResult;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.support.TestMappers;
import com.medicalsys.historial.web.dto.OpenMedicalRecordRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regla critica del expediente: una segunda solicitud devuelve el mismo expediente.
 */
@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository repository;

    @Mock
    private NarrativeCipher cipher;

    @Mock
    private CipherSession cipherSession;

    private MedicalRecordService service;

    @BeforeEach
    void setUp() {
        service = new MedicalRecordService(repository, new SequenceNumberGenerator(), cipher, properties(),
                TestMappers.medicalRecord(cipher));
    }

    @Test
    @DisplayName("la primera solicitud crea el expediente del paciente")
    void createsRecordOnFirstRequest() {
        when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
        when(cipherSession.keyPublicId()).thenReturn(TestFixtures.KEY_ID);
        when(repository.findByOrganizationPublicIdAndPatientPublicId(TestFixtures.ORGANIZATION_ID,
                TestFixtures.PATIENT_ID)).thenReturn(Optional.empty());
        when(repository.findMaxRecordNumber(eq(TestFixtures.ORGANIZATION_ID), anyString()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(MedicalRecord.class))).thenAnswer(call -> call.getArgument(0));

        OpenResult result = service.openOrGet(TestFixtures.physicianContext(),
                new OpenMedicalRecordRequest(TestFixtures.PATIENT_ID, TestFixtures.BRANCH_ID));

        assertThat(result.created()).isTrue();
        assertThat(result.record().patientId()).isEqualTo(TestFixtures.PATIENT_ID);
        assertThat(result.record().recordNumber()).endsWith("-000001");
        assertThat(result.record().status()).isEqualTo(RecordStatus.ACTIVE);
    }

    @Test
    @DisplayName("la segunda solicitud devuelve el mismo expediente y no crea otro")
    void reusesExistingRecord() {
        MedicalRecord existing = TestFixtures.medicalRecord();
        when(repository.findByOrganizationPublicIdAndPatientPublicId(TestFixtures.ORGANIZATION_ID,
                TestFixtures.PATIENT_ID)).thenReturn(Optional.of(existing));

        OpenResult result = service.openOrGet(TestFixtures.physicianContext(),
                new OpenMedicalRecordRequest(TestFixtures.PATIENT_ID, TestFixtures.BRANCH_ID));

        assertThat(result.created()).isFalse();
        assertThat(result.record().id()).isEqualTo(existing.getPublicId());
        assertThat(result.record().recordNumber()).isEqualTo(existing.getRecordNumber());
        verify(repository, never()).saveAndFlush(any(MedicalRecord.class));
    }

    private static HistorialProperties properties() {
        return new HistorialProperties(
                new HistorialProperties.Crypto(CryptoMode.LOCAL, "http://ms-criptografia:8084", "clave",
                        TestFixtures.KEY_ID, Duration.ofSeconds(2), Duration.ofSeconds(3)),
                new HistorialProperties.Catalogs(CatalogValidationMode.LOCAL, "http://ms-catalogos:8083",
                        Duration.ofSeconds(2), Duration.ofSeconds(3),
                        new HistorialProperties.Catalogs.Local(Set.of("J00"), Set.of("X_RAY"),
                                Set.of("PARACETAMOL_500"))),
                new HistorialProperties.Numbering("EXP", "REC", 5));
    }
}
