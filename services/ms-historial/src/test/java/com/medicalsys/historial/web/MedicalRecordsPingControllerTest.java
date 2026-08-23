package com.medicalsys.historial.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.historial.web.controller.MedicalRecordsPingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MedicalRecordsPingController.class)
class MedicalRecordsPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el ping identifica a ms-historial sin requerir cabeceras de identidad")
    void identifiesMedicalRecordsService() throws Exception {
        mockMvc.perform(get("/api/v1/medical-records/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-historial"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
