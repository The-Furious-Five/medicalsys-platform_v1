package com.medicalsys.historial.service;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.Prescription;
import com.medicalsys.historial.mapper.PrescriptionMapper;
import com.medicalsys.historial.repository.PrescriptionRepository;
import com.medicalsys.historial.web.dto.AttachDocumentRequest;
import com.medicalsys.historial.web.dto.ChangePrescriptionStatusRequest;
import com.medicalsys.historial.web.dto.CreatePrescriptionRequest;
import com.medicalsys.historial.web.dto.PrescriptionItemRequest;
import com.medicalsys.historial.web.dto.PrescriptionResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de recetas emitidas durante un encuentro. */
@Service
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    private final PrescriptionRepository repository;
    private final ClinicalEncounterService encounters;
    private final CatalogsClient catalogs;
    private final NarrativeCipher cipher;
    private final SequenceNumberGenerator sequenceNumbers;
    private final HistorialProperties properties;
    private final PrescriptionMapper mapper;

    public PrescriptionService(PrescriptionRepository repository, ClinicalEncounterService encounters,
            CatalogsClient catalogs, NarrativeCipher cipher, SequenceNumberGenerator sequenceNumbers,
            HistorialProperties properties, PrescriptionMapper mapper) {
        this.repository = repository;
        this.encounters = encounters;
        this.catalogs = catalogs;
        this.cipher = cipher;
        this.sequenceNumbers = sequenceNumbers;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Emite una receta con sus detalles.
     *
     * <p>Los codigos de medicamento se validan contra ms-catalogos; un preparado magistral viaja como
     * descripcion libre.
     */
    @Transactional
    public PrescriptionResponse create(RequestContext context, UUID encounterPublicId,
            CreatePrescriptionRequest request) {
        ClinicalEncounter encounter = encounters.requireNotVoided(context, encounterPublicId);
        validateItems(request.items());

        CipherSession session = cipher.open(context.organizationId());
        String yearPrefix = sequenceNumbers.prefixForCurrentYear(properties.numbering().prescriptionFolioPrefix());

        for (int attempt = 1; attempt <= properties.numbering().maxAttempts(); attempt++) {
            String folio = sequenceNumbers.next(yearPrefix,
                    repository.findMaxFolio(context.organizationId(), yearPrefix));
            Prescription prescription = Prescription.issue(encounter, folio, request.validityDays(),
                    session.encrypt(request.generalInstructions()));
            request.items().forEach(item -> prescription.addItem(
                    blankToNull(item.medicationCode()),
                    blankToNull(item.freeTextDescription()),
                    item.dose(),
                    item.administrationRoute(),
                    item.frequencyHours(),
                    item.durationDays(),
                    item.quantity(),
                    item.unit(),
                    session.encrypt(item.instructions())));
            try {
                Prescription saved = repository.saveAndFlush(prescription);
                log.info("Receta emitida prescriptionId={} folio={} encounterId={}", saved.getPublicId(),
                        saved.getFolio(), encounter.getPublicId());
                return mapper.toResponse(context, saved);
            } catch (DataIntegrityViolationException e) {
                log.warn("Colision de folio de receta en el intento {}", attempt);
            }
        }
        throw new ConflictException(ErrorCode.NUMBERING_CONFLICT,
                "No fue posible asignar un folio de receta unico; reintente la operacion");
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse get(RequestContext context, UUID publicId) {
        return mapper.toResponse(context, requireByPublicId(context, publicId));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listByEncounter(RequestContext context, UUID encounterPublicId) {
        ClinicalEncounter encounter = encounters.requireByPublicId(context, encounterPublicId);
        return repository.findByClinicalEncounterIdOrderByIssuedAtDesc(encounter.getId()).stream()
                .map(prescription -> mapper.toResponse(context, prescription))
                .toList();
    }

    /** Cambia el estado de la receta respetando su ciclo de vida. */
    @Transactional
    public PrescriptionResponse changeStatus(RequestContext context, UUID publicId,
            ChangePrescriptionStatusRequest request) {
        Prescription prescription = requireByPublicId(context, publicId);
        try {
            prescription.changeStatus(request.status());
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(ErrorCode.PRESCRIPTION_TRANSITION_NOT_ALLOWED, e.getMessage());
        }
        return mapper.toResponse(context, prescription);
    }

    /** Vincula el PDF generado por ms-documental; el binario nunca vive aqui. */
    @Transactional
    public PrescriptionResponse attachDocument(RequestContext context, UUID publicId,
            AttachDocumentRequest request) {
        Prescription prescription = requireByPublicId(context, publicId);
        prescription.attachDocument(request.documentId(), request.sha256Hash(), request.certificateId());
        return mapper.toResponse(context, prescription);
    }

    private Prescription requireByPublicId(RequestContext context, UUID publicId) {
        return repository.findByOrganizationPublicIdAndPublicId(context.organizationId(), publicId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRESCRIPTION_NOT_FOUND,
                        "La receta solicitada no existe o no pertenece a esta organizacion"));
    }

    private void validateItems(List<PrescriptionItemRequest> items) {
        for (PrescriptionItemRequest item : items) {
            String medicationCode = blankToNull(item.medicationCode());
            String freeText = blankToNull(item.freeTextDescription());
            if (medicationCode == null && freeText == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                        "Cada item debe indicar medicationCode o freeTextDescription");
            }
            if (medicationCode != null) {
                catalogs.requireMedicationCode(medicationCode);
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
