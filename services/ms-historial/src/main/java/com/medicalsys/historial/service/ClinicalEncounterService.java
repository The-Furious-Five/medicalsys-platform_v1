package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.enums.EncounterType;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.EncounterAddendum;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.mapper.ClinicalEncounterMapper;
import com.medicalsys.historial.repository.ClinicalEncounterRepository;
import com.medicalsys.historial.repository.DiagnosisRepository;
import com.medicalsys.historial.repository.EncounterAddendumRepository;
import com.medicalsys.historial.web.dto.AddendumResponse;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.CreateAddendumRequest;
import com.medicalsys.historial.web.dto.EncounterSummaryResponse;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import com.medicalsys.historial.web.dto.SignEncounterRequest;
import com.medicalsys.historial.web.dto.UpdateNarrativeRequest;
import com.medicalsys.historial.web.dto.VoidEncounterRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del encuentro clinico.
 *
 * <p>Reglas centrales del agregado:
 * <ul>
 *   <li>La narrativa se cifra antes de persistir; la base nunca conserva texto en claro.</li>
 *   <li>Una cita produce como maximo un encuentro.</li>
 *   <li>Un encuentro firmado es inmutable; corregirlo exige un addendum.</li>
 * </ul>
 * La inmutabilidad se refuerza en la base con un trigger, de modo que un UPDATE directo tambien sea
 * rechazado. Esta capa existe para devolver un 422 con un mensaje util antes de llegar alli.
 *
 * <p>Los metodos publicos devuelven DTOs construidos dentro de la transaccion. Con
 * {@code open-in-view: false}, mapear en el controlador fallaria al tocar el expediente perezoso.
 */
