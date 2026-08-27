package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.common.error.ConflictException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.common.error.NotFoundException;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.repository.PatientRepository;
import com.medicalsys.pacientes.web.dto.FiscalCustomerResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.RegisterPatientRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del paciente.
 *
 * <p>La regla critica de este agregado es la unicidad del documento: la misma persona no puede
 * quedar registrada dos veces, porque cada duplicado partiria su historia clinica en dos.
 *
 * <p>La comprobacion previa da un 409 con mensaje util, pero la garantia final es la restriccion
 * {@code uq_patient_document_type_document_number_document_complement}: entre la consulta y el
 * INSERT puede colarse otra transaccion, y solo el motor cierra esa ventana.
 */
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patients;
    private final PatientMapper mapper;

    public PatientService(PatientRepository patients, PatientMapper mapper) {
        this.patients = patients;
        this.mapper = mapper;
    }

    /**
     * Registra un paciente nuevo.
     *
     * @throws ConflictException si la terna de documento ya pertenece a otro paciente
     */
    @Transactional
    public PatientResponse register(RegisterPatientRequest request) {
        String documentNumber = Patient.normalizeDocument(request.documentNumber());
        String complement = Patient.normalizeComplement(request.documentComplement());

        if (patients.existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
                request.documentType(), documentNumber, complement)) {
            throw documentConflict(request);
        }
        if (request.userAccountId() != null && patients.existsByUserAccountPublicId(request.userAccountId())) {
            throw new ConflictException(ErrorCode.PATIENT_USER_ACCOUNT_ALREADY_LINKED,
                    "Esa cuenta de usuario ya esta vinculada a otro paciente");
        }

        Patient patient = Patient.register(request.documentType(), documentNumber, complement,
                request.givenNames(), request.paternalSurname(), request.maternalSurname(),
                request.birthDate(), request.sex());
        patient.contactAt(request.phoneE164(), request.alternatePhone(), request.email(), request.city(),
                request.department());
        patient.emergencyContact(request.emergencyContactName(), request.emergencyContactPhone(),
                request.emergencyContactRelationship());
        patient.linkUserAccount(request.userAccountId());

        try {
            // flush inmediato: sin el, la violacion de unicidad saldria al cerrar la transaccion,
            // fuera de este bloque, y se traduciria como un 409 generico sin el codigo estable.
            patients.saveAndFlush(patient);
        } catch (DataIntegrityViolationException e) {
            throw documentConflict(request);
        }

        log.info("Paciente registrado patientId={} documento={}***", patient.getPublicId(),
                documentNumber.substring(0, Math.min(3, documentNumber.length())));
        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientResponse get(UUID patientId) {
        return mapper.toResponse(require(patientId));
    }

    /** Proyeccion fiscal minima consumida por ms-facturacion. */
    @Transactional(readOnly = true)
    public FiscalCustomerResponse getFiscalCustomer(UUID patientId) {
        return mapper.toFiscalCustomer(require(patientId));
    }

    /** Busqueda por nombre o documento para la pantalla de recepcion. */
    @Transactional(readOnly = true)
    public Page<PatientResponse> search(String term, Pageable pageable) {
        return patients.search(term, pageable).map(mapper::toResponse);
    }

    private Patient require(UUID patientId) {
        return patients.findByPublicId(patientId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PATIENT_NOT_FOUND,
                        "El paciente '" + patientId + "' no existe"));
    }

    /** El mensaje no repite el documento completo: es un dato identificable de una persona. */
    private ConflictException documentConflict(RegisterPatientRequest request) {
        return new ConflictException(ErrorCode.PATIENT_DOCUMENT_ALREADY_REGISTERED,
                "Ya existe un paciente registrado con ese " + request.documentType() + " y complemento");
    }
}
