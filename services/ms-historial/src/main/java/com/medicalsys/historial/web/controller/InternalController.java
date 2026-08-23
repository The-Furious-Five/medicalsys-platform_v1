package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.ClinicalEncounterService;
import com.medicalsys.historial.service.MedicalRecordService;
import com.medicalsys.historial.service.StudyOrderService;
import com.medicalsys.historial.web.dto.EncounterSummaryResponse;
import com.medicalsys.historial.web.dto.MedicalRecordSummaryResponse;
import com.medicalsys.historial.web.dto.StudyOrderSummaryResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas internas consumidas por otros microservicios dentro de la red medicalsys-interna.
 *
 * <p>Devuelven proyecciones minimas: ningun consumidor recibe narrativa clinica ni referencias de
 * cifrado. El aislamiento por organizacion se aplica igual que en las rutas publicas.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final MedicalRecordService medicalRecords;
    private final ClinicalEncounterService encounters;
    private final StudyOrderService studyOrders;

    public InternalController(MedicalRecordService medicalRecords, ClinicalEncounterService encounters,
            StudyOrderService studyOrders) {
        this.medicalRecords = medicalRecords;
        this.encounters = encounters;
        this.studyOrders = studyOrders;
    }

    /** Expediente por su UUID publico. */
    @GetMapping("/expedientes/{recordId}")
    public MedicalRecordSummaryResponse getRecord(@PathVariable UUID recordId) {
        return medicalRecords.getSummaryByPublicId(RequestContextHolder.require(), recordId);
    }

    /** Expediente de un paciente; lo consume ms-documental para vincular archivos. */
    @GetMapping("/expedientes")
    public MedicalRecordSummaryResponse getRecordByPatient(@RequestParam UUID patientId) {
        return medicalRecords.getSummaryByPatient(RequestContextHolder.require(), patientId);
    }

    /** Encuentro por su UUID publico, sin narrativa. */
    @GetMapping("/encuentros/{encounterId}")
    public EncounterSummaryResponse getEncounter(@PathVariable UUID encounterId) {
        return encounters.getSummary(RequestContextHolder.require(), encounterId);
    }

    /** Encuentro asociado a una cita; lo consume ms-agenda para cerrar el circulo. */
    @GetMapping("/encuentros")
    public EncounterSummaryResponse getEncounterByAppointment(@RequestParam UUID appointmentId) {
        return encounters.getSummaryByAppointment(RequestContextHolder.require(), appointmentId);
    }

    /** Orden de estudio por su orderId; la consumen ms-agenda y ms-documental. */
    @GetMapping("/ordenes-estudio/{orderId}")
    public StudyOrderSummaryResponse getStudyOrder(@PathVariable UUID orderId) {
        return studyOrders.getSummary(RequestContextHolder.require(), orderId);
    }
}
