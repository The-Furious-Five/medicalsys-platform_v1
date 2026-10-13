package com.medicalsys.agenda.web;

import com.medicalsys.agenda.security.InternalEndpoint;
import com.medicalsys.agenda.service.AppointmentService;
import com.medicalsys.agenda.service.SchedulingMapper;
import com.medicalsys.agenda.web.dto.InternalAppointmentResponse;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno para ms-historial: comprueba que la cita exista y en que estado esta antes de
 * abrir un encuentro clinico. No sale del host, viaja por la red interna de Compose.
 */
@RestController
@RequestMapping("/internal/v1/appointments")
public class InternalAppointmentController {

    private final AppointmentService appointments;
    private final SchedulingMapper mapper;

    public InternalAppointmentController(AppointmentService appointments, SchedulingMapper mapper) {
        this.appointments = appointments;
        this.mapper = mapper;
    }

    @GetMapping("/{publicId}")
    @Transactional(readOnly = true)
    @InternalEndpoint
    public InternalAppointmentResponse get(@PathVariable UUID publicId) {
        return mapper.toInternalResponse(appointments.require(publicId));
    }
}
