package com.medicalsys.marketing.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.marketing.common.tenant.TenantContextFilter;
import com.medicalsys.marketing.web.controller.MarketingPingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas del ping y de los ejes de segmentacion.
 *
 * <p>La lista de ejes permitidos se publica en el contrato para que quien integre pueda verificar la
 * frontera de privacidad sin leer este codigo. Desde RBAC-04 exige marketing.segment.read, como el
 * resto de la consulta de segmentos; el ping sigue siendo publico.
 */
@WebMvcTest(MarketingPingController.class)
class MarketingPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el ping responde sin identidad propagada")
    void pingIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-marketing"))
                .andExpect(jsonPath("$.publishes").value("marketing.campana"))
                .andExpect(jsonPath("$.clinicalDataAccess").value("none"));
    }

    @Test
    @DisplayName("el contrato publica los ejes de segmentacion permitidos a quien consulta segmentos")
    void publishesAllowedCriteria() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/criterios-permitidos")
                        .header(TenantContextFilter.ORGANIZATION_HEADER, "00000000-0000-4000-8000-0000000000a1")
                        .header(TenantContextFilter.USER_HEADER, "00000000-0000-4000-8000-000000000901")
                        .header(TenantContextFilter.PERMISSIONS_HEADER, "marketing.segment.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].field").value("minAge"))
                .andExpect(jsonPath("$.fields.length()").value(13));
    }

    @Test
    @DisplayName("sin la identidad del gateway, los ejes responden 401")
    void allowedCriteriaRequireIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/criterios-permitidos")).andExpect(status().isUnauthorized());
    }
}
