package com.medicalsys.notificaciones.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsPingController {

    @GetMapping("/ping")
    public Mono<Map<String, String>> ping() {
        return Mono.just(Map.of("service", "ms-notificaciones", "status", "ok"));
    }
}
