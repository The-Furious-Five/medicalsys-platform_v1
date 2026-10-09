package com.medicalsys.facturacion.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.facturacion.web.controller.BillingPingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BillingPingController.class)
class BillingPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el ping identifica a ms-facturacion sin requerir cabeceras de identidad")
    void identifiesBillingService() throws Exception {
        mockMvc.perform(get("/api/v1/billing/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-facturacion"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
