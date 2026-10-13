package com.medicalsys.agenda.web;

import com.medicalsys.agenda.security.PublicEndpoint;
import com.medicalsys.agenda.web.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling")
public class PingController {

    @GetMapping("/ping")
    @PublicEndpoint
    public PingResponse ping() {
        return new PingResponse("ms-agenda", "ok");
    }
}
