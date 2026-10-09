package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.ScheduleBlock;
import com.medicalsys.agenda.repository.ScheduleBlockRepository;
import com.medicalsys.agenda.web.dto.BlockResponse;
import com.medicalsys.agenda.web.dto.CreateBlockRequest;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleBlockService {

    private final ScheduleBlockRepository blocks;
    private final PhysicalResourceService resourceService;
    private final SchedulingMapper mapper;

    public ScheduleBlockService(ScheduleBlockRepository blocks, PhysicalResourceService resourceService,
                                SchedulingMapper mapper) {
        this.blocks = blocks;
        this.resourceService = resourceService;
        this.mapper = mapper;
    }

    @Transactional
    public BlockResponse create(CreateBlockRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("endsAt debe ser posterior a startsAt");
        }
        if (request.healthProfessionalPublicId() == null
                && request.physicalResourcePublicId() == null
                && request.branchPublicId() == null) {
            throw ApiException.badRequest(
                    "El bloqueo debe apuntar a un profesional, a un recurso o a una sucursal");
        }

        ScheduleBlock block = new ScheduleBlock();
        block.setOrganizationPublicId(request.organizationPublicId());
        block.setHealthProfessionalPublicId(request.healthProfessionalPublicId());
        block.setBranchPublicId(request.branchPublicId());
        block.setType(request.type());
        block.setStartsAt(request.startsAt());
        block.setEndsAt(request.endsAt());
        block.setReason(request.reason());
        block.setCreatedByUserPublicId(request.createdByUserPublicId());
        if (request.physicalResourcePublicId() != null) {
            block.setPhysicalResource(resourceService.require(request.physicalResourcePublicId()));
        }
        return mapper.toBlockResponse(blocks.saveAndFlush(block));
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> search(UUID organizationPublicId, UUID healthProfessionalPublicId,
                                      UUID branchPublicId, OffsetDateTime from, OffsetDateTime to) {
        Specification<ScheduleBlock> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationPublicId"), organizationPublicId));
            if (healthProfessionalPublicId != null) {
                predicates.add(builder.equal(root.get("healthProfessionalPublicId"), healthProfessionalPublicId));
            }
            if (branchPublicId != null) {
                predicates.add(builder.equal(root.get("branchPublicId"), branchPublicId));
            }
            if (from != null) {
                predicates.add(builder.greaterThan(root.get("endsAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("startsAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return blocks.findAll(specification, Sort.by("startsAt").ascending()).stream()
                .map(mapper::toBlockResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID organizationPublicId, Long id) {
        ScheduleBlock block = blocks.findById(id)
                .filter(candidate -> candidate.getOrganizationPublicId().equals(organizationPublicId))
                .orElseThrow(() -> ApiException.notFound("No existe el bloqueo " + id));
        blocks.delete(block);
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlock> findAffecting(UUID organizationPublicId, UUID healthProfessionalPublicId,
                                             UUID branchPublicId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return blocks.findAffecting(organizationPublicId, healthProfessionalPublicId, branchPublicId,
                startsAt, endsAt);
    }
}
