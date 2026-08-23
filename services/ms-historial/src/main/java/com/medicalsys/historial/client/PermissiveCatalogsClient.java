package com.medicalsys.historial.client;

import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Valida unicamente el formato del codigo.
 *
 * <p>Solo para depuracion aislada: no garantiza que el codigo exista en ms-catalogos.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.catalogs", name = "validation-mode", havingValue = "PERMISSIVE")
public class PermissiveCatalogsClient implements CatalogsClient {

    private static final Logger log = LoggerFactory.getLogger(PermissiveCatalogsClient.class);

    public PermissiveCatalogsClient() {
        log.warn("Los codigos de catalogo no se contrastan contra ms-catalogos "
                + "(CATALOGS_VALIDATION_MODE=PERMISSIVE). No usar para evidencia de Review.");
    }

    @Override
    public void requireIcd10Code(String icd10Code) {
        String code = CatalogCodes.normalize(icd10Code);
        if (code == null || !CatalogCodes.ICD10.matcher(code).matches()) {
            throw new BusinessRuleException(ErrorCode.ICD10_CODE_NOT_ALLOWED,
                    "El codigo CIE-10 '" + icd10Code + "' no tiene un formato valido");
        }
    }

    @Override
    public void requireStudyTypeCode(String studyTypeCode) {
        String code = CatalogCodes.normalize(studyTypeCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches()) {
            throw new BusinessRuleException(ErrorCode.STUDY_TYPE_NOT_ALLOWED,
                    "El tipo de estudio '" + studyTypeCode + "' no tiene un formato valido");
        }
    }

    @Override
    public void requireMedicationCode(String medicationCode) {
        String code = CatalogCodes.normalize(medicationCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches()) {
            throw new BusinessRuleException(ErrorCode.MEDICATION_CODE_NOT_ALLOWED,
                    "El medicamento '" + medicationCode + "' no tiene un formato valido");
        }
    }
}
