package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.PhysicalResource;
import com.medicalsys.agenda.domain.ResourceStatus;
import com.medicalsys.agenda.domain.ResourceType;
import com.medicalsys.agenda.repository.PhysicalResourceRepository;
import com.medicalsys.agenda.web.dto.ChangeResourceStatusRequest;
import com.medicalsys.agenda.web.dto.CreateResourceRequest;
import com.medicalsys.agenda.web.dto.PageResponse;
import com.medicalsys.agenda.web.dto.ResourceResponse;
import com.medicalsys.agenda.web.dto.UpdateResourceRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PhysicalResourceService {

    private final PhysicalResourceRepository resources;
    private final SchedulingMapper mapper;

    public PhysicalResourceService(PhysicalResourceRepository resources, SchedulingMapper mapper) {
        this.resources = resources;
        this.mapper = mapper;
    }

    @Transactional
    public ResourceResponse create(CreateResourceRequest request) {
        String code = request.code().trim().toUpperCase();
        if (resources.existsByOrganizationPublicIdAndCodeIgnoreCase(request.organizationPublicId(), code)) {
            throw ApiException.conflict("Ya existe un recurso con el codigo " + code + " en la organizacion");
        }

        PhysicalResource resource = new PhysicalResource();
        resource.setOrganizationPublicId(request.organizationPublicId());
        resource.setBranchPublicId(request.branchPublicId());
        resource.setCode(code);
        resource.setName(request.name().trim());
        resource.setType(request.type());
        resource.setFloor(StringUtils.hasText(request.floor()) ? request.floor() : null);
        resource.setPeopleCapacity(request.peopleCapacity() == null ? 1 : request.peopleCapacity());
        resource.setPreparationMinutes(request.preparationMinutes() == null ? 0 : request.preparationMinutes());
        resource.setRequiresSterilization(Boolean.TRUE.equals(request.requiresSterilization()));
        resource.setEquipment(StringUtils.hasText(request.equipment()) ? request.equipment() : null);

        PhysicalResource saved = OverlapTranslator.translate(() -> resources.saveAndFlush(resource));
        return mapper.toResourceResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> search(UUID organizationPublicId, UUID branchPublicId, ResourceType type,
                                                 ResourceStatus status, Pageable pageable) {
        Specification<PhysicalResource> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationPublicId != null) {
                predicates.add(builder.equal(root.get("organizationPublicId"), organizationPublicId));
            }
            if (branchPublicId != null) {
                predicates.add(builder.equal(root.get("branchPublicId"), branchPublicId));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(resources.findAll(specification, pageable), mapper::toResourceResponse);
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(UUID publicId) {
        return mapper.toResourceResponse(require(publicId));
    }

    @Transactional
    public ResourceResponse update(UUID publicId, UpdateResourceRequest request) {
        PhysicalResource resource = require(publicId);
        if (StringUtils.hasText(request.name())) {
            resource.setName(request.name().trim());
        }
        if (request.type() != null) {
            resource.setType(request.type());
        }
        if (request.floor() != null) {
            resource.setFloor(StringUtils.hasText(request.floor()) ? request.floor() : null);
        }
        if (request.peopleCapacity() != null) {
            resource.setPeopleCapacity(request.peopleCapacity());
        }
        if (request.preparationMinutes() != null) {
            resource.setPreparationMinutes(request.preparationMinutes());
        }
        if (request.requiresSterilization() != null) {
            resource.setRequiresSterilization(Boolean.TRUE.equals(request.requiresSterilization()));
        }
        if (request.equipment() != null) {
            resource.setEquipment(StringUtils.hasText(request.equipment()) ? request.equipment() : null);
        }
        return mapper.toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse changeStatus(UUID publicId, ChangeResourceStatusRequest request) {
        PhysicalResource resource = require(publicId);
        resource.setStatus(request.status());
        return mapper.toResourceResponse(resource);
    }

    @Transactional(readOnly = true)
    public PhysicalResource require(UUID publicId) {
        return resources.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("No existe el recurso fisico " + publicId));
    }
}
