package com.medicalsys.pacientes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.pacientes.common.error.BusinessRuleException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientMerge;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.repository.PatientMergeRepository;
import com.medicalsys.pacientes.web.dto.MergePatientRequest;
import com.medicalsys.pacientes.web.dto.PatientMergeResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientMergeServiceTest {

    @Mock
    private PatientMergeRepository repository;
    @Mock
    private PatientService patients;

    private PatientMergeService service;

    @BeforeEach
    void setUp() {
        service = new PatientMergeService(repository, patients, new PatientMapper());
    }

    @Test
    void rejectsMergingAPatientIntoItself() {
        UUID patientId = UUID.randomUUID();
        MergePatientRequest request = new MergePatientRequest(patientId, "Duplicado", UUID.randomUUID());
        when(repository.findByEventPublicId(request.eventId())).thenReturn(Optional.empty());

        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> service.merge(patientId, request, UUID.randomUUID()));

        assertEquals(ErrorCode.INVALID_PATIENT_MERGE, exception.code());
    }

    @Test
    void recordsMergeAndMarksDuplicatePatientInactive() {
        Patient surviving = Patient.register(DocumentType.CI, "123", "");
        Patient duplicate = Patient.register(DocumentType.CI, "123", "A");
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        MergePatientRequest request = new MergePatientRequest(duplicate.getPublicId(), "Registro duplicado", eventId);

        when(repository.findByEventPublicId(eventId)).thenReturn(Optional.empty());
        when(repository.existsByMergedPatient_PublicId(duplicate.getPublicId())).thenReturn(false);
        when(patients.requireEntity(surviving.getPublicId())).thenReturn(surviving);
        when(patients.requireEntity(duplicate.getPublicId())).thenReturn(duplicate);
        when(repository.saveAndFlush(any(PatientMerge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientMergeResponse response = service.merge(surviving.getPublicId(), request, userId);

        assertEquals(surviving.getPublicId(), response.survivingPatientId());
        assertEquals(duplicate.getPublicId(), response.mergedPatientId());
        assertEquals(PatientStatus.INACTIVE, duplicate.getStatus());
        verify(repository).saveAndFlush(any(PatientMerge.class));
    }
}
