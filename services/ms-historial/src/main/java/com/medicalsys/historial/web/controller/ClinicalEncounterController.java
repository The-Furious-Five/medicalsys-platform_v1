package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.DateTimes;
import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.service.ClinicalEncounterService;
import com.medicalsys.historial.web.dto.AddendumResponse;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.CreateAddendumRequest;
import com.medicalsys.historial.web.dto.EncounterSummaryResponse;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import com.medicalsys.historial.web.dto.PageResponse;
import com.medicalsys.historial.web.dto.SignEncounterRequest;
import com.medicalsys.historial.web.dto.UpdateNarrativeRequest;
import com.medicalsys.historial.web.dto.VoidEncounterRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Encuentros clinicos.
 *
 * <p>La narrativa solo viaja descifrada hacia roles clinicos; una recepcionista ve el encuentro y su
 * estado, no su contenido.
 */
@RestController
@RequestMapping("/api/v1/encuentros")
public class ClinicalEncounterController {

    private final ClinicalEncounterService service;

    public ClinicalEncounterController(ClinicalEncounterService service) {
        this.service = service;
    }

    /** Abre un encuentro en borrador, recuperando o creando el expediente del paciente. */
    @PostMapping
    @RequiresPermission("clinical.encounter.open")
    public ResponseEntity<ClinicalEncounterResponse> open(@Valid @RequestBody OpenEncounterRequest request) {
        ClinicalEncounterResponse body = service.open(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/encuentros/" + body.id())).body(body);
    }

    @GetMapping("/{encounterId}")
    @RequiresPermission("clinical.encounter.read")
    public ClinicalEncounterResponse get(@PathVariable UUID encounterId) {
        return service.get(RequestContextHolder.require(), encounterId);
    }

    /** Busqueda paginada por expediente, profesional, estado y rango de fechas. */
    @GetMapping
    @RequiresPermission("clinical.encounter.read")
    public PageResponse<EncounterSummaryResponse> search(
            @RequestParam(required = false) UUID recordId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(RequestContextHolder.require(), recordId, professionalId, status,
                DateTimes.toInstant(from), DateTimes.toInstant(to), pageable));
    }

    /** Reemplaza la narrativa SOAP; solo se acepta sobre un encuentro en borrador. */
    @PutMapping("/{encounterId}/notas")
    @RequiresPermission("clinical.encounter.update_notes")
    public ClinicalEncounterResponse updateNarrative(@PathVariable UUID encounterId,
            @Valid @RequestBody UpdateNarrativeRequest request) {
        return service.updateNarrative(RequestContextHolder.require(), encounterId, request);
    }

    /** Firma el encuentro y lo vuelve inmutable. */
    @PostMapping("/{encounterId}/firma")
    @RequiresPermission("clinical.encounter.sign")
    public ClinicalEncounterResponse sign(@PathVariable UUID encounterId,
            @RequestBody(required = false) SignEncounterRequest request) {
        return service.sign(RequestContextHolder.require(), encounterId, request);
    }

    /** Anula el encuentro conservando el registro y su motivo. */
    @PostMapping("/{encounterId}/anulacion")
    @RequiresPermission("clinical.encounter.void")
    public ClinicalEncounterResponse voidEncounter(@PathVariable UUID encounterId,
            @Valid @RequestBody VoidEncounterRequest request) {
        return service.voidEncounter(RequestContextHolder.require(), encounterId, request);
    }

    /** Agrega una adicion inmutable sobre un encuentro firmado. */
    @PostMapping("/{encounterId}/addendums")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("clinical.addendum.create")
    public AddendumResponse addAddendum(@PathVariable UUID encounterId,
            @Valid @RequestBody CreateAddendumRequest request) {
        return service.addAddendum(RequestContextHolder.require(), encounterId, request);
    }

    @GetMapping("/{encounterId}/addendums")
    @RequiresPermission("clinical.addendum.read")
    public List<AddendumResponse> listAddendums(@PathVariable UUID encounterId) {
        return service.listAddendums(RequestContextHolder.require(), encounterId);
    }
}
