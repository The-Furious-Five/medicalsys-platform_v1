package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.api.dto.BillingPatientSummary;
import com.medicalsys.pacientes.api.dto.CreatePatientRequest;
import com.medicalsys.pacientes.api.dto.PatientResponse;
import com.medicalsys.pacientes.api.dto.SchedulingPatientSummary;
import com.medicalsys.pacientes.domain.DocumentType;
import com.medicalsys.pacientes.domain.Patient;
import com.medicalsys.pacientes.exception.DuplicatePatientDocumentException;
import com.medicalsys.pacientes.exception.InvalidPatientDataException;
import com.medicalsys.pacientes.exception.PatientNotFoundException;
import com.medicalsys.pacientes.exception.UnsupportedDocumentTypeException;
import com.medicalsys.pacientes.repository.PatientRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private static final LocalDate MINIMUM_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final PatientRepository repository;
    private final PatientMapper mapper;

    public PatientService(PatientRepository repository, PatientMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        requireSupportedDocumentType(request.documentType());
        validateBirthDate(request.birthDate());

        String documentNumber = upper(request.documentNumber());
        String documentComplement = upperOrEmpty(request.documentComplement());

        if (repository.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
                request.documentType(), documentNumber, documentComplement)) {
            throw new DuplicatePatientDocumentException();
        }

        Patient patient = Patient.create(
                request.documentType(),
                documentNumber,
                documentComplement,
                request.issuedDepartment(),
                trim(request.givenNames()),
                trim(request.paternalSurname()),
                trim(request.maternalSurname()),
                request.birthDate(),
                request.sex(),
                trim(request.phoneE164()),
                lowerOrNull(request.email()),
                trimOrNull(request.city()),
                request.department());

        try {
            return mapper.toResponse(repository.saveAndFlush(patient));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicatePatientDocumentException(exception);
        }
    }

    @Transactional(readOnly = true)
    public PatientResponse getByPublicId(UUID patientId) {
        return mapper.toResponse(requirePatient(patientId));
    }

    @Transactional(readOnly = true)
    public PatientResponse getByDocument(DocumentType documentType, String documentNumber,
            String documentComplement) {
        requireSupportedDocumentType(documentType);
        Patient patient = repository.findByDocumentTypeAndDocumentNumberAndDocumentComplement(
                        documentType, upper(documentNumber), upperOrEmpty(documentComplement))
                .orElseThrow(PatientNotFoundException::new);
        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public SchedulingPatientSummary getSchedulingSummary(UUID patientId) {
        return mapper.toSchedulingSummary(requirePatient(patientId));
    }

    @Transactional(readOnly = true)
    public BillingPatientSummary getBillingSummary(UUID patientId) {
        return mapper.toBillingSummary(requirePatient(patientId));
    }

    private Patient requirePatient(UUID patientId) {
        return repository.findByPublicId(patientId).orElseThrow(PatientNotFoundException::new);
    }

    private void requireSupportedDocumentType(DocumentType documentType) {
        if (documentType != DocumentType.CI) {
            throw new UnsupportedDocumentTypeException(documentType);
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate.isBefore(MINIMUM_BIRTH_DATE)) {
            throw new InvalidPatientDataException("La fecha de nacimiento no puede ser anterior a 1900-01-01");
        }
    }

    private String upper(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private String upperOrEmpty(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String lowerOrNull(String value) {
        String normalized = trimOrNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trim(String value) {
        return value.trim();
    }
}
