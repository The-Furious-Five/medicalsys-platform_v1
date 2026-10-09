package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.client.NarrativeCipher.CipherSession;
import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.PerformedProcedure;
import com.medicalsys.historial.mapper.ClinicalEncounterMapper;
import com.medicalsys.historial.repository.PerformedProcedureRepository;
import com.medicalsys.historial.web.dto.CreateProcedureRequest;
import com.medicalsys.historial.web.dto.PerformedProcedureResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de procedimientos realizados durante el encuentro. */
@Service
public class PerformedProcedureService {

    private final PerformedProcedureRepository repository;
    private final ClinicalEncounterService encounters;
    private final NarrativeCipher cipher;
    private final ClinicalEncounterMapper mapper;

    public PerformedProcedureService(PerformedProcedureRepository repository,
            ClinicalEncounterService encounters, NarrativeCipher cipher, ClinicalEncounterMapper mapper) {
        this.repository = repository;
        this.encounters = encounters;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    /** Registra un procedimiento cifrando su descripcion y sus complicaciones. */
    @Transactional
    public PerformedProcedureResponse create(RequestContext context, UUID encounterPublicId,
            CreateProcedureRequest request) {
        ClinicalEncounter encounter = encounters.requireEditable(context, encounterPublicId);
        CipherSession session = cipher.open(context.organizationId());
        PerformedProcedure procedure = repository.saveAndFlush(PerformedProcedure.record(encounter,
                request.serviceOfferingId(), request.physicalResourceId(), request.responsibleId(),
                DateTimes.toInstant(request.startsAt()), DateTimes.toInstant(request.endsAt()),
                request.anesthesiaType(), session.encrypt(request.description()),
                session.encrypt(request.complications()), request.informedConsentId(), request.status()));
        return mapper.toResponse(context, procedure);
    }

    @Transactional(readOnly = true)
    public List<PerformedProcedureResponse> list(RequestContext context, UUID encounterPublicId) {
        ClinicalEncounter encounter = encounters.requireByPublicId(context, encounterPublicId);
        return repository.findByClinicalEncounterIdOrderByStartsAtAsc(encounter.getId()).stream()
                .map(procedure -> mapper.toResponse(context, procedure))
                .toList();
    }
}
