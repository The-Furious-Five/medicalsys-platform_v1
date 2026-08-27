package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.FiscalCustomerResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas internas consumidas por otros microservicios dentro de la red medicalsys-interna.
 *
 * <p>Devuelven proyecciones minimas: ms-facturacion recibe el DTO fiscal, no la ficha completa.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final PatientService patients;

    public InternalController(PatientService patients) {
        this.patients = patients;
    }

    /** DTO fiscal minimo: documento y nombre. Consumido por ms-facturacion. */
    @GetMapping("/patients/{patientId}/fiscal")
    public FiscalCustomerResponse fiscalCustomer(@PathVariable UUID patientId) {
        return patients.getFiscalCustomer(patientId);
    }

    /** Existencia y datos minimos del paciente; ms-agenda lo usa antes de crear una cita. */
    @GetMapping("/patients/{patientId}")
    public PatientResponse patient(@PathVariable UUID patientId) {
        return patients.get(patientId);
    }
}
