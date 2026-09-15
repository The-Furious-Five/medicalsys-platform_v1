package com.medicalsys.profesionales.service;

import com.medicalsys.profesionales.domain.*;
import com.medicalsys.profesionales.repository.HealthProfessionalRepository;
import com.medicalsys.profesionales.web.CreateProfessionalRequest;
import com.medicalsys.profesionales.web.ProfessionalResponse;
import com.medicalsys.profesionales.web.UpdateProfessionalRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfessionalService {
    private final HealthProfessionalRepository professionals;
    public ProfessionalService(HealthProfessionalRepository professionals) { this.professionals = professionals; }

    @Transactional
    public ProfessionalResponse create(CreateProfessionalRequest request) {
        ensureOnePrimarySpecialty(request.specialties());
        HealthProfessional professional = new HealthProfessional();
        professional.setPublicId(UUID.randomUUID());
        professional.setUserAccountPublicId(request.userAccountId());
        professional.setDocumentType(request.documentType());
        professional.setDocumentNumber(request.documentNumber().trim());
        professional.setDocumentComplement(request.documentComplement());
        professional.setGivenNames(request.givenNames().trim());
        professional.setPaternalSurname(request.paternalSurname().trim());
        professional.setMaternalSurname(blankToNull(request.maternalSurname()));
        professional.setProfessionalType(request.professionalType());
        professional.setProfessionalLicenseNumber(request.professionalLicenseNumber().trim());
        professional.setTitle(blankToNull(request.title()));
        professional.setBiography(blankToNull(request.biography()));
        professional.setStatus(ProfessionalStatus.ACTIVE);
        for (CreateProfessionalRequest.SpecialtyRequest item : request.specialties()) {
            ProfessionalSpecialty specialty = new ProfessionalSpecialty();
            specialty.setSpecialtyCode(item.code().trim().toUpperCase(Locale.ROOT));
            specialty.setPrimary(item.primary());
            specialty.setGraduationYear(item.graduationYear());
            professional.addSpecialty(specialty);
        }
        ProfessionalOrganization organization = new ProfessionalOrganization();
        organization.setPublicId(UUID.randomUUID());
        organization.setOrganizationPublicId(request.organizationId());
        organization.setBranchPublicId(request.branchId());
        organization.setModality(request.modality() == null ? OrganizationModality.FEE_BASED : request.modality());
        organization.setCommissionPercentage(request.commissionPercentage());
        organization.setStartDate(request.startDate());
        organization.setStatus(OrganizationStatus.ACTIVE);
        professional.addOrganization(organization);
        return toResponse(professionals.saveAndFlush(professional));
    }

    @Transactional(readOnly = true)
    public List<ProfessionalResponse> list(UUID organizationId, String specialtyCode) {
        return professionals.findActiveByOrganizationAndSpecialty(organizationId,
                        blankToNull(specialtyCode == null ? null : specialtyCode.toUpperCase(Locale.ROOT)),
                        OrganizationStatus.ACTIVE, ProfessionalStatus.ACTIVE)
                .stream().map(p -> toResponse(p, organizationId)).toList();
    }

    @Transactional(readOnly = true)
    public ProfessionalResponse findActive(UUID publicId) {
        HealthProfessional professional = professionals.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado"));
        if (professional.getStatus() != ProfessionalStatus.ACTIVE || professional.getOrganizations().stream().noneMatch(link -> link.getStatus() == OrganizationStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no está activo");
        }
        return toResponse(professional);
    }

    /**
     * Ficha por identificador, sin filtrar por estado.
     *
     * <p>Es deliberadamente distinta de {@link #findActive(UUID)}: aquella es el contrato con
     * ms-agenda, que solo quiere saber si puede citar a alguien y por eso responde 404 a un
     * profesional suspendido. La ficha tiene que poder abrirse justo para ver -y revertir- esa
     * suspension, asi que devuelve al profesional en cualquier estado.
     *
     * <p>Solo es visible para una organizacion con la que el profesional tiene (o tuvo) vinculo:
     * sin ese chequeo, cualquier clinica podia abrir la ficha de un profesional de otra por id.
     */
    @Transactional(readOnly = true)
    public ProfessionalResponse get(UUID organizationId, UUID publicId) {
        HealthProfessional professional = professionals.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado"));
        boolean linkedToOrganization = professional.getOrganizations().stream()
                .anyMatch(link -> link.getOrganizationPublicId().equals(organizationId));
        if (!linkedToOrganization) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado");
        }
        return toResponse(professional, organizationId);
    }

    /** Edicion parcial: cada campo nulo se deja como estaba. */
    @Transactional
    public ProfessionalResponse update(UUID publicId, UpdateProfessionalRequest request) {
        HealthProfessional professional = professionals.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado"));
        if (request.givenNames() != null) {
            professional.setGivenNames(requireText(request.givenNames(), "givenNames"));
        }
        if (request.paternalSurname() != null) {
            professional.setPaternalSurname(requireText(request.paternalSurname(), "paternalSurname"));
        }
        if (request.maternalSurname() != null) {
            professional.setMaternalSurname(blankToNull(request.maternalSurname()));
        }
        if (request.professionalType() != null) {
            professional.setProfessionalType(request.professionalType());
        }
        if (request.title() != null) {
            professional.setTitle(blankToNull(request.title()));
        }
        if (request.biography() != null) {
            professional.setBiography(blankToNull(request.biography()));
        }
        return toResponse(professionals.saveAndFlush(professional));
    }

    /**
     * Activa, suspende o termina el vinculo de un profesional con un centro.
     *
     * <p>ENDED cierra el vinculo y por eso sella `end_date`; ACTIVE lo reabre y la limpia. Sin
     * eso, reactivar a alguien dejaria un vinculo activo con fecha de fin en el pasado, que es
     * lo que despues consulta ms-agenda.
     */
    @Transactional
    public ProfessionalResponse changeLinkStatus(UUID organizationId, UUID publicId, UUID linkPublicId, OrganizationStatus status) {
        HealthProfessional professional = professionals.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado"));
        ProfessionalOrganization link = professional.getOrganizations().stream()
                .filter(item -> item.getPublicId().equals(linkPublicId)
                        && item.getOrganizationPublicId().equals(organizationId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculo no encontrado"));
        link.setStatus(status);
        link.setEndDate(status == OrganizationStatus.ENDED ? LocalDate.now() : null);
        return toResponse(professionals.saveAndFlush(professional), organizationId);
    }

    private String requireText(String value, String field) {
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " no puede quedar vacio");
        }
        return value.trim();
    }

    private void ensureOnePrimarySpecialty(List<CreateProfessionalRequest.SpecialtyRequest> specialties) {
        long primaryCount = specialties.stream().filter(CreateProfessionalRequest.SpecialtyRequest::primary).count();
        if (primaryCount != 1 || specialties.stream().map(item -> item.code().trim().toUpperCase(Locale.ROOT)).distinct().count() != specialties.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe enviar exactamente una especialidad principal y sin códigos duplicados");
        }
    }

    private ProfessionalResponse toResponse(HealthProfessional p) {
        return toResponse(p, null);
    }

    /**
     * Arma la respuesta filtrando los vinculos a los de {@code organizationScope} cuando se
     * indica: la comision, sucursal y estado de un vinculo son datos de negocio de esa clinica,
     * no algo que otra organizacion deba poder leer solo porque comparten al mismo profesional.
     * {@code null} devuelve todos los vinculos (alta recien creada y contrato interno con agenda,
     * donde no hay una organizacion llamante que acotar).
     */
    private ProfessionalResponse toResponse(HealthProfessional p, UUID organizationScope) {
        String fullName = String.join(" ", List.of(p.getPaternalSurname(), p.getMaternalSurname() == null ? "" : p.getMaternalSurname(), p.getGivenNames())).trim().replaceAll(" +", " ");
        List<ProfessionalOrganization> organizations = organizationScope == null
                ? p.getOrganizations()
                : p.getOrganizations().stream()
                        .filter(o -> o.getOrganizationPublicId().equals(organizationScope))
                        .toList();
        return new ProfessionalResponse(p.getPublicId(), fullName, p.getProfessionalLicenseNumber(), p.getProfessionalType(), p.getStatus(),
                p.getSpecialties().stream().map(s -> new ProfessionalResponse.SpecialtyResponse(s.getSpecialtyCode(), s.isPrimary(), s.getGraduationYear())).toList(),
                organizations.stream().map(o -> new ProfessionalResponse.OrganizationResponse(o.getPublicId(), o.getOrganizationPublicId(), o.getBranchPublicId(), o.getModality(), o.getStatus(), o.getCommissionPercentage(), o.getStartDate(), o.getEndDate())).toList());
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
