package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.error.NotFoundException;
import com.medicalsys.tenencia.common.security.InternalEndpoint;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.service.BranchService;
import com.medicalsys.tenencia.service.OrganizationService;
import com.medicalsys.tenencia.service.ServiceOfferingService;
import com.medicalsys.tenencia.web.dto.BillableServiceResponse;
import com.medicalsys.tenencia.web.dto.BranchRef;
import com.medicalsys.tenencia.web.dto.OrganizationRef;
import com.medicalsys.tenencia.web.dto.SchedulableServiceResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas internas consumidas por otros microservicios dentro de la red medicalsys-interna.
 *
 * <p>Devuelven proyecciones minimas: cada consumidor recibe solo lo que su caso de uso necesita y
 * nada mas. El aislamiento por organizacion se aplica igual que en las rutas publicas, tomando el
 * tenant del header {@code X-Organization-Id} que el llamante propaga.
 *
 * <p>{@code GET /internal/v1/prestaciones/{serviceOfferingId}} es contrato congelado con
 * ms-facturacion: un 404 significa que la prestacion no existe en ese tenant, y ese servicio lo
 * traduce a 422. Un cambio incompatible aqui exige una ruta {@code /internal/v2}.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final ServiceOfferingService offerings;
    private final OrganizationService organizations;
    private final BranchService branches;

    public InternalController(ServiceOfferingService offerings, OrganizationService organizations,
            BranchService branches) {
        this.offerings = offerings;
        this.organizations = organizations;
        this.branches = branches;
    }

    /** Prestacion facturable: codigo, descripcion y precio. Consumida por ms-facturacion. */
    @GetMapping("/prestaciones/{serviceOfferingId}")
    @InternalEndpoint
    public BillableServiceResponse billableService(@PathVariable UUID serviceOfferingId) {
        return offerings.getBillable(RequestContextHolder.require().organizationId(), serviceOfferingId);
    }

    /** Prestacion agendable: duracion y recurso requerido. Consumida por ms-agenda. */
    @GetMapping("/prestaciones/{serviceOfferingId}/agendable")
    @InternalEndpoint
    public SchedulableServiceResponse schedulableService(@PathVariable UUID serviceOfferingId) {
        return offerings.getSchedulable(RequestContextHolder.require().organizationId(), serviceOfferingId);
    }

    /**
     * Existencia y estado del tenant, para validar un organizationId recibido.
     *
     * <p>Solo responde por la organizacion del propio contexto. Un servicio vecino que pregunte por
     * otra recibe 404: si esta ruta contestara por cualquier tenant, bastaria con recorrer UUIDs
     * para enumerar las clinicas de la plataforma.
     */
    @GetMapping("/organizaciones/{organizationId}")
    @InternalEndpoint
    public OrganizationRef organization(@PathVariable UUID organizationId) {
        UUID contextOrganizationId = RequestContextHolder.require().organizationId();
        if (!contextOrganizationId.equals(organizationId)) {
            throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                    "La organizacion '" + organizationId + "' no existe");
        }
        return organizations.getRef(contextOrganizationId);
    }

    /** Existencia y estado de una sede del tenant. */
    @GetMapping("/sucursales/{branchId}")
    @InternalEndpoint
    public BranchRef branch(@PathVariable UUID branchId) {
        return branches.getRef(RequestContextHolder.require().organizationId(), branchId);
    }
}
