package com.medicalsys.auditoria.web.controller;

import com.medicalsys.auditoria.service.AuditEventService;
import com.medicalsys.auditoria.web.dto.AuditEventReceipt;
import com.medicalsys.auditoria.web.dto.RecordAuditEventRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingesta de eventos desde los demas microservicios.
 *
 * <p>Vive bajo {@code /internal/v1/**} y el gateway no publica ese prefijo: la bitacora solo se
 * escribe desde dentro de la red de servicios. Si el navegador pudiera asentar eventos, cualquiera
 * podria fabricar la prueba de que no accedio a un expediente.
 *
 * <p>Estas rutas no exigen rol. La identidad que importa aqui no es la del usuario final sino la
 * del servicio que reporta, y el actor del hecho viaja dentro del propio evento.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/audit-events")
public class InternalAuditEventController {

    /**
     * Tope del lote.
     *
     * <p>Existe porque el asiento toma un bloqueo sobre la punta de la cadena de la organizacion:
     * un lote sin limite lo retendria el tiempo suficiente para frenar al resto de productores.
     */
    private static final int MAX_BATCH_SIZE = 200;

    private final AuditEventService auditEvents;

    public InternalAuditEventController(AuditEventService auditEvents) {
        this.auditEvents = auditEvents;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventReceipt record(@Valid @RequestBody RecordAuditEventRequest request) {
        return auditEvents.record(request);
    }

    /** Descarga de eventos acumulados por un productor mientras la bitacora estuvo inaccesible. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AuditEventReceipt> recordAll(
            @RequestBody @NotEmpty @Size(max = MAX_BATCH_SIZE) List<@Valid RecordAuditEventRequest> requests) {
        return auditEvents.recordAll(requests);
    }
}
