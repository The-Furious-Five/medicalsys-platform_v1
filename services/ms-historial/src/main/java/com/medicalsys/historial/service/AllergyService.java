package com.medicalsys.historial.service;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.common.error.ConflictException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.model.Allergy;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.mapper.MedicalRecordMapper;
import com.medicalsys.historial.repository.AllergyRepository;
import com.medicalsys.historial.web.dto.AllergyResponse;
import com.medicalsys.historial.web.dto.CreateAllergyRequest;
import com.medicalsys.historial.web.dto.UpdateAllergyRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de alergias del expediente.
 *
 * <p>Como el resto de los servicios, devuelve DTOs ya construidos dentro de la transaccion: con
 * {@code open-in-view: false} mapear fuera de ella romperia al tocar una asociacion perezosa.
 */
@Service
public class AllergyService {

    private final AllergyRepository repository;
    private final MedicalRecordService medicalRecords;
    private final CatalogsClient catalogs;
    private final MedicalRecordMapper mapper;

    public AllergyService(AllergyRepository repository, MedicalRecordService medicalRecords,
            CatalogsClient catalogs, MedicalRecordMapper mapper) {
        this.repository = repository;
        this.medicalRecords = medicalRecords;
        this.catalogs = catalogs;
        this.mapper = mapper;
    }

    /**
     * Registra una alergia.
     *
     * <p>La pareja tipo-sustancia es unica dentro del expediente: registrar dos veces la misma
     * alergia es un conflicto, no un duplicado silencioso.
     */
    @Transactional
    public AllergyResponse create(RequestContext context, UUID recordPublicId, CreateAllergyRequest request) {
        MedicalRecord record = medicalRecords.requireByPublicId(context, recordPublicId);
        if (request.medicationCode() != null && !request.medicationCode().isBlank()) {
            catalogs.requireMedicationCode(request.medicationCode());
        }
        if (repository.existsByMedicalRecordIdAndTypeAndSubstanceIgnoreCase(record.getId(), request.type(),
                request.substance())) {
            throw new ConflictException(ErrorCode.ALLERGY_ALREADY_REGISTERED,
                    "La alergia ya esta registrada en este expediente");
        }
        Allergy allergy = repository.saveAndFlush(Allergy.record(record, request.type(), request.substance(),
                request.medicationCode(), request.reaction(), request.severity(), context.userId()));
        return mapper.toResponse(allergy);
    }

    @Transactional(readOnly = true)
    public List<AllergyResponse> list(RequestContext context, UUID recordPublicId) {
        MedicalRecord record = medicalRecords.requireByPublicId(context, recordPublicId);
        return repository.findByMedicalRecordIdOrderByRecordedAtDesc(record.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public AllergyResponse update(RequestContext context, Long allergyId, UpdateAllergyRequest request) {
        Allergy allergy = repository.findByIdAndMedicalRecordOrganizationPublicId(allergyId,
                        context.organizationId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ALLERGY_NOT_FOUND,
                        "La alergia solicitada no existe o no pertenece a esta organizacion"));
        allergy.reassess(request.severity(), request.reaction(), request.status());
        return mapper.toResponse(allergy);
    }
}
