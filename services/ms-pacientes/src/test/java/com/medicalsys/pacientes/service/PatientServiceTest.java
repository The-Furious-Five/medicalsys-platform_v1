package com.medicalsys.pacientes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.pacientes.common.error.ConflictException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.repository.PatientRepository;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.RegisterPatientRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository repository;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(repository, new PatientMapper());
    }

    @Test
    void rejectsARepeatedCiWithTheSameComplement() {
        when(repository.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
                DocumentType.CI, "123", "")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.register(request("123", null)));

        assertEquals(ErrorCode.PATIENT_DOCUMENT_ALREADY_EXISTS, exception.code());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void allowsCi123WithoutComplementAndCi123WithComplementA() {
        when(repository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse withoutComplement = service.register(request(" 123 ", null));
        PatientResponse withComplementA = service.register(request("123", " a "));

        assertEquals("123", withoutComplement.documentNumber());
        assertEquals("", withoutComplement.documentComplement());
        assertEquals("123", withComplementA.documentNumber());
        assertEquals("A", withComplementA.documentComplement());
        assertNotEquals(withoutComplement.patientId(), withComplementA.patientId());

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        List<Patient> patients = saved.getAllValues();
        assertEquals("", patients.get(0).getDocumentComplement());
        assertEquals("A", patients.get(1).getDocumentComplement());
    }

    @Test
    void storesAndReturnsEverySupportedPatientField() {
        when(repository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = service.register(request("abc-987", "1b"));

        assertNotNull(response.patientId());
        assertEquals("ABC-987", response.documentNumber());
        assertEquals("1B", response.documentComplement());
        assertEquals(Department.LA_PAZ, response.issuedDepartment());
        assertEquals(MaritalStatus.SINGLE, response.maritalStatus());
        assertEquals(BloodType.O_POSITIVE, response.bloodType());
        assertEquals("Arquitecta", response.occupation());
        assertEquals("paciente.demo@example.invalid", response.email());
        assertEquals("AQIDBA==", response.encryptedAddressBase64());
        assertEquals(Department.COCHABAMBA, response.department());
        assertEquals("Madre", response.emergencyContactRelationship());
    }

    private RegisterPatientRequest request(String number, String complement) {
        return new RegisterPatientRequest(
                null,
                DocumentType.CI,
                number,
                complement,
                Department.LA_PAZ,
                "Maria Elena",
                "Ficticia",
                "Prueba",
                LocalDate.of(1995, 4, 10),
                Sex.F,
                MaritalStatus.SINGLE,
                BloodType.O_POSITIVE,
                "Arquitecta",
                "+59170000001",
                "+59170000002",
                " Paciente.Demo@Example.Invalid ",
                "AQIDBA==",
                "Cochabamba",
                Department.COCHABAMBA,
                "Persona Ficticia",
                "+59170000003",
                "Madre",
                null,
                null,
                null);
    }
}
