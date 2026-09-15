package com.medicalsys.historial.service;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.client.PatientAccessClient;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyResultStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.StudyOrder;
import com.medicalsys.historial.domain.model.StudyResult;
import com.medicalsys.historial.mapper.StudyOrderMapper;
import com.medicalsys.historial.repository.StudyOrderRepository;
import com.medicalsys.historial.repository.StudyResultRepository;
import com.medicalsys.historial.web.dto.ChangeStudyOrderStatusRequest;
import com.medicalsys.historial.web.dto.CreateStudyOrderRequest;
import com.medicalsys.historial.web.dto.RegisterStudyResultRequest;
import com.medicalsys.historial.web.dto.ScheduleStudyOrderRequest;
import com.medicalsys.historial.web.dto.StudyOrderResponse;
import com.medicalsys.historial.web.dto.StudyOrderSummaryResponse;
import com.medicalsys.historial.web.dto.StudyResultResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ordenes de estudio y de sus resultados.
 *
 * <p>La orden nace en estado {@code REQUESTED} y su UUID publico es el orderId que consumen
 * ms-agenda, para agendar la toma, y ms-documental, para vincular el archivo. Al registrar un
 * resultado la orden pasa a {@code REPORTED}: ese es el estado "disponible" del guion de la demo.
 *
 * <p>ms-historial nunca guarda el binario del estudio: solo la referencia {@code documentId}.
 */
@Service
public class StudyOrderService {

    private static final Logger log = LoggerFactory.getLogger(StudyOrderService.class);

    private final StudyOrderRepository repository;
    private final StudyResultRepository resultRepository;
    private final ClinicalEncounterService encounters;
    private final CatalogsClient catalogs;
    private final NarrativeCipher cipher;
    private final StudyOrderMapper mapper;
    private final PatientAccessClient patientAccess;

    public StudyOrderService(StudyOrderRepository repository, StudyResultRepository resultRepository,
            ClinicalEncounterService encounters, CatalogsClient catalogs, NarrativeCipher cipher,
            StudyOrderMapper mapper, PatientAccessClient patientAccess) {
        this.repository = repository;
        this.resultRepository = resultRepository;
        this.encounters = encounters;
        this.catalogs = catalogs;
        this.cipher = cipher;
        this.mapper = mapper;
        this.patientAccess = patientAccess;
    }

    /** Crea una orden de estudio a partir del encuentro. */
    @Transactional
    public StudyOrderResponse create(RequestContext context, UUID encounterPublicId,
            CreateStudyOrderRequest request) {
        ClinicalEncounter encounter = encounters.requireNotVoided(context, encounterPublicId);
        String studyTypeCode = request.studyTypeCode().trim().toUpperCase(Locale.ROOT);
        catalogs.requireStudyTypeCode(studyTypeCode);

        String icd10Code = null;
        if (request.icd10Code() != null && !request.icd10Code().isBlank()) {
            icd10Code = request.icd10Code().trim().toUpperCase(Locale.ROOT);
            catalogs.requireIcd10Code(icd10Code);
        }

        StudyOrder order = repository.saveAndFlush(StudyOrder.request(encounter, studyTypeCode,
                request.priority(), request.instructions(), icd10Code,
                encounter.getHealthProfessionalPublicId()));
        log.info("Orden de estudio creada orderId={} studyType={} encounterId={} recordId={}",
                order.getPublicId(), studyTypeCode, encounter.getPublicId(),
                encounter.getMedicalRecord().getPublicId());
        return mapper.toResponse(context, order, List.of());
    }

    /** Dentro del tenant, o de otro si el paciente autorizo el acceso (ver {@link PatientAccessClient}). */
    @Transactional(readOnly = true)
    public StudyOrderResponse get(RequestContext context, UUID publicId) {
        StudyOrder order = requireReadableByPublicId(context, publicId);
        return mapper.toResponse(context, order, results(order));
    }

    /** Proyeccion minima para ms-agenda y ms-documental; misma regla de lectura cruzada que arriba. */
    @Transactional(readOnly = true)
    public StudyOrderSummaryResponse getSummary(RequestContext context, UUID publicId) {
        return mapper.toSummary(requireReadableByPublicId(context, publicId));
    }

