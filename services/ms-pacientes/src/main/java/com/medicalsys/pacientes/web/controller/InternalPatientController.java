package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.BillingPatientSummary;
import com.medicalsys.pacientes.web.dto.SchedulingPatientSummary;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Proyecciones mínimas para consumidores internos; estas rutas no se publican en el gateway. */
@RestController
@RequestMapping("/internal/v1/patients")
public class InternalPatientController {

    private final PatientService patients;

    public InternalPatientController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping("/{patientId}/scheduling-summary")
    public SchedulingPatientSummary schedulingSummary(@PathVariable UUID patientId) {
        return patients.getSchedulingSummary(patientId);
    }

    @GetMapping("/{patientId}/billing-summary")
    public BillingPatientSummary billingSummary(@PathVariable UUID patientId) {
        return patients.getBillingSummary(patientId);
    }
}
