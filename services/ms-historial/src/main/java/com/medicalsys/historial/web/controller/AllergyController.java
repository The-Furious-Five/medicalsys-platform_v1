package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.AllergyService;
import com.medicalsys.historial.web.dto.AllergyResponse;
import com.medicalsys.historial.web.dto.CreateAllergyRequest;
import com.medicalsys.historial.web.dto.UpdateAllergyRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alergias del expediente. */
@RestController
@RequestMapping("/api/v1")
public class AllergyController {

    private final AllergyService service;

    public AllergyController(AllergyService service) {
        this.service = service;
    }

    @PostMapping("/expedientes/{recordId}/alergias")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("clinical.allergy.create")
    public AllergyResponse create(@PathVariable UUID recordId, @Valid @RequestBody CreateAllergyRequest request) {
        return service.create(RequestContextHolder.require(), recordId, request);
    }

    @GetMapping("/expedientes/{recordId}/alergias")
    @RequiresPermission("clinical.allergy.read")
    public List<AllergyResponse> list(@PathVariable UUID recordId) {
        return service.list(RequestContextHolder.require(), recordId);
    }

    @PatchMapping("/alergias/{allergyId}")
    @RequiresPermission("clinical.allergy.update")
    public AllergyResponse update(@PathVariable Long allergyId,
            @Valid @RequestBody UpdateAllergyRequest request) {
        return service.update(RequestContextHolder.require(), allergyId, request);
    }
}
