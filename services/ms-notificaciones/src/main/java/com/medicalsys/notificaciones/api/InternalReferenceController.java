package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.OrganizationReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PatientReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.service.ReferenceSyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/v1/notifications/references")
public class InternalReferenceController {

    private final ReferenceSyncService service;

    public InternalReferenceController(ReferenceSyncService service) {
        this.service = service;
    }

    @PutMapping("/organizations")
    public Mono<ResponseEntity<Void>> organization(@Valid @RequestBody OrganizationReferenceRequest request) {
        return service.upsertOrganization(request).thenReturn(ResponseEntity.noContent().build());
    }

    @PutMapping("/patients")
    public Mono<ResponseEntity<Void>> patient(@Valid @RequestBody PatientReferenceRequest request) {
        return service.upsertPatient(request).thenReturn(ResponseEntity.noContent().build());
    }

    @PutMapping("/preferences")
    public Mono<ResponseEntity<Void>> preference(@Valid @RequestBody PreferenceReferenceRequest request) {
        return service.upsertPreference(request).thenReturn(ResponseEntity.noContent().build());
    }
}
