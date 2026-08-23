package com.medicalsys.historial.client;

/**
 * Puerto de validacion contra ms-catalogos.
 *
 * <p>ms-historial no guarda catalogos globales: valida por contrato HTTP y persiste solo el codigo.
 * Un codigo desconocido se rechaza en el momento de registrar el dato, nunca despues.
 */
public interface CatalogsClient {

    /**
     * Verifica que el codigo CIE-10 exista y sea utilizable.
     *
     * @throws com.medicalsys.historial.common.error.BusinessRuleException si el codigo no es valido
     */
    void requireIcd10Code(String icd10Code);

    /**
     * Verifica que el tipo de estudio exista.
     *
     * @throws com.medicalsys.historial.common.error.BusinessRuleException si el codigo no es valido
     */
    void requireStudyTypeCode(String studyTypeCode);

    /**
     * Verifica que el medicamento exista en el vademecum.
     *
     * @throws com.medicalsys.historial.common.error.BusinessRuleException si el codigo no es valido
     */
    void requireMedicationCode(String medicationCode);
}
