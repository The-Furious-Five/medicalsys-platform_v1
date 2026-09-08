package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.common.DateTimes;
import com.medicalsys.pacientes.common.audit.AuditEventDraft;
import com.medicalsys.pacientes.common.audit.AuditTrail;
import com.medicalsys.pacientes.common.error.ConflictException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.common.error.NotFoundException;
import com.medicalsys.pacientes.common.error.ValidationException;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.repository.PatientRepository;
import com.medicalsys.pacientes.web.dto.BillingPatientSummary;
import com.medicalsys.pacientes.web.dto.FiscalCustomerResponse;
import com.medicalsys.pacientes.web.dto.NotificationContactSummary;
import com.medicalsys.pacientes.web.dto.PatientDemographicsResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.RegisterPatientRequest;
import com.medicalsys.pacientes.web.dto.SchedulingPatientSummary;
import com.medicalsys.pacientes.web.dto.UpdatePatientRequest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso del registro administrativo del paciente. */
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private static final int MAX_ENCRYPTED_ADDRESS_BYTES = 1024;

    private final PatientRepository patients;
    private final PatientMapper mapper;
    private final AuditTrail auditTrail;

    public PatientService(PatientRepository patients, PatientMapper mapper, AuditTrail auditTrail) {
        this.patients = patients;
        this.mapper = mapper;
        this.auditTrail = auditTrail;
    }

    /** La garantía final de unicidad vive también en MySQL para cubrir solicitudes concurrentes. */
    @Transactional
    public PatientResponse register(RegisterPatientRequest request) {
        String documentNumber = Patient.normalizeDocument(request.documentNumber());
        String complement = Patient.normalizeComplement(request.documentComplement());
        ensureDocumentAvailable(request.documentType(), documentNumber, complement);
        ensureUserAccountAvailable(request.userAccountId(), null);

        Patient patient = Patient.register(request.documentType(), documentNumber, complement);
        applyCreateData(patient, request);

        try {
            patients.saveAndFlush(patient);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception);
        }

        log.info("Paciente registrado patientId={} documento={}*** complemento={}", patient.getPublicId(),
                documentNumber.substring(0, Math.min(3, documentNumber.length())), complement);
        // El tipo de documento identifica la operacion sin revelar a quien pertenece; el numero se
        // queda fuera del evento a proposito, igual que se queda fuera del log de arriba.
        auditTrail.record(AuditEventDraft.patientWrite("PATIENT_REGISTERED", patient.getPublicId(), 201,
                Map.of("documentType", request.documentType().name())));
        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientResponse get(UUID patientId) {
        return mapper.toResponse(requireEntity(patientId));
    }

    @Transactional(readOnly = true)
    public PatientResponse getByDocument(DocumentType documentType, String documentNumber, String complement) {
        String normalizedNumber = Patient.normalizeDocument(documentNumber);
        String normalizedComplement = Patient.normalizeComplement(complement);
        Patient patient = patients.findByDocumentTypeAndDocumentNumberAndDocumentComplement(
                        documentType, normalizedNumber, normalizedComplement)
                .orElseThrow(() -> notFound("No existe un paciente con el documento indicado"));
        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> search(String term, Pageable pageable) {
        String normalized = term == null ? null : term.trim();
        return patients.search(normalized, pageable).map(mapper::toResponse);
    }

    @Transactional
    public PatientResponse update(UUID patientId, UpdatePatientRequest request) {
        Patient patient = requireEntity(patientId);
        ensureUserAccountAvailable(request.userAccountId(), patientId);

        Patient guardian = resolveGuardian(request.guardianPatientId(), patientId);
        try {
            patient.updatePersonalData(request.issuedDepartment(), request.givenNames(), request.paternalSurname(),
                    request.maternalSurname(), request.birthDate(), request.sex(), request.maritalStatus(),
                    request.bloodType(), request.occupation());
            patient.updateContactData(request.phoneE164(), request.alternatePhone(), request.email(),
                    decodeAddress(request.encryptedAddressBase64()), request.city(), request.department(),
                    request.emergencyContactName(), request.emergencyContactPhone(),
                    request.emergencyContactRelationship());
            patient.linkUserAccount(request.userAccountId());
            patient.assignGuardian(guardian);
            patient.configureEncryptionKey(request.encryptionKeyId());
            patient.recordDataConsent(DateTimes.toInstant(request.dataConsentAt()));
            patient.changeStatus(request.status(), request.deathDate());
            patients.saveAndFlush(patient);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception);
        }
        auditTrail.record(AuditEventDraft.patientWrite("PATIENT_UPDATED", patient.getPublicId(), 200,
                Map.of("status", patient.getStatus().name())));
        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public SchedulingPatientSummary getSchedulingSummary(UUID patientId) {
        return mapper.toSchedulingSummary(requireEntity(patientId));
    }

    @Transactional(readOnly = true)
    public NotificationContactSummary getNotificationContact(UUID patientId) {
        return mapper.toNotificationContact(requireEntity(patientId));
    }

    @Transactional(readOnly = true)
    public BillingPatientSummary getBillingSummary(UUID patientId) {
        return mapper.toBillingSummary(requireEntity(patientId));
    }

    /**
     * Proyeccion fiscal para ms-facturacion.
     *
     * <p>El complemento vacio viaja como null: la columna del comprobante distingue "sin
     * complemento" de "complemento en blanco", y la cadena vacia se imprimiria como un sufijo
     * inexistente en el numero de documento.
     */
    @Transactional(readOnly = true)
    public FiscalCustomerResponse getFiscalCustomer(UUID patientId) {
        Patient patient = requireEntity(patientId);
        String complement = patient.getDocumentComplement();
        return new FiscalCustomerResponse(
                patient.getPublicId(),
                patient.getDocumentType().name(),
                patient.getDocumentNumber(),
                complement == null || complement.isBlank() ? null : complement,
                patient.getFullName(),
                patient.getEmail());
    }

    /** Proyeccion demografica para ms-marketing; sin documento y sin nada clinico. */
    @Transactional(readOnly = true)
    public PatientDemographicsResponse getDemographics(UUID patientId) {
        Patient patient = requireEntity(patientId);
        return new PatientDemographicsResponse(
                patient.getPublicId(),
                patient.getFullName(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getCity(),
                patient.getPhoneE164(),
                patient.getStatus());
    }

    @Transactional(readOnly = true)
    public Patient requireEntity(UUID patientId) {
        return patients.findByPublicId(patientId)
                .orElseThrow(() -> notFound("El paciente solicitado no existe"));
    }

    private void applyCreateData(Patient patient, RegisterPatientRequest request) {
        try {
            patient.updatePersonalData(request.issuedDepartment(), request.givenNames(), request.paternalSurname(),
                    request.maternalSurname(), request.birthDate(), request.sex(), request.maritalStatus(),
                    request.bloodType(), request.occupation());
            patient.updateContactData(request.phoneE164(), request.alternatePhone(), request.email(),
                    decodeAddress(request.encryptedAddressBase64()), request.city(), request.department(),
                    request.emergencyContactName(), request.emergencyContactPhone(),
                    request.emergencyContactRelationship());
            patient.linkUserAccount(request.userAccountId());
            patient.assignGuardian(resolveGuardian(request.guardianPatientId(), patient.getPublicId()));
            patient.configureEncryptionKey(request.encryptionKeyId());
            patient.recordDataConsent(DateTimes.toInstant(request.dataConsentAt()));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }

    private Patient resolveGuardian(UUID guardianPatientId, UUID patientId) {
        if (guardianPatientId == null) {
            return null;
        }
        if (guardianPatientId.equals(patientId)) {
            throw new ValidationException("Un paciente no puede ser su propio tutor");
        }
        return requireEntity(guardianPatientId);
    }

    private byte[] decodeAddress(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length > MAX_ENCRYPTED_ADDRESS_BYTES) {
                throw new ValidationException("encryptedAddressBase64 supera los 1024 bytes permitidos");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("encryptedAddressBase64 no contiene Base64 valido");
        }
    }

    private void ensureDocumentAvailable(DocumentType documentType, String number, String complement) {
        if (patients.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(documentType, number, complement)) {
            throw documentConflict();
        }
    }

    private void ensureUserAccountAvailable(UUID userAccountId, UUID currentPatientId) {
        if (userAccountId == null) {
            return;
        }
        boolean occupied = currentPatientId == null
                ? patients.existsByUserAccountPublicId(userAccountId)
                : patients.existsByUserAccountPublicIdAndPublicIdNot(userAccountId, currentPatientId);
        if (occupied) {
            throw new ConflictException(ErrorCode.PATIENT_USER_ACCOUNT_ALREADY_LINKED,
                    "La cuenta de usuario ya esta vinculada a otro paciente");
        }
    }

    private RuntimeException translateIntegrityFailure(DataIntegrityViolationException exception) {
        String details = rootMessage(exception).toLowerCase();
        if (details.contains("uq_patient_user_account_public_id")) {
            return new ConflictException(ErrorCode.PATIENT_USER_ACCOUNT_ALREADY_LINKED,
                    "La cuenta de usuario ya esta vinculada a otro paciente");
        }
        if (details.contains("uq_patient_document") || details.contains("document_type")) {
            return documentConflict();
        }
        return new ConflictException(ErrorCode.DATA_INTEGRITY_CONFLICT,
                "Los datos del paciente violan una restriccion de integridad");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private ConflictException documentConflict() {
        return new ConflictException(ErrorCode.PATIENT_DOCUMENT_ALREADY_EXISTS,
                "Ya existe un paciente con el mismo tipo, numero y complemento de documento");
    }

    private NotFoundException notFound(String message) {
        return new NotFoundException(ErrorCode.PATIENT_NOT_FOUND, message);
    }
}
