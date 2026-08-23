package com.medicalsys.historial.client;

import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.DependencyUnavailableException;
import com.medicalsys.historial.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Valida codigos consultando ms-catalogos.
 *
 * <p>Contrato consumido (rutas internas):
 * <pre>
 * GET /internal/v1/cie10/{code}          -> 200 si existe, 404 si no
 * GET /internal/v1/tipos-estudio/{code}  -> 200 si existe, 404 si no
 * GET /internal/v1/medicamentos/{code}   -> 200 si existe, 404 si no
 * </pre>
 *
 * <p>Un 404 es una respuesta de contrato y se traduce a 422; una caida de la dependencia se
 * traduce a 503 y nunca se confunde con un codigo invalido.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.catalogs", name = "validation-mode", havingValue = "REMOTE")
public class RemoteCatalogsClient implements CatalogsClient {

    private final RestClient restClient;

    public RemoteCatalogsClient(@Qualifier("catalogsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void requireIcd10Code(String icd10Code) {
        String code = CatalogCodes.normalize(icd10Code);
        if (code == null || !CatalogCodes.ICD10.matcher(code).matches()) {
            throw notAllowed(ErrorCode.ICD10_CODE_NOT_ALLOWED, "CIE-10", icd10Code, "no tiene un formato valido");
        }
        requireExists("/internal/v1/cie10/{code}", code, ErrorCode.ICD10_CODE_NOT_ALLOWED, "CIE-10");
    }

    @Override
    public void requireStudyTypeCode(String studyTypeCode) {
        String code = CatalogCodes.normalize(studyTypeCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches()) {
            throw notAllowed(ErrorCode.STUDY_TYPE_NOT_ALLOWED, "tipo de estudio", studyTypeCode,
                    "no tiene un formato valido");
        }
        requireExists("/internal/v1/tipos-estudio/{code}", code, ErrorCode.STUDY_TYPE_NOT_ALLOWED,
                "tipo de estudio");
    }

    @Override
    public void requireMedicationCode(String medicationCode) {
        String code = CatalogCodes.normalize(medicationCode);
        if (code == null || !CatalogCodes.CATALOG_CODE.matcher(code).matches()) {
            throw notAllowed(ErrorCode.MEDICATION_CODE_NOT_ALLOWED, "medicamento", medicationCode,
                    "no tiene un formato valido");
        }
        requireExists("/internal/v1/medicamentos/{code}", code, ErrorCode.MEDICATION_CODE_NOT_ALLOWED, "medicamento");
    }

    private void requireExists(String path, String code, ErrorCode errorCode, String label) {
        try {
            restClient.get().uri(path, code).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notAllowed(errorCode, label, code, "no existe en el catalogo vigente");
            }
            throw new DependencyUnavailableException(
                    "ms-catalogos respondio " + e.getStatusCode() + " al validar un codigo", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-catalogos no esta disponible para validar codigos de catalogo", e);
        }
    }

    private BusinessRuleException notAllowed(ErrorCode errorCode, String label, String code, String reason) {
        return new BusinessRuleException(errorCode, "El codigo de " + label + " '" + code + "' " + reason);
    }
}
