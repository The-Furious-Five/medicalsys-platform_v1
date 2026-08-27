package com.medicalsys.pacientes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.pacientes.api.dto.CreatePatientRequest;
import com.medicalsys.pacientes.api.dto.PatientResponse;
import com.medicalsys.pacientes.domain.Department;
import com.medicalsys.pacientes.domain.DocumentType;
import com.medicalsys.pacientes.domain.Patient;
import com.medicalsys.pacientes.domain.PatientSex;
import com.medicalsys.pacientes.exception.DuplicatePatientDocumentException;
import com.medicalsys.pacientes.exception.InvalidPatientDataException;
import com.medicalsys.pacientes.exception.PatientNotFoundException;
import com.medicalsys.pacientes.exception.UnsupportedDocumentTypeException;
import com.medicalsys.pacientes.repository.PatientRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
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
    void createsPatientAndNormalizesDocumentAndEmail() {
        when(repository.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
                DocumentType.CI, "DEMO-0001", "1A")).thenReturn(false);
        when(repository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = service.create(request(
                DocumentType.CI, " demo-0001 ", " 1a ", LocalDate.of(1995, 4, 10)));

        assertNotNull(response.patientId());
        assertEquals("DEMO-0001", response.documentNumber());
        assertEquals("1A", response.documentComplement());
        assertEquals("paciente.demo@example.invalid", response.email());
        assertEquals("ACTIVE", response.status());

        ArgumentCaptor<Patient> patient = ArgumentCaptor.forClass(Patient.class);
        verify(repository).saveAndFlush(patient.capture());
        assertEquals(PatientSex.F, patient.getValue().getSex());
        assertEquals("Maria Elena", patient.getValue().getGivenNames());
    }

    @Test
    void rejectsDuplicateBeforeInsert() {
        when(repository.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
                DocumentType.CI, "DEMO-0001", "")).thenReturn(true);

        assertThrows(DuplicatePatientDocumentException.class,
                () -> service.create(request(DocumentType.CI, "DEMO-0001", null,
                        LocalDate.of(1995, 4, 10))));
    }

    @Test
    void rejectsDocumentTypeOutsideMvp() {
        assertThrows(UnsupportedDocumentTypeException.class,
                () -> service.create(request(DocumentType.PASSPORT, "DEMO-0001", null,
                        LocalDate.of(1995, 4, 10))));
    }

    @Test
    void rejectsBirthDateBeforeDatabaseMinimum() {
        assertThrows(InvalidPatientDataException.class,
                () -> service.create(request(DocumentType.CI, "DEMO-0001", null,
                        LocalDate.of(1899, 12, 31))));
    }

    @Test
    void returnsNotFoundForUnknownPublicId() {
        UUID id = UUID.randomUUID();
        when(repository.findByPublicId(id)).thenReturn(Optional.empty());

        assertThrows(PatientNotFoundException.class, () -> service.getByPublicId(id));
    }

    private CreatePatientRequest request(DocumentType type, String number, String complement,
            LocalDate birthDate) {
        return new CreatePatientRequest(
                type,
                number,
                complement,
                Department.LA_PAZ,
                "Maria Elena",
                "Ficticia",
                "Prueba",
                birthDate,
                PatientSex.F,
                "+59170000001",
                " Paciente.Demo@Example.Invalid ",
                "La Paz",
                Department.LA_PAZ);
    }
}
