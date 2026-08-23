package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.mapper.MedicalRecordMapper;
import com.medicalsys.historial.repository.MedicalRecordRepository;
import com.medicalsys.historial.web.dto.ChangeRecordStatusRequest;
import com.medicalsys.historial.web.dto.MedicalRecordResponse;
import com.medicalsys.historial.web.dto.MedicalRecordSummaryResponse;
import com.medicalsys.historial.web.dto.OpenMedicalRecordRequest;
import com.medicalsys.historial.web.dto.UpdateSharingRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del expediente clinico.
 *
 * <p>La apertura es idempotente por organizacion y paciente: es la garantia que exige el flujo de
 * atencion, donde recepcion y consultorio pueden pedir el expediente a la vez.
 */
@Service
public class MedicalRecordService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordService.class);

    private final MedicalRecordRepository repository;
    private final SequenceNumberGenerator sequenceNumbers;
    private final NarrativeCipher cipher;
    private final HistorialProperties properties;
    private final MedicalRecordMapper mapper;

    public MedicalRecordService(MedicalRecordRepository repository, SequenceNumberGenerator sequenceNumbers,
            NarrativeCipher cipher, HistorialProperties properties, MedicalRecordMapper mapper) {
        this.repository = repository;
        this.sequenceNumbers = sequenceNumbers;
        this.cipher = cipher;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Resultado de la apertura, que distingue creacion de recuperacion para elegir 201 o 200.
     *
     * @param record  expediente vigente
     * @param created true si esta llamada lo creo
     */
    public record OpenResult(MedicalRecordResponse record, boolean created) {
    }

    /**
     * Crea el expediente del paciente o devuelve el existente.
     *
     * <p>Ante una carrera entre dos solicitudes simultaneas, la restriccion UNIQUE de la base decide
     * y esta operacion recupera el expediente ganador en vez de propagar el fallo.
     */
    @Transactional
    public OpenResult openOrGet(RequestContext context, OpenMedicalRecordRequest request) {
        Entry entry = openOrGetEntity(context, request);
        return new OpenResult(mapper.toResponse(entry.record()), entry.created());
    }

    /** Variante interna que devuelve la entidad para el flujo del encuentro. */
    @Transactional
    Entry openOrGetEntity(RequestContext context, OpenMedicalRecordRequest request) {
        UUID organizationId = context.organizationId();
        return repository.findByOrganizationPublicIdAndPatientPublicId(organizationId, request.patientId())
                .map(existing -> new Entry(existing, false))
                .orElseGet(() -> create(context, request));
    }

    /** Par entidad-creacion usado dentro del servicio. */
    record Entry(MedicalRecord record, boolean created) {
    }

    private Entry create(RequestContext context, OpenMedicalRecordRequest request) {
        UUID organizationId = context.organizationId();
        UUID encryptionKeyId = cipher.open(organizationId).keyPublicId();
        String yearPrefix = sequenceNumbers.prefixForCurrentYear(properties.numbering().recordNumberPrefix());

        for (int attempt = 1; attempt <= properties.numbering().maxAttempts(); attempt++) {
            String recordNumber = sequenceNumbers.next(yearPrefix,
                    repository.findMaxRecordNumber(organizationId, yearPrefix));
            try {
                MedicalRecord record = repository.saveAndFlush(MedicalRecord.open(organizationId,
                        request.patientId(), recordNumber, request.branchId(), encryptionKeyId));
                log.info("Expediente abierto recordId={} patientId={}", record.getPublicId(),
                        record.getPatientPublicId());
                return new Entry(record, true);
            } catch (DataIntegrityViolationException e) {
                // Otra transaccion tomo el correlativo o creo el expediente del mismo paciente.
                var concurrent = repository.findByOrganizationPublicIdAndPatientPublicId(organizationId,
                        request.patientId());
                if (concurrent.isPresent()) {
                    return new Entry(concurrent.get(), false);
                }
                log.warn("Colision de correlativo de expediente en el intento {}", attempt);
            }
        }
        throw new ConflictException(ErrorCode.NUMBERING_CONFLICT,
                "No fue posible asignar un numero de expediente unico; reintente la operacion");
    }

    /** Devuelve el expediente por su UUID publico dentro del tenant. */
    @Transactional(readOnly = true)
    public MedicalRecordResponse getByPublicId(RequestContext context, UUID publicId) {
        return mapper.toResponse(requireByPublicId(context, publicId));
    }

    /** Proyeccion minima para otros microservicios. */
    @Transactional(readOnly = true)
    public MedicalRecordSummaryResponse getSummaryByPublicId(RequestContext context, UUID publicId) {
        return mapper.toSummary(requireByPublicId(context, publicId));
    }

    /** Devuelve el expediente de un paciente dentro del tenant. */
    @Transactional(readOnly = true)
    public MedicalRecordResponse getByPatient(RequestContext context, UUID patientId) {
        return mapper.toResponse(requireByPatient(context, patientId));
    }

    /** Proyeccion minima por paciente para otros microservicios. */
    @Transactional(readOnly = true)
    public MedicalRecordSummaryResponse getSummaryByPatient(RequestContext context, UUID patientId) {
        return mapper.toSummary(requireByPatient(context, patientId));
    }

    @Transactional(readOnly = true)
    public Page<MedicalRecordResponse> list(RequestContext context, Pageable pageable) {
        return repository.findByOrganizationPublicId(context.organizationId(), pageable)
                .map(mapper::toResponse);
    }

    @Transactional
    public MedicalRecordResponse updateSharing(RequestContext context, UUID publicId,
            UpdateSharingRequest request) {
        MedicalRecord record = requireByPublicId(context, publicId);
        record.updateNetworkSharing(Boolean.TRUE.equals(request.sharesWithNetwork()));
        return mapper.toResponse(record);
    }

    @Transactional
    public MedicalRecordResponse changeStatus(RequestContext context, UUID publicId,
            ChangeRecordStatusRequest request) {
        MedicalRecord record = requireByPublicId(context, publicId);
        record.changeStatus(request.status(), request.retentionUntil());
        return mapper.toResponse(record);
    }

    /** Recupera la entidad dentro del tenant; la usan los demas servicios del agregado. */
    @Transactional(readOnly = true)
    MedicalRecord requireByPublicId(RequestContext context, UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(context.organizationId(), publicId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEDICAL_RECORD_NOT_FOUND,
                        "El expediente solicitado no existe o no pertenece a esta organizacion"));
    }

    private MedicalRecord requireByPatient(RequestContext context, UUID patientId) {
        return repository.findByOrganizationPublicIdAndPatientPublicId(context.organizationId(), patientId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEDICAL_RECORD_NOT_FOUND,
                        "El paciente no tiene expediente abierto en esta organizacion"));
    }

    /**
     * Recupera el expediente activo del paciente creandolo si hace falta.
     *
     * <p>Lo usa la apertura de un encuentro: atender no debe fallar porque nadie abrio el expediente
     * antes.
     */
    @Transactional
    MedicalRecord getOrCreateActiveRecord(RequestContext context, UUID patientId, UUID branchId) {
        MedicalRecord record = openOrGetEntity(context, new OpenMedicalRecordRequest(patientId, branchId))
                .record();
        if (!record.isActive()) {
            throw new BusinessRuleException(ErrorCode.MEDICAL_RECORD_NOT_ACTIVE,
                    "El expediente esta en estado " + record.getStatus() + " y no admite nuevos registros");
        }
        return record;
    }
}
