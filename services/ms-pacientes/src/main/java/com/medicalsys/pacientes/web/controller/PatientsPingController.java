package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.PublicEndpoint;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientsPingController {

    @GetMapping("/ping")
    @PublicEndpoint
    public Map<String, String> ping() {
        return Map.of("service", "ms-pacientes", "status", "ok");
    }
}
