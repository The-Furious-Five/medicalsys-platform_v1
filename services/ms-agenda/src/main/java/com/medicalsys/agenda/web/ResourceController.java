package com.medicalsys.agenda.web;

import com.medicalsys.agenda.domain.ResourceStatus;
import com.medicalsys.agenda.domain.ResourceType;
import com.medicalsys.agenda.service.PhysicalResourceService;
import com.medicalsys.agenda.web.dto.ChangeResourceStatusRequest;
import com.medicalsys.agenda.web.dto.CreateResourceRequest;
import com.medicalsys.agenda.web.dto.PageResponse;
import com.medicalsys.agenda.web.dto.ResourceResponse;
import com.medicalsys.agenda.web.dto.UpdateResourceRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling/resources")
public class ResourceController {

    private final PhysicalResourceService resources;

    public ResourceController(PhysicalResourceService resources) {
        this.resources = resources;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse created = resources.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/scheduling/resources/" + created.publicId()))
                .body(created);
    }

    @GetMapping
    public PageResponse<ResourceResponse> search(
            @RequestParam(required = false) UUID organizationPublicId,
            @RequestParam(required = false) UUID branchPublicId,
            @RequestParam(required = false) ResourceType type,
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return resources.search(organizationPublicId, branchPublicId, type, status,
                PageRequest.of(page, Math.min(size, 100), Sort.by("code").ascending()));
    }

    @GetMapping("/{publicId}")
    public ResourceResponse get(@PathVariable UUID publicId) {
        return resources.get(publicId);
    }

    @PatchMapping("/{publicId}")
    public ResourceResponse update(@PathVariable UUID publicId, @Valid @RequestBody UpdateResourceRequest request) {
        return resources.update(publicId, request);
    }

    @PatchMapping("/{publicId}/status")
    public ResourceResponse changeStatus(@PathVariable UUID publicId,
                                         @Valid @RequestBody ChangeResourceStatusRequest request) {
        return resources.changeStatus(publicId, request);
    }
}
