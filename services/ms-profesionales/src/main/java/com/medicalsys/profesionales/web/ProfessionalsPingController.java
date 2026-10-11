package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.common.security.PublicEndpoint;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProfessionalsPingController {
    @GetMapping("/api/v1/professionals/ping")
    @PublicEndpoint
    Map<String, String> ping() { return Map.of("service", "ms-profesionales", "status", "ok"); }
}
