package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.common.error.BusinessRuleException;
import com.medicalsys.pacientes.common.error.ConflictException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientMerge;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.repository.PatientMergeRepository;
import com.medicalsys.pacientes.web.dto.MergePatientRequest;
import com.medicalsys.pacientes.web.dto.PatientMergeResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unifica identidades duplicadas y conserva una evidencia idempotente de la operación. */
@Service
public class PatientMergeService {

    private final PatientMergeRepository merges;
    private final PatientService patients;
    private final PatientMapper mapper;

    public PatientMergeService(PatientMergeRepository merges, PatientService patients, PatientMapper mapper) {
        this.merges = merges;
        this.patients = patients;
        this.mapper = mapper;
    }

    @Transactional
    public PatientMergeResponse merge(UUID survivingPatientId, MergePatientRequest request, UUID performedByUserId) {
        PatientMerge previousEvent = merges.findByEventPublicId(request.eventId()).orElse(null);
        if (previousEvent != null) {
            if (sameOperation(previousEvent, survivingPatientId, request.mergedPatientId())) {
                return mapper.toMergeResponse(previousEvent);
            }
            throw new ConflictException(ErrorCode.MERGE_EVENT_ALREADY_USED,
                    "El eventId ya fue utilizado por otra fusion de pacientes");
        }
        if (survivingPatientId.equals(request.mergedPatientId())) {
            throw new BusinessRuleException(ErrorCode.INVALID_PATIENT_MERGE,
                    "El paciente superviviente y el fusionado deben ser diferentes");
        }
        if (merges.existsByMergedPatient_PublicId(request.mergedPatientId())) {
            throw new ConflictException(ErrorCode.PATIENT_ALREADY_MERGED,
                    "El paciente indicado ya fue fusionado anteriormente");
        }

        Patient surviving = patients.requireEntity(survivingPatientId);
        Patient merged = patients.requireEntity(request.mergedPatientId());
        PatientMerge merge;
        try {
            merge = PatientMerge.create(surviving, merged, request.reason(), performedByUserId, request.eventId());
            merged.markInactiveAfterMerge();
            merge = merges.saveAndFlush(merge);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(ErrorCode.PATIENT_ALREADY_MERGED,
                    "La fusion ya fue registrada por otra solicitud concurrente");
        }
        return mapper.toMergeResponse(merge);
    }

    @Transactional(readOnly = true)
    public List<PatientMergeResponse> history(UUID survivingPatientId) {
        patients.requireEntity(survivingPatientId);
        return merges.findAllBySurvivingPatient_PublicIdOrderByMergedAtDesc(survivingPatientId).stream()
                .map(mapper::toMergeResponse)
                .toList();
    }

    private boolean sameOperation(PatientMerge merge, UUID survivingPatientId, UUID mergedPatientId) {
        return merge.getSurvivingPatient().getPublicId().equals(survivingPatientId)
                && merge.getMergedPatient().getPublicId().equals(mergedPatientId);
    }
}
