package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.PublicEndpoint;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint temporal para comprobar el enrutamiento del servicio desde el gateway. */
@RestController
@RequestMapping("/api/v1/tenancy")
public class TenancyPingController {

    @GetMapping("/ping")
    @PublicEndpoint
    public Map<String, String> ping() {
        return Map.of(
                "service", "ms-tenencia",
                "status", "ok");
    }
}
