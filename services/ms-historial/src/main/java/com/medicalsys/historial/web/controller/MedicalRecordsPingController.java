package com.medicalsys.historial.web.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint temporal para comprobar el enrutamiento del servicio desde el gateway. */
@RestController
@RequestMapping("/api/v1/medical-records")
public class MedicalRecordsPingController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of(
                "service", "ms-historial",
                "status", "ok");
    }
}
