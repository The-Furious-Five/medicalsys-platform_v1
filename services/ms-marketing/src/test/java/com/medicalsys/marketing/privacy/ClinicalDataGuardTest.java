package com.medicalsys.marketing.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.marketing.common.error.ApiException;
import com.medicalsys.marketing.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas del guardian que impide que un dato clinico entre a marketing_db.
 *
 * <p>No necesitan Spring ni base: el guardian es logica pura, y una prueba rapida se ejecuta en cada
 * compilacion en vez de solo cuando hay Docker.
 */
class ClinicalDataGuardTest {

    private final ClinicalDataGuard guard = new ClinicalDataGuard();

    @ParameterizedTest(name = "rechaza el criterio clinico ''{0}''")
    @ValueSource(strings = {"diagnosticoCie10", "diagnostico_principal", "DIAGNOSTICO", "cie10",
            "icd10Code", "alergias", "allergyList", "tratamientoActual", "medicacion", "recetaVigente",
            "expedienteId", "medicalRecordId", "resultadoEstudio", "sintomas", "encryptedAddress"})
    @DisplayName("un criterio con vocabulario clinico se rechaza con CLINICAL_DATA_NOT_ALLOWED")
    void rejectsClinicalCriteria(String field) {
        assertThatThrownBy(() -> guard.assertSegmentCriteriaAllowed(Map.of(field, "cualquier valor")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CLINICAL_DATA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("la deteccion no depende de acentos, mayusculas ni separadores")
    void detectionIsInsensitiveToSpelling() {
        assertThatThrownBy(() -> guard.assertSegmentCriteriaAllowed(Map.of("Diagnóstico-CIE 10", "J45")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CLINICAL_DATA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("un criterio desconocido pero no clinico se rechaza como criterio invalido")
    void rejectsUnknownCriteriaSeparately() {
        // La distincion importa: un nombre mal escrito no es lo mismo que un intento de cruzar
        // patologia con publicidad, y quien lea el log tiene que poder diferenciarlos.
        assertThatThrownBy(() -> guard.assertSegmentCriteriaAllowed(Map.of("colorFavorito", "azul")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.SEGMENT_CRITERIA_INVALID);
    }

    @Test
    @DisplayName("un criterio vacio se rechaza: nadie debe crear por accidente el segmento 'todos'")
    void rejectsEmptyCriteria() {
        assertThatThrownBy(() -> guard.assertSegmentCriteriaAllowed(Map.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.SEGMENT_CRITERIA_INVALID);
    }

    @Test
    @DisplayName("los trece ejes de la lista blanca se aceptan")
    void acceptsEveryAllowedField() {
        for (SegmentCriteriaField field : SegmentCriteriaField.values()) {
            assertThatCode(() -> guard.assertSegmentCriteriaAllowed(Map.of(field.jsonName(), "valor")))
                    .as("el eje %s debe aceptarse", field.jsonName())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("un evento con un campo clinico anidado se rechaza")
    void rejectsClinicalFieldNestedInsideAnEvent() {
        Map<String, Object> payload = Map.of(
                "patientId", "00000000-0000-4000-8000-000000000001",
                "encounter", Map.of("suggestedNextVisitOn", "2026-10-01", "diagnosisCie10", "J45"));

        assertThatThrownBy(() -> guard.assertEventPayloadAllowed(payload, "historial.encuentro"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CLINICAL_DATA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("un evento con un campo clinico dentro de una lista se rechaza")
    void rejectsClinicalFieldInsideAList() {
        Map<String, Object> payload = Map.of(
                "patientId", "00000000-0000-4000-8000-000000000001",
                "items", List.of(Map.of("serviceOfferingId", "x"), Map.of("cie10", "J45")));

        assertThatThrownBy(() -> guard.assertEventPayloadAllowed(payload, "facturacion.factura"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CLINICAL_DATA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("un evento con campos administrativos de mas se acepta: sobrar no es delito")
    void toleratesUnknownButHarmlessEventFields() {
        Map<String, Object> payload = Map.of(
                "patientId", "00000000-0000-4000-8000-000000000001",
                "suggestedNextVisitOn", "2026-10-01",
                "campoQueMarketingIgnora", "algo");

        assertThatCode(() -> guard.assertEventPayloadAllowed(payload, "historial.encuentro"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la lista blanca publicada coincide con los ejes declarados")
    void allowedNamesAreTheDeclaredOnes() {
        assertThat(SegmentCriteriaField.allowedJsonNames())
                .containsExactly("minAge", "maxAge", "sex", "cities", "lastVisitBefore", "lastVisitAfter",
                        "minAppointments", "maxNoShows", "minTotalBilledBob", "lastServiceOfferingId",
                        "birthdayMonth", "controlDueBefore", "excludeCampaignId");
    }
}
