package com.medicalsys.historial.client;

import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.config.HistorialProperties.CatalogValidationMode;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Validacion local de codigos de catalogo mientras ms-catalogos no publique su endpoint.
 *
 * <p>En modo {@code LOCAL} exige formato canonico y pertenencia a la lista de codigos sembrados en
 * el entorno de demostracion: un CIE-10 inexistente se rechaza con 422, que es la verificacion
 * comprometida para el encuentro clinico. En modo {@code PERMISSIVE} solo se exige el formato.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.catalogs", name = "validation-mode",
        havingValue = "LOCAL", matchIfMissing = true)
public class LocalCatalogsClient implements CatalogsClient {

    private static final Logger log = LoggerFactory.getLogger(LocalCatalogsClient.class);

    private final Set<String> icd10Codes;
    private final Set<String> studyTypeCodes;
    private final Set<String> medicationCodes;

    public LocalCatalogsClient(HistorialProperties properties) {
        HistorialProperties.Catalogs.Local local = properties.catalogs().local();
        this.icd10Codes = normalize(local.icd10Codes());
        this.studyTypeCodes = normalize(local.studyTypeCodes());
        this.medicationCodes = normalize(local.medicationCodes());
        log.warn("Los codigos de catalogo se validan con la lista local de demostracion "
                + "(CATALOGS_VALIDATION_MODE=LOCAL). Para consultar ms-catalogos use REMOTE.");
    }

    @Override
    public void requireIcd10Code(String icd10Code) {
        String code = CatalogCodes.normalize(icd10Code);
        if (code == null || !CatalogCodes.ICD10.matcher(code).matches() || !icd10Codes.contains(code)) {
            throw new BusinessRuleException(ErrorCode.ICD10_CODE_NOT_ALLOWED,
                    "El codigo CIE-10 '" + icd10Code + "' no existe en el catalogo vigente");
        }
    }

    @Override
    public void requireStudyTypeCode(String studyTypeCode) {
        String code = CatalogCodes.normalize(studyTypeCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches() || !studyTypeCodes.contains(code)) {
            throw new BusinessRuleException(ErrorCode.STUDY_TYPE_NOT_ALLOWED,
                    "El tipo de estudio '" + studyTypeCode + "' no existe en el catalogo vigente");
        }
    }

    @Override
    public void requireMedicationCode(String medicationCode) {
        String code = CatalogCodes.normalize(medicationCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches() || !medicationCodes.contains(code)) {
            throw new BusinessRuleException(ErrorCode.MEDICATION_CODE_NOT_ALLOWED,
                    "El medicamento '" + medicationCode + "' no existe en el vademecum vigente");
        }
    }

    private Set<String> normalize(Set<String> codes) {
        return codes.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    /** Expone el modo activo para los mensajes de arranque y las pruebas. */
    public CatalogValidationMode mode() {
        return CatalogValidationMode.LOCAL;
    }
}
