package com.medicalsys.agenda.web;

import com.medicalsys.agenda.config.RequestContextHolder;
import com.medicalsys.agenda.security.RequiresPermission;
import com.medicalsys.agenda.service.ScheduleBlockService;
import com.medicalsys.agenda.web.dto.BlockResponse;
import com.medicalsys.agenda.web.dto.CreateBlockRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling/blocks")
public class BlockController {

    private final ScheduleBlockService blocks;

    public BlockController(ScheduleBlockService blocks) {
        this.blocks = blocks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("scheduling.block.create")
    public BlockResponse create(@Valid @RequestBody CreateBlockRequest request) {
        return blocks.create(request);
    }

    @GetMapping
    @RequiresPermission("scheduling.block.read")
    public List<BlockResponse> search(
            @RequestParam(required = false) UUID healthProfessionalPublicId,
            @RequestParam(required = false) UUID branchPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID organizationPublicId = RequestContextHolder.require().organizationId();
        return blocks.search(organizationPublicId, healthProfessionalPublicId, branchPublicId, from, to);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("scheduling.block.delete")
    public void delete(@PathVariable Long id) {
        blocks.delete(RequestContextHolder.require().organizationId(), id);
    }
}
