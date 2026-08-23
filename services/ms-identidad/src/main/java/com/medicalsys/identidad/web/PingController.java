package com.medicalsys.identidad.web;

import com.medicalsys.identidad.web.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ms-identidad", "ok");
    }
}
