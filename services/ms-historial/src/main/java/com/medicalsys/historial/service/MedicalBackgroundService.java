package com.medicalsys.historial.service;

import com.medicalsys.historial.client.CatalogsClient;
import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.BackgroundType;
import com.medicalsys.historial.domain.model.MedicalBackground;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.mapper.MedicalRecordMapper;
import com.medicalsys.historial.repository.MedicalBackgroundRepository;
import com.medicalsys.historial.web.dto.CreateMedicalBackgroundRequest;
import com.medicalsys.historial.web.dto.MedicalBackgroundResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de antecedentes del expediente. */
@Service
public class MedicalBackgroundService {

    private final MedicalBackgroundRepository repository;
    private final MedicalRecordService medicalRecords;
    private final CatalogsClient catalogs;
    private final NarrativeCipher cipher;
    private final MedicalRecordMapper mapper;

    public MedicalBackgroundService(MedicalBackgroundRepository repository, MedicalRecordService medicalRecords,
            CatalogsClient catalogs, NarrativeCipher cipher, MedicalRecordMapper mapper) {
        this.repository = repository;
        this.medicalRecords = medicalRecords;
        this.catalogs = catalogs;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    /** Registra un antecedente cifrando su detalle antes de persistirlo. */
    @Transactional
    public MedicalBackgroundResponse create(RequestContext context, UUID recordPublicId,
            CreateMedicalBackgroundRequest request) {
        MedicalRecord record = medicalRecords.requireByPublicId(context, recordPublicId);
        if (request.icd10Code() != null && !request.icd10Code().isBlank()) {
            catalogs.requireIcd10Code(request.icd10Code());
        }
        byte[] encryptedDetail = cipher.open(context.organizationId()).encrypt(request.detail());
        MedicalBackground background = repository.saveAndFlush(MedicalBackground.record(record, request.type(),
                request.title(), encryptedDetail, request.icd10Code(), request.eventDate(), context.userId()));
        return mapper.toResponse(context, background);
    }

    @Transactional(readOnly = true)
    public List<MedicalBackgroundResponse> list(RequestContext context, UUID recordPublicId,
            BackgroundType type) {
        MedicalRecord record = medicalRecords.requireByPublicId(context, recordPublicId);
        List<MedicalBackground> backgrounds = type == null
                ? repository.findByMedicalRecordIdOrderByRecordedAtDesc(record.getId())
                : repository.findByMedicalRecordIdAndTypeOrderByRecordedAtDesc(record.getId(), type);
        return backgrounds.stream().map(background -> mapper.toResponse(context, background)).toList();
    }

    /** Marca el antecedente como no vigente conservando su historia. */
    @Transactional
    public void deactivate(RequestContext context, Long backgroundId) {
        MedicalBackground background = repository
                .findByIdAndMedicalRecordOrganizationPublicId(backgroundId, context.organizationId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEDICAL_BACKGROUND_NOT_FOUND,
                        "El antecedente solicitado no existe o no pertenece a esta organizacion"));
        background.deactivate();
    }
}
