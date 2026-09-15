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
import com.medicalsys.historial.client.PatientAccessClient;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyPriority;
import com.medicalsys.historial.domain.enums.StudyResultStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.domain.model.StudyOrder;
import com.medicalsys.historial.domain.model.StudyResult;
import com.medicalsys.historial.repository.StudyOrderRepository;
import com.medicalsys.historial.repository.StudyResultRepository;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.support.TestMappers;
import com.medicalsys.historial.web.dto.CreateStudyOrderRequest;
import com.medicalsys.historial.web.dto.RegisterStudyResultRequest;
import com.medicalsys.historial.web.dto.StudyOrderResponse;
import com.medicalsys.historial.web.dto.StudyResultResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Orden de rayos X y registro de resultado.
 *
 * <p>Verifica lo comprometido en el backlog: la orden nace solicitada y pertenece al expediente
 * correcto, y el resultado solo guarda la referencia documental, nunca el binario.
 */
@ExtendWith(MockitoExtension.class)
class StudyOrderServiceTest {

    @Mock
    private StudyOrderRepository repository;

    @Mock
    private StudyResultRepository resultRepository;

    @Mock
    private ClinicalEncounterService encounters;

    @Mock
    private CatalogsClient catalogs;

    @Mock
    private NarrativeCipher cipher;

    @Mock
    private CipherSession cipherSession;

    @Mock
    private PatientAccessClient patientAccess;

    private StudyOrderService service;
    private MedicalRecord record;
    private ClinicalEncounter encounter;

    @BeforeEach
    void setUp() {
        service = new StudyOrderService(repository, resultRepository, encounters, catalogs, cipher,
                TestMappers.studyOrder(cipher), patientAccess);
        record = TestFixtures.medicalRecord();
        encounter = TestFixtures.draftEncounter(record);
    }

    @Test
    @DisplayName("crea la orden de rayos X en estado solicitada y ligada al expediente correcto")
    void createsXRayOrder() {
        when(encounters.requireNotVoided(any(), any())).thenReturn(encounter);
        when(repository.saveAndFlush(any(StudyOrder.class))).thenAnswer(call -> call.getArgument(0));

        StudyOrderResponse order = service.create(TestFixtures.physicianContext(), encounter.getPublicId(),
                new CreateStudyOrderRequest("x_ray", StudyPriority.ROUTINE, "Placa de torax", "J00"));

        assertThat(order.status()).isEqualTo(StudyOrderStatus.REQUESTED);
        assertThat(order.studyTypeCode()).isEqualTo("X_RAY");
        assertThat(order.id()).isNotNull();
        // La orden pertenece al expediente correcto.
        assertThat(order.medicalRecordId()).isEqualTo(record.getPublicId());
        assertThat(order.patientId()).isEqualTo(record.getPatientPublicId());
        assertThat(order.encounterId()).isEqualTo(encounter.getPublicId());
        verify(catalogs).requireStudyTypeCode("X_RAY");
        verify(catalogs).requireIcd10Code("J00");
    }

    @Test
    @DisplayName("un tipo de estudio invalido impide crear la orden")
    void rejectsUnknownStudyType() {
        when(encounters.requireNotVoided(any(), any())).thenReturn(encounter);
        doThrowStudyTypeNotAllowed();

        assertThatThrownBy(() -> service.create(TestFixtures.physicianContext(), encounter.getPublicId(),
                new CreateStudyOrderRequest("RESONANCIA", null, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).saveAndFlush(any(StudyOrder.class));
    }

    @Test
    @DisplayName("registrar el resultado deja la orden informada y guarda solo la referencia documental")
    void registersResultWithDocumentReference() {
        StudyOrder order = persistedOrder();
        when(repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID, order.getPublicId()))
                .thenReturn(Optional.of(order));
        when(resultRepository.existsByStudyOrderIdAndParameter(order.getId(), "RESULT")).thenReturn(false);
        when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
        when(cipherSession.encrypt(any())).thenReturn("informe-cifrado".getBytes(StandardCharsets.UTF_8));
        when(cipherSession.keyPublicId()).thenReturn(TestFixtures.KEY_ID);
        var captor = org.mockito.ArgumentCaptor.forClass(StudyResult.class);
        when(resultRepository.saveAndFlush(any(StudyResult.class))).thenAnswer(call -> call.getArgument(0));

        StudyResultResponse result = service.registerResult(TestFixtures.physicianContext(),
                order.getPublicId(), new RegisterStudyResultRequest(null, null, "Sin hallazgos patologicos",
                        null, null, null, null, TestFixtures.DOCUMENT_ID, TestFixtures.PROFESSIONAL_ID));

        assertThat(order.getStatus()).isEqualTo(StudyOrderStatus.REPORTED);
        assertThat(result.documentId()).isEqualTo(TestFixtures.DOCUMENT_ID);
        assertThat(result.status()).isEqualTo(StudyResultStatus.FINAL);
        assertThat(result.parameter()).isEqualTo(StudyResult.DEFAULT_PARAMETER);
        // El informe se persiste cifrado; el binario nunca vive en historial.
        verify(resultRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEncryptedTextValue()).isNotNull();
        assertThat(captor.getValue().getEncryptionKeyPublicId()).isEqualTo(TestFixtures.KEY_ID);
    }

    @Test
    @DisplayName("marca fuera de rango cuando el valor supera el limite de referencia")
    void flagsOutOfRangeValues() {
        StudyOrder order = persistedOrder();
        when(repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID, order.getPublicId()))
                .thenReturn(Optional.of(order));
        when(resultRepository.existsByStudyOrderIdAndParameter(order.getId(), "HEMOGLOBINA")).thenReturn(false);
        when(cipher.open(TestFixtures.ORGANIZATION_ID)).thenReturn(cipherSession);
        when(resultRepository.saveAndFlush(any(StudyResult.class))).thenAnswer(call -> call.getArgument(0));

        StudyResultResponse result = service.registerResult(TestFixtures.physicianContext(),
                order.getPublicId(), new RegisterStudyResultRequest("HEMOGLOBINA",
                        new java.math.BigDecimal("19.0"), null, "g/dL", new java.math.BigDecimal("12.0"),
                        new java.math.BigDecimal("17.0"), null, null, null));

        assertThat(result.outOfRange()).isTrue();
    }

    @Test
    @DisplayName("no se informa dos veces el mismo parametro de una orden")
    void rejectsDuplicatedParameter() {
        StudyOrder order = persistedOrder();
        when(repository.findByOrganizationPublicIdAndPublicId(TestFixtures.ORGANIZATION_ID, order.getPublicId()))
                .thenReturn(Optional.of(order));
        when(resultRepository.existsByStudyOrderIdAndParameter(order.getId(), "RESULT")).thenReturn(true);

        assertThatThrownBy(() -> service.registerResult(TestFixtures.physicianContext(), order.getPublicId(),
                new RegisterStudyResultRequest(null, null, null, null, null, null, null,
                        TestFixtures.DOCUMENT_ID, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.STUDY_RESULT_ALREADY_REGISTERED);
    }

    private StudyOrder persistedOrder() {
        StudyOrder order = StudyOrder.request(encounter, "X_RAY", StudyPriority.ROUTINE, null, null,
                TestFixtures.PROFESSIONAL_ID);
        ReflectionTestUtils.setField(order, "id", 100L);
        return order;
    }

    private void doThrowStudyTypeNotAllowed() {
        org.mockito.Mockito.doThrow(new BusinessRuleException(ErrorCode.STUDY_TYPE_NOT_ALLOWED, "invalido"))
                .when(catalogs).requireStudyTypeCode("RESONANCIA");
    }
}
