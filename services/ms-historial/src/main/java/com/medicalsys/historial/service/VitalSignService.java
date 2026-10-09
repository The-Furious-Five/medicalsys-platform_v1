package com.medicalsys.historial.service;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.VitalSign;
import com.medicalsys.historial.mapper.ClinicalEncounterMapper;
import com.medicalsys.historial.repository.VitalSignRepository;
import com.medicalsys.historial.web.dto.CreateVitalSignRequest;
import com.medicalsys.historial.web.dto.VitalSignResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de signos vitales del encuentro. */
@Service
public class VitalSignService {

    private final VitalSignRepository repository;
    private final ClinicalEncounterService encounters;
    private final ClinicalEncounterMapper mapper;

    public VitalSignService(VitalSignRepository repository, ClinicalEncounterService encounters,
            ClinicalEncounterMapper mapper) {
        this.repository = repository;
        this.encounters = encounters;
        this.mapper = mapper;
    }

    /**
     * Registra una medicion sobre un encuentro en borrador.
     *
     * <p>Se fuerza el {@code flush} para releer el indice de masa corporal, que calcula la base.
     */
    @Transactional
    public VitalSignResponse create(RequestContext context, UUID encounterPublicId,
            CreateVitalSignRequest request) {
        ClinicalEncounter encounter = encounters.requireEditable(context, encounterPublicId);
        Instant measuredAt = request.measuredAt() == null ? Instant.now()
                : DateTimes.toInstant(request.measuredAt());
        VitalSign vitalSign = VitalSign.builder(encounter, measuredAt)
                .pressure(request.systolicPressure(), request.diastolicPressure())
                .rates(request.heartRate(), request.respiratoryRate())
                .temperature(request.temperatureC())
                .oxygenSaturation(request.oxygenSaturation())
                .bloodGlucose(request.bloodGlucoseMgDl())
                .anthropometry(request.weightKg(), request.heightCm(), request.abdominalCircumferenceCm())
                .painScale(request.painScale())
                .recordedBy(context.userId())
                .build();
        return mapper.toResponse(repository.saveAndFlush(vitalSign));
    }

    @Transactional(readOnly = true)
    public List<VitalSignResponse> list(RequestContext context, UUID encounterPublicId) {
        ClinicalEncounter encounter = encounters.requireByPublicId(context, encounterPublicId);
        return repository.findByClinicalEncounterIdOrderByMeasuredAtDesc(encounter.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
