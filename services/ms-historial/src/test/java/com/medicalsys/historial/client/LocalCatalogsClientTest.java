package com.medicalsys.historial.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.config.HistorialProperties.CatalogValidationMode;
import com.medicalsys.historial.config.HistorialProperties.CryptoMode;
import com.medicalsys.historial.support.TestFixtures;
import java.time.Duration;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verificacion comprometida para el encuentro clinico: un codigo CIE-10 invalido se rechaza.
 */
class LocalCatalogsClientTest {

    private final LocalCatalogsClient catalogs = new LocalCatalogsClient(properties());

    @ParameterizedTest(name = "acepta el codigo CIE-10 {0}")
    @ValueSource(strings = {"J00", "M54.5", "j00", " M54.5 "})
    void acceptsCatalogedCodes(String code) {
        assertThatCode(() -> catalogs.requireIcd10Code(code)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rechaza el codigo CIE-10 {0}")
    @ValueSource(strings = {"ZZZ999", "A99", "no-es-un-codigo", "1234"})
    @DisplayName("un CIE-10 inexistente o mal formado se rechaza con 422")
    void rejectsUnknownIcd10Codes(String code) {
        assertThatThrownBy(() -> catalogs.requireIcd10Code(code))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.ICD10_CODE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("acepta el tipo de estudio X_RAY que consume la orden de rayos X")
    void acceptsStudyType() {
        assertThatCode(() -> catalogs.requireStudyTypeCode("X_RAY")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza un tipo de estudio ajeno al catalogo")
    void rejectsUnknownStudyType() {
        assertThatThrownBy(() -> catalogs.requireStudyTypeCode("RESONANCIA"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.STUDY_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("rechaza un medicamento fuera del vademecum")
    void rejectsUnknownMedication() {
        assertThatThrownBy(() -> catalogs.requireMedicationCode("MEDICAMENTO_INVENTADO"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code", InstanceOfAssertFactories.type(ErrorCode.class))
                .isEqualTo(ErrorCode.MEDICATION_CODE_NOT_ALLOWED);
    }

    private static HistorialProperties properties() {
        return new HistorialProperties(
                new HistorialProperties.Crypto(CryptoMode.LOCAL, "http://ms-criptografia:8084", "clave",
                        TestFixtures.KEY_ID, Duration.ofSeconds(2), Duration.ofSeconds(3)),
                new HistorialProperties.Catalogs(CatalogValidationMode.LOCAL, "http://ms-catalogos:8083",
                        Duration.ofSeconds(2), Duration.ofSeconds(3),
                        new HistorialProperties.Catalogs.Local(Set.of("J00", "M54.5", "R51"),
                                Set.of("X_RAY", "LAB_HEMOGRAM"), Set.of("PARACETAMOL_500"))),
                new HistorialProperties.Numbering("EXP", "REC", 5));
    }
}
