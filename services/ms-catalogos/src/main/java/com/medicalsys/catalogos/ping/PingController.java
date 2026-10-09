package com.medicalsys.catalogos.ping;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de prueba acordado por el equipo.
 * GET /api/v1/catalogs/ping
 * Respuesta: { "service": "ms-catalogos", "status": "ok" }
 */
@RestController
public class PingController {

    @GetMapping("/api/v1/catalogs/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "service", "ms-catalogos",
                "status",  "ok"
        ));
    }
}
