package com.medicalsys.tenencia.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.tenencia.web.controller.TenancyPingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TenancyPingController.class)
class TenancyPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el ping identifica a ms-tenencia sin requerir cabeceras de identidad")
    void identifiesTenancyService() throws Exception {
        mockMvc.perform(get("/api/v1/tenancy/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-tenencia"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