    @Transactional(readOnly = true)
    public List<StudyOrderResponse> listByEncounter(RequestContext context, UUID encounterPublicId) {
        ClinicalEncounter encounter = encounters.requireByPublicId(context, encounterPublicId);
        return repository.findByClinicalEncounterIdOrderByRequestedAtDesc(encounter.getId()).stream()
                .map(order -> mapper.toResponse(context, order, results(order)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StudyOrderResponse> search(RequestContext context, StudyOrderStatus status,
            String studyTypeCode, UUID patientId, Pageable pageable) {
        String normalized = studyTypeCode == null || studyTypeCode.isBlank() ? null
                : studyTypeCode.trim().toUpperCase(Locale.ROOT);
        return repository.search(context.organizationId(), status, normalized, patientId, pageable)
                .map(order -> mapper.toResponse(context, order, results(order)));
    }

    /** Vincula la cita de toma creada por ms-agenda y deja la orden agendada. */
    @Transactional
    public StudyOrderResponse scheduleCollection(RequestContext context, UUID publicId,
            ScheduleStudyOrderRequest request) {
        StudyOrder order = requireByPublicId(context, publicId);
        try {
            order.scheduleCollection(request.collectionAppointmentId());
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(ErrorCode.STUDY_ORDER_TRANSITION_NOT_ALLOWED, e.getMessage());
        }
        return mapper.toResponse(context, order, results(order));
    }

    /** Aplica una transicion explicita de estado. */
    @Transactional
    public StudyOrderResponse changeStatus(RequestContext context, UUID publicId,
            ChangeStudyOrderStatusRequest request) {
        StudyOrder order = requireByPublicId(context, publicId);
        try {
            order.changeStatus(request.status());
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(ErrorCode.STUDY_ORDER_TRANSITION_NOT_ALLOWED, e.getMessage());
        }
        return mapper.toResponse(context, order, results(order));
    }

    /**
     * Registra el resultado de un estudio y deja la orden informada.
     *
     * <p>El informe narrativo se cifra; del archivo solo se conserva su UUID en ms-documental. El
     * parametro es unico dentro de la orden: reinformar el mismo parametro es un conflicto.
     */
    @Transactional
    public StudyResultResponse registerResult(RequestContext context, UUID orderPublicId,
            RegisterStudyResultRequest request) {
        StudyOrder order = requireByPublicId(context, orderPublicId);
        if (!order.acceptsResults()) {
            throw new BusinessRuleException(ErrorCode.STUDY_ORDER_TRANSITION_NOT_ALLOWED,
                    "La orden esta anulada y no admite resultados");
        }

        String parameter = request.parameter() == null || request.parameter().isBlank()
                ? StudyResult.DEFAULT_PARAMETER
                : request.parameter().trim();
        if (resultRepository.existsByStudyOrderIdAndParameter(order.getId(), parameter)) {
            throw new ConflictException(ErrorCode.STUDY_RESULT_ALREADY_REGISTERED,
                    "El parametro " + parameter + " ya tiene resultado en esta orden");
        }

        CipherSession session = cipher.open(context.organizationId());
        byte[] encryptedText = session.encrypt(request.textValue());
        StudyResultStatus status = request.status() == null ? StudyResultStatus.FINAL : request.status();

        StudyResult result = resultRepository.saveAndFlush(StudyResult.report(order, parameter,
                request.numericValue(), encryptedText, request.unit(), request.referenceMin(),
                request.referenceMax(), status, request.documentId(), request.reportedById(),
                encryptedText == null ? null : session.keyPublicId()));

        order.markReported();
        log.info("Resultado registrado orderId={} parameter={} documentId={} status={}", order.getPublicId(),
                parameter, request.documentId(), order.getStatus());
        return mapper.toResponse(context, result);
    }

    @Transactional(readOnly = true)
    public List<StudyResultResponse> listResults(RequestContext context, UUID orderPublicId) {
        StudyOrder order = requireByPublicId(context, orderPublicId);
        return results(order).stream().map(result -> mapper.toResponse(context, result)).toList();
    }

    private StudyOrder requireByPublicId(RequestContext context, UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(context.organizationId(), publicId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STUDY_ORDER_NOT_FOUND,
                        "La orden solicitada no existe o no pertenece a esta organizacion"));
    }

    private StudyOrder requireReadableByPublicId(RequestContext context, UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(context.organizationId(), publicId)
                .or(() -> repository.findByPublicId(publicId)
                        .filter(order -> patientAccess.hasAccess(
                                order.getClinicalEncounter().getMedicalRecord().getPatientPublicId(),
                                context.organizationId())))
                .orElseThrow(() -> new NotFoundException(ErrorCode.STUDY_ORDER_NOT_FOUND,
                        "La orden solicitada no existe o no pertenece a esta organizacion"));
    }

    private List<StudyResult> results(StudyOrder order) {
        return resultRepository.findByStudyOrderIdOrderByParameterAsc(order.getId());
    }
}
