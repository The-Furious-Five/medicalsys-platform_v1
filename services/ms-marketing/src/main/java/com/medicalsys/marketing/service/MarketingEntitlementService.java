package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.error.ApiException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.config.MarketingProperties.EntitlementMode;
import com.medicalsys.marketing.domain.model.RefOrganization;
import com.medicalsys.marketing.repository.RefOrganizationRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide si un tenant tiene derecho a usar marketing: el criterio C5 hecho comprobacion.
 *
 * <p>La seccion 5.12 lo dice sin rodeos: {@code plan_suscripcion.incluye_marketing} admite el valor
 * 0, y "un modulo que puede no existir para un cliente es por definicion un servicio aparte". Esa
 * frase tiene una consecuencia operativa que conviene no perder: el modulo puede estar desplegado y
 * sano y aun asi <b>no</b> corresponderle a un tenant concreto. Sin esta comprobacion, la
 * opcionalidad seria un argumento de diseno sin ninguna consecuencia en tiempo de ejecucion.
 *
 * <p>El derecho se lee de {@code ref_organization.includes_marketing}, alimentado por eventos de
 * ms-tenencia. Un tenant del que todavia no llego el plan se resuelve con {@code defaultIncluded}:
 * en el entorno local eso vale {@code true} para que nada se bloquee, y en produccion la decision es
 * explicita de quien despliega.
 */
@Service
public class MarketingEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(MarketingEntitlementService.class);

    private final RefOrganizationRepository organizations;
    private final MarketingProperties properties;
    private final Set<UUID> excludedTenants;

    public MarketingEntitlementService(RefOrganizationRepository organizations, MarketingProperties properties) {
        this.organizations = organizations;
        this.properties = properties;
        this.excludedTenants = Set.copyOf(properties.entitlement().excludedTenants() == null
                ? Set.of()
                : properties.entitlement().excludedTenants());
        if (properties.entitlement().mode() == EntitlementMode.ALWAYS_INCLUDED) {
            log.warn("El derecho a marketing no se verifica contra el plan (ENTITLEMENT_MODE=ALWAYS_INCLUDED). "
                    + "Use ENTITLEMENT_MODE=REPLICA para respetar includes_marketing del tenant.");
        }
    }

    /**
     * Exige que el tenant tenga marketing contratado.
     *
     * <p>Se llama al principio de cada caso de uso, incluidos los de solo lectura, y por eso <b>no
     * escribe nada</b>. Registrar aqui al tenant desconocido seria tentador -- es el primer sitio que
     * lo ve -- pero la mitad de las llamadas ocurren dentro de una transaccion {@code readOnly}, donde
     * Hibernate desactiva el flush: la fila no se guardaria y el fallo seria silencioso. Crear la
     * replica es tarea de {@link ReferenceDataService#requireOrganization}, que los casos de uso de
     * escritura invocan justo despues de esta comprobacion.
     *
     * @throws ApiException con {@link ErrorCode#MARKETING_NOT_INCLUDED_IN_PLAN} si el plan lo excluye
     */
    @Transactional(readOnly = true)
    public void requireEntitled(UUID organizationId) {
        if (excludedTenants.contains(organizationId)) {
            throw notIncluded(organizationId, "esta excluido por configuracion del despliegue");
        }
        if (properties.entitlement().mode() == EntitlementMode.ALWAYS_INCLUDED) {
            return;
        }
        Optional<RefOrganization> organization = organizations.findById(organizationId);
        if (organization.isEmpty()) {
            // Todavia no llego el evento de ms-tenencia con el plan de este tenant.
            if (!properties.entitlement().defaultIncluded()) {
                throw notIncluded(organizationId,
                        "su plan aun no se replico y el despliegue no lo incluye por defecto");
            }
            log.debug("Tenant {} desconocido en la replica: se asume includes_marketing=true "
                    + "hasta que llegue el evento de ms-tenencia", organizationId);
            return;
        }
        if (!organization.get().includesMarketing()) {
            throw notIncluded(organizationId, "su plan tiene includes_marketing = 0");
        }
    }

    /** Comprueba el derecho sin lanzar; util para rutas de solo lectura tolerantes y para diagnostico. */
    @Transactional(readOnly = true)
    public boolean isEntitled(UUID organizationId) {
        if (excludedTenants.contains(organizationId)) {
            return false;
        }
        if (properties.entitlement().mode() == EntitlementMode.ALWAYS_INCLUDED) {
            return true;
        }
        return organizations.findById(organizationId)
                .map(RefOrganization::includesMarketing)
                .orElseGet(properties.entitlement()::defaultIncluded);
    }

    private ApiException notIncluded(UUID organizationId, String reason) {
        log.warn("Se rechazo una operacion de marketing del tenant {}: {}", organizationId, reason);
        return new ApiException(ErrorCode.MARKETING_NOT_INCLUDED_IN_PLAN,
                "La organizacion no tiene el modulo de marketing incluido en su plan: " + reason);
    }
}
