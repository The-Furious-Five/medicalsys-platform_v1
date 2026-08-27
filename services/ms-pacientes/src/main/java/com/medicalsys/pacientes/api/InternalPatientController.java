package com.medicalsys.pacientes.api;

import com.medicalsys.pacientes.api.dto.BillingPatientSummary;
import com.medicalsys.pacientes.api.dto.SchedulingPatientSummary;
import com.medicalsys.pacientes.service.PatientService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/patients")
public class InternalPatientController {

    private final PatientService service;

    public InternalPatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping("/{patientId}/scheduling-summary")
    public SchedulingPatientSummary schedulingSummary(@PathVariable UUID patientId) {
        return service.getSchedulingSummary(patientId);
    }

    @GetMapping("/{patientId}/billing-summary")
    public BillingPatientSummary billingSummary(@PathVariable UUID patientId) {
        return service.getBillingSummary(patientId);
    }
}