@Service
public class ClinicalEncounterService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalEncounterService.class);

    private final ClinicalEncounterRepository repository;
    private final EncounterAddendumRepository addendumRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final MedicalRecordService medicalRecords;
    private final NarrativeCipher cipher;
    private final ContentHasher contentHasher;
    private final ClinicalEncounterMapper mapper;

    public ClinicalEncounterService(ClinicalEncounterRepository repository,
            EncounterAddendumRepository addendumRepository, DiagnosisRepository diagnosisRepository,
            MedicalRecordService medicalRecords, NarrativeCipher cipher, ContentHasher contentHasher,
            ClinicalEncounterMapper mapper) {
        this.repository = repository;
        this.addendumRepository = addendumRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.medicalRecords = medicalRecords;
        this.cipher = cipher;
        this.contentHasher = contentHasher;
        this.mapper = mapper;
    }

    /**
     * Abre un encuentro en borrador, recuperando o creando el expediente del paciente.
     *
     * @throws ConflictException si la cita ya produjo un encuentro
     */
    @Transactional
    public ClinicalEncounterResponse open(RequestContext context, OpenEncounterRequest request) {
        MedicalRecord record = medicalRecords.getOrCreateActiveRecord(context, request.patientId(),
                request.branchId());

        if (request.appointmentId() != null && repository.existsByAppointmentPublicId(request.appointmentId())) {
            throw new ConflictException(ErrorCode.ENCOUNTER_ALREADY_EXISTS_FOR_APPOINTMENT,
                    "La cita indicada ya tiene un encuentro clinico registrado");
        }

        Instant startsAt = request.startsAt() == null ? Instant.now() : DateTimes.toInstant(request.startsAt());
        EncounterType type = request.type() == null ? EncounterType.INITIAL_CONSULTATION : request.type();

        ClinicalEncounter encounter = repository.saveAndFlush(ClinicalEncounter.open(record,
                request.appointmentId(), request.professionalId(), request.branchId(), type, startsAt,
                cipher.open(context.organizationId()).keyPublicId()));
        log.info("Encuentro abierto encounterId={} recordId={} appointmentId={}", encounter.getPublicId(),
                record.getPublicId(), request.appointmentId());
        return toResponse(context, encounter);
    }

    /** Reemplaza la narrativa SOAP del encuentro, siempre cifrada. */
    @Transactional
    public ClinicalEncounterResponse updateNarrative(RequestContext context, UUID publicId,
            UpdateNarrativeRequest request) {
        ClinicalEncounter encounter = requireEditable(context, publicId);
        CipherSession session = cipher.open(context.organizationId());
        encounter.replaceNarrative(
                session.encrypt(request.chiefComplaint()),
                session.encrypt(request.subjective()),
                session.encrypt(request.objective()),
                session.encrypt(request.assessment()),
                session.encrypt(request.plan()),
                session.keyPublicId());
        encounter.updateSchedule(DateTimes.toInstant(request.endsAt()), request.suggestedNextAppointmentAt(),
                request.type());
        return toResponse(context, encounter);
    }

    /** Firma el encuentro y lo vuelve inmutable. */
    @Transactional
    public ClinicalEncounterResponse sign(RequestContext context, UUID publicId,
            SignEncounterRequest request) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        if (!encounter.getStatus().canTransitionTo(EncounterStatus.SIGNED)) {
            throw new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_SIGNABLE,
                    "Un encuentro en estado " + encounter.getStatus() + " no puede firmarse");
        }
        encounter.sign(contentHasher.hashEncounter(encounter), null,
                request == null ? null : request.certificateId());
        log.info("Encuentro firmado encounterId={} contentHash={}", encounter.getPublicId(),
                encounter.getContentHash());
        return toResponse(context, encounter);
    }

    /** Anula el encuentro conservando el registro, el motivo y el responsable. */
    @Transactional
    public ClinicalEncounterResponse voidEncounter(RequestContext context, UUID publicId,
            VoidEncounterRequest request) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        if (!encounter.getStatus().canTransitionTo(EncounterStatus.VOIDED)) {
            throw new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_EDITABLE,
                    "Un encuentro en estado " + encounter.getStatus() + " no puede anularse");
        }
        encounter.voidEncounter(context.userId(), request.reason());
        log.info("Encuentro anulado encounterId={}", encounter.getPublicId());
        return toResponse(context, encounter);
    }

    /**
     * Agrega una adicion inmutable sobre un encuentro firmado.
     *
     * <p>Es el unico camino de correccion: el encuentro original nunca se sobrescribe.
     */
    @Transactional
    public AddendumResponse addAddendum(RequestContext context, UUID publicId,
            CreateAddendumRequest request) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        if (!encounter.isSigned()) {
            throw new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_SIGNED,
                    "Solo un encuentro firmado admite addendums; edite el borrador en su lugar");
        }
        CipherSession session = cipher.open(context.organizationId());
        byte[] encryptedText = session.encrypt(request.text());
        EncounterAddendum addendum = addendumRepository.saveAndFlush(EncounterAddendum.append(encounter,
                request.professionalId(), request.reason(), encryptedText, session.keyPublicId(),
                contentHasher.hashCiphertext(encryptedText)));
        return mapper.toResponse(context, addendum);
    }

    @Transactional(readOnly = true)
    public List<AddendumResponse> listAddendums(RequestContext context, UUID publicId) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        return addendumRepository.findByClinicalEncounterIdOrderByCreatedAtAsc(encounter.getId()).stream()
                .map(addendum -> mapper.toResponse(context, addendum))
                .toList();
    }

    /** Devuelve el encuentro por su UUID publico dentro del tenant. */
    @Transactional(readOnly = true)
    public ClinicalEncounterResponse get(RequestContext context, UUID publicId) {
        return toResponse(context, requireByPublicId(context, publicId));
    }

    /** Proyeccion minima para otros microservicios. */
    @Transactional(readOnly = true)
    public EncounterSummaryResponse getSummary(RequestContext context, UUID publicId) {
        return mapper.toSummary(requireByPublicId(context, publicId));
    }

    /** Proyeccion minima del encuentro asociado a una cita; la consume ms-agenda. */
    @Transactional(readOnly = true)
    public EncounterSummaryResponse getSummaryByAppointment(RequestContext context, UUID appointmentId) {
        ClinicalEncounter encounter = repository
                .findByOrganizationPublicIdAndAppointmentPublicId(context.organizationId(), appointmentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CLINICAL_ENCOUNTER_NOT_FOUND,
                        "La cita indicada no tiene un encuentro registrado en esta organizacion"));
        return mapper.toSummary(encounter);
    }

    /** Busqueda paginada con filtros opcionales. */
    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> search(RequestContext context, UUID recordPublicId,
            UUID professionalId, EncounterStatus status, Instant from, Instant to, Pageable pageable) {
        Long recordId = recordPublicId == null ? null
                : medicalRecords.requireByPublicId(context, recordPublicId).getId();
        return repository.search(context.organizationId(), recordId, professionalId, status, from, to, pageable)
                .map(mapper::toSummary);
    }

    /**
     * Recupera el encuentro exigiendo que siga siendo editable.
     *
     * @throws BusinessRuleException si el encuentro ya fue firmado o anulado
     */
    ClinicalEncounter requireEditable(RequestContext context, UUID publicId) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        if (!encounter.isEditable()) {
            throw new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_EDITABLE,
                    "El encuentro esta en estado " + encounter.getStatus()
                            + " y no admite cambios; registre un addendum");
        }
        return encounter;
    }

    /**
     * Recupera el encuentro exigiendo que no este anulado.
     *
     * <p>Recetas y ordenes de estudio pueden emitirse mientras el encuentro sigue vivo, incluso
     * despues de firmarlo, porque no alteran su contenido sellado.
     */
    ClinicalEncounter requireNotVoided(RequestContext context, UUID publicId) {
        ClinicalEncounter encounter = requireByPublicId(context, publicId);
        if (encounter.getStatus() == EncounterStatus.VOIDED) {
            throw new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_EDITABLE,
                    "El encuentro esta anulado y no admite nuevos registros");
        }
        return encounter;
    }

    /** Recupera la entidad dentro del tenant; la usan los demas servicios del agregado. */
    ClinicalEncounter requireByPublicId(RequestContext context, UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(context.organizationId(), publicId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CLINICAL_ENCOUNTER_NOT_FOUND,
                        "El encuentro solicitado no existe o no pertenece a esta organizacion"));
    }

    private ClinicalEncounterResponse toResponse(RequestContext context, ClinicalEncounter encounter) {
        return mapper.toResponse(context, encounter,
                diagnosisRepository.findByClinicalEncounterIdOrderByPrimaryDescIdAsc(encounter.getId()));
    }
}
