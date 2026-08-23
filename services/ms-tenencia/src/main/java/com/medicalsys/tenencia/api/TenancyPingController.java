package com.medicalsys.tenencia.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenancy")
public class TenancyPingController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
	return Map.of(
		"service", "ms-tenencia",
		"status", "ok"
	);
    }
}
