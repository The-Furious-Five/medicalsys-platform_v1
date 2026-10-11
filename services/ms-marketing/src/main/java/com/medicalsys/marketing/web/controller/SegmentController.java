package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresPermission;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.service.SegmentService;
import com.medicalsys.marketing.web.dto.CreateSegmentRequest;
import com.medicalsys.marketing.web.dto.PageResponse;
import com.medicalsys.marketing.web.dto.SegmentPreviewResponse;
import com.medicalsys.marketing.web.dto.SegmentResponse;
import com.medicalsys.marketing.web.dto.UpdateSegmentRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Segmentos de audiencia.
 *
 * <p>El cuerpo no acepta {@code organizationId}: lo impone el gateway desde claims firmados. Y no
 * acepta ningun criterio fuera de la lista blanca: un intento de segmentar por dato clinico responde
 * 422 {@code CLINICAL_DATA_NOT_ALLOWED} y queda registrado.
 */
@RestController
@RequestMapping("/api/v1/segmentos")
public class SegmentController {

    private final SegmentService service;

    public SegmentController(SegmentService service) {
        this.service = service;
    }

    /** Crea un segmento y devuelve su tamano estimado ya evaluado. */
    @PostMapping
    @RequiresPermission("marketing.segment.create")
    public ResponseEntity<SegmentResponse> create(@Valid @RequestBody CreateSegmentRequest request) {
        SegmentResponse segment = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/segmentos/" + segment.id())).body(segment);
    }

    /** Lista los segmentos del tenant. */
    @GetMapping
    @RequiresPermission("marketing.segment.read")
    public PageResponse<SegmentResponse> list(@RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), active, pageable));
    }

    /** Devuelve un segmento con su criterio. */
    @GetMapping("/{segmentId}")
    @RequiresPermission("marketing.segment.read")
    public SegmentResponse get(@PathVariable UUID segmentId) {
        return service.get(RequestContextHolder.require(), segmentId);
    }

    /** Modifica la descripcion o el criterio. */
    @PatchMapping("/{segmentId}")
    @RequiresPermission("marketing.segment.update")
    public SegmentResponse update(@PathVariable UUID segmentId,
            @Valid @RequestBody UpdateSegmentRequest request) {
        return service.update(RequestContextHolder.require(), segmentId, request);
    }

    /** Reevalua el segmento y devuelve su tamano con una muestra. */
    @PostMapping("/{segmentId}/evaluacion")
    @RequiresPermission("marketing.segment.evaluate")
    public SegmentPreviewResponse evaluate(@PathVariable UUID segmentId) {
        return service.evaluate(RequestContextHolder.require(), segmentId);
    }

    /**
     * Evalua un criterio sin guardarlo.
     *
     * <p>Es la operacion que evita el error caro: ver a cuanta gente alcanza un criterio antes de
     * crear la campana, cuando todavia se puede corregir.
     */
    @PostMapping("/vista-previa")
    @RequiresPermission("marketing.segment.evaluate")
    public SegmentPreviewResponse preview(@RequestBody Map<String, Object> criteria) {
        return service.previewRaw(RequestContextHolder.require(), criteria);
    }

    /** Desactiva el segmento sin borrarlo. */
    @PostMapping("/{segmentId}/desactivacion")
    @RequiresPermission("marketing.segment.deactivate")
    public SegmentResponse deactivate(@PathVariable UUID segmentId) {
        return service.deactivate(RequestContextHolder.require(), segmentId);
    }

    /** Elimina el segmento; falla con 409 si alguna campana lo referencia. */
    @DeleteMapping("/{segmentId}")
    @RequiresPermission("marketing.segment.delete")
    public ResponseEntity<Void> delete(@PathVariable UUID segmentId) {
        service.delete(RequestContextHolder.require(), segmentId);
        return ResponseEntity.noContent().build();
    }
}
