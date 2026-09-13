package com.medicalsys.historial.service;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.Diagnosis;
import com.medicalsys.historial.mapper.ClinicalEncounterMapper;
import com.medicalsys.historial.repository.DiagnosisRepository;
import com.medicalsys.historial.web.dto.CreateDiagnosisRequest;
import com.medicalsys.historial.web.dto.DiagnosisResponse;
import com.medicalsys.historial.web.dto.UpdateDiagnosisRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de diagnosticos.
 *
 * <p>El codigo CIE-10 se valida contra ms-catalogos antes de persistir: un codigo inexistente se
 * rechaza y no llega nunca a la base.
 */
@Service
public class DiagnosisService {

    private final DiagnosisRepository repository;
    private final ClinicalEncounterService encounters;
    private final CatalogsClient catalogs;
    private final NarrativeCipher cipher;
    private final ClinicalEncounterMapper mapper;

    public DiagnosisService(DiagnosisRepository repository, ClinicalEncounterService encounters,
            CatalogsClient catalogs, NarrativeCipher cipher, ClinicalEncounterMapper mapper) {
        this.repository = repository;
        this.encounters = encounters;
        this.catalogs = catalogs;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    /** Registra un diagnostico en el encuentro. */
    @Transactional
    public DiagnosisResponse create(RequestContext context, UUID encounterPublicId,
            CreateDiagnosisRequest request) {
        ClinicalEncounter encounter = encounters.requireEditable(context, encounterPublicId);
        String icd10Code = request.icd10Code().trim().toUpperCase(Locale.ROOT);
        catalogs.requireIcd10Code(icd10Code);

        if (repository.existsByClinicalEncounterIdAndIcd10Code(encounter.getId(), icd10Code)) {
            throw new ConflictException(ErrorCode.DIAGNOSIS_ALREADY_REGISTERED,
                    "El diagnostico " + icd10Code + " ya esta registrado en este encuentro");
        }

        boolean primary = Boolean.TRUE.equals(request.primary());
        if (primary) {
            // Un encuentro tiene un unico diagnostico principal: el nuevo desplaza al anterior.
            repository.findByClinicalEncounterIdAndPrimaryTrue(encounter.getId())
                    .forEach(Diagnosis::demoteFromPrimary);
        }

        byte[] observation = cipher.open(context.organizationId()).encrypt(request.observation());
        Diagnosis diagnosis = repository.saveAndFlush(Diagnosis.record(encounter, icd10Code, request.type(),
                primary, Boolean.TRUE.equals(request.chronic()), request.laterality(), observation));
        return mapper.toResponse(context, diagnosis);
    }

    @Transactional(readOnly = true)
    public List<DiagnosisResponse> list(RequestContext context, UUID encounterPublicId) {
        ClinicalEncounter encounter = encounters.requireByPublicId(context, encounterPublicId);
        return repository.findByClinicalEncounterIdOrderByPrimaryDescIdAsc(encounter.getId()).stream()
                .map(diagnosis -> mapper.toResponse(context, diagnosis))
                .toList();
    }

    @Transactional
    public DiagnosisResponse update(RequestContext context, Long diagnosisId,
            UpdateDiagnosisRequest request) {
        Diagnosis diagnosis = repository
                .findByIdAndClinicalEncounterOrganizationPublicId(diagnosisId, context.organizationId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "El diagnostico solicitado no existe o no pertenece a esta organizacion"));
        diagnosis.evolve(request.type(), request.status());
        return mapper.toResponse(context, diagnosis);
    }
}
