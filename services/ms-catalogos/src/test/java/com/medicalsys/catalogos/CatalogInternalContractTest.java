package com.medicalsys.catalogos;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.catalogos.icd10.Icd10Catalog;
import com.medicalsys.catalogos.icd10.Icd10Repository;
import com.medicalsys.catalogos.medication.Medication;
import com.medicalsys.catalogos.medication.MedicationRepository;
import com.medicalsys.catalogos.specialty.Specialty;
import com.medicalsys.catalogos.specialty.SpecialtyRepository;
import com.medicalsys.catalogos.studytype.StudyType;
import com.medicalsys.catalogos.studytype.StudyTypeRepository;
import com.medicalsys.catalogos.tax.TaxCatalog;
import com.medicalsys.catalogos.tax.TaxCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogInternalContractTest {

    private static final String CORRELATION = "11111111-2222-4333-8444-555555555555";
    private static final String AUTHENTICATED_USER = "99999999-8888-4777-8666-555555555555";
    /** Los permisos de lectura de los cinco catalogos, como los propaga el gateway en X-Permissions. */
    private static final String CATALOG_READ = "catalogs.icd10.read,catalogs.medication.read,"
            + "catalogs.specialty.read,catalogs.study_type.read,catalogs.tax.read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Icd10Repository icd10Repository;

    @Autowired
    private StudyTypeRepository studyTypeRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private TaxCatalogRepository taxCatalogRepository;

    @BeforeEach
    void seed() {
        icd10Repository.deleteAll();
        studyTypeRepository.deleteAll();
        medicationRepository.deleteAll();
        specialtyRepository.deleteAll();
        taxCatalogRepository.deleteAll();

        icd10Repository.save(new Icd10Catalog("J00", "Rinofaringitis aguda", true));
        icd10Repository.save(new Icd10Catalog("ZZZ999", "Codigo inactivo", false));
        studyTypeRepository.save(new StudyType(
                "X_RAY", "Radiografia", StudyType.Category.IMAGING, true, true));
        studyTypeRepository.save(new StudyType(
                "LAB_HEMOGRAM", "Hemograma", StudyType.Category.LABORATORY, false, true));
        studyTypeRepository.save(new StudyType(
                "OLD_STUDY", "Retirado", StudyType.Category.OTHER, false, false));
        medicationRepository.save(new Medication("PARACETAMOL_500", "Paracetamol", true));
        medicationRepository.save(new Medication("IBUPROFENO_400", "Ibuprofeno", true));
        medicationRepository.save(new Medication("RETIRADO_10", "Retirado", false));
        specialtyRepository.save(new Specialty("MEDICINA_GENERAL", "Medicina General"));
        taxCatalogRepository.save(new TaxCatalog(
                TaxCatalog.CatalogType.PRODUCT_SERVICE, "CONSULTA", "Consulta medica", true));
        taxCatalogRepository.save(new TaxCatalog(
                TaxCatalog.CatalogType.PRODUCT_SERVICE, "RETIRADO", "Producto retirado", false));
        taxCatalogRepository.save(new TaxCatalog(
                TaxCatalog.CatalogType.UNIT_OF_MEASURE, "58", "Unidad (servicio)", true));
    }

    @Test
    @DisplayName("Historial puede validar CIE-10, estudio y medicamento por las rutas congeladas")
    void historialInternalRoutesReturnActiveCodes() throws Exception {
        mockMvc.perform(get("/internal/v1/cie10/{code}", "J00").header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.code").value("J00"));

        mockMvc.perform(get("/internal/v1/tipos-estudio/{code}", "X_RAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("X_RAY"));

        mockMvc.perform(get("/internal/v1/medicamentos/{code}", "PARACETAMOL_500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PARACETAMOL_500"));
    }

    @Test
    @DisplayName("Los aliases ingleses internos responden el mismo recurso")
    void englishInternalAliasesRemainCompatible() throws Exception {
        mockMvc.perform(get("/internal/v1/icd10/{code}", "J00")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/study-types/{code}", "X_RAY")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/medications/{code}", "IBUPROFENO_400")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un codigo inactivo o desconocido es 404 con los seis campos de error")
    void unknownOrInactiveCodesAreNotFoundWithCommonError() throws Exception {
        mockMvc.perform(get("/internal/v1/cie10/{code}", "ZZZ999").header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ICD10_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").value(CORRELATION))
                .andExpect(jsonPath("$.path").value("/internal/v1/cie10/ZZZ999"));

        mockMvc.perform(get("/internal/v1/tipos-estudio/{code}", "OLD_STUDY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_TYPE_NOT_FOUND"));

        mockMvc.perform(get("/internal/v1/medicamentos/{code}", "NO_EXISTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("La API publica lista especialidades, estudios y medicamentos activos a personal autenticado")
    void publicListsOnlyActiveEntries() throws Exception {
        mockMvc.perform(asStaff(get("/api/v1/catalogs/specialties")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(asStaff(get("/api/v1/catalogs/study-types")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(asStaff(get("/api/v1/catalogs/medications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(asStaff(get("/api/v1/catalogs/cie10")).param("q", "J00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("J00"));
        mockMvc.perform(asStaff(get("/api/v1/catalogs/tax"))
                        .param("type", "PRODUCT_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CONSULTA"));
        mockMvc.perform(asStaff(get("/api/v1/catalogs/tax"))
                        .param("type", "UNIT_OF_MEASURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("58"));
    }

    @Test
    @DisplayName("RF-11.1: sin identidad propagada por el gateway, la API publica de catalogos responde 401")
    void publicRoutesRejectMissingIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/cie10").param("q", "J00").header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION))
                .andExpect(jsonPath("$.path").value("/api/v1/catalogs/cie10"));

        mockMvc.perform(get("/api/v1/catalogs/study-types")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalogs/medications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalogs/specialties")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalogs/tax").param("type", "PRODUCT_SERVICE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC-04: con identidad pero sin el permiso del catalogo responde 403 y nombra el permiso")
    void authenticatedWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/cie10").param("q", "J00")
                        .header("X-User-Id", AUTHENTICATED_USER)
                        .header("X-Permissions", "catalogs.medication.read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("catalogs.icd10.read")));
        mockMvc.perform(get("/api/v1/catalogs/medications")
                        .header("X-User-Id", AUTHENTICATED_USER)
                        .header("X-Permissions", "catalogs.medication.read"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Las rutas internas siguen abiertas a servicio-a-servicio, sin identidad de usuario")
    void internalRoutesDoNotRequireUserIdentity() throws Exception {
        mockMvc.perform(get("/internal/v1/cie10/{code}", "J00")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/tipos-estudio/{code}", "X_RAY")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/medicamentos/{code}", "PARACETAMOL_500")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/specialties/{code}", "MEDICINA_GENERAL")).andExpect(status().isOk());
    }

    /** Personal autenticado, con los permisos que el gateway propaga para leer los catalogos. */
    private static MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", AUTHENTICATED_USER).header("X-Permissions", CATALOG_READ);
    }
}
