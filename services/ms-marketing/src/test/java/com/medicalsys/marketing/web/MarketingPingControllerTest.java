package com.medicalsys.marketing.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.marketing.web.controller.MarketingPingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la ruta publica.
 *
 * <p>La segunda comprueba algo mas que un endpoint: que la lista de ejes permitidos se publica en el
 * contrato. Quien integre tiene que poder verificar la frontera de privacidad sin leer este codigo.
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
    @DisplayName("el contrato publica los ejes de segmentacion permitidos")
    void publishesAllowedCriteria() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/criterios-permitidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].field").value("minAge"))
                .andExpect(jsonPath("$.fields.length()").value(13));
    }
}
