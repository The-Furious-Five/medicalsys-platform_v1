package com.medicalsys.facturacion.client;

import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.config.BillingProperties;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Resuelve la prestacion contra el catalogo de demostracion declarado en la configuracion.
 *
 * <p>Existe para que la rebanada vertical funcione mientras ms-tenencia no publique su ruta interna.
 * La lista reproduce las prestaciones ficticias que ms-tenencia siembra en el entorno local, con sus
 * precios: una prestacion que no este en ella se rechaza con 422, igual que haria el propietario.
 *
 * <p>Al pasar a {@code TENANCY_MODE=REMOTE} este componente deja de registrarse.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.tenancy", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalServiceCatalogClient implements ServiceCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(LocalServiceCatalogClient.class);

    private final Map<UUID, BillableService> offerings;

    public LocalServiceCatalogClient(BillingProperties properties) {
        this.offerings = properties.tenancy().local().offerings().stream()
                .map(LocalServiceCatalogClient::toBillableService)
                .collect(Collectors.toUnmodifiableMap(BillableService::id, Function.identity()));
        log.warn("Las prestaciones y sus precios se resuelven con el catalogo local de demostracion "
                + "(TENANCY_MODE=LOCAL, {} prestaciones). Para consultar ms-tenencia use TENANCY_MODE=REMOTE.",
                offerings.size());
    }

    @Override
    public BillableService billableService(UUID organizationId, UUID serviceOfferingId) {
        BillableService offering = offerings.get(serviceOfferingId);
        if (offering == null) {
            throw new BusinessRuleException(ErrorCode.SERVICE_OFFERING_NOT_BILLABLE,
                    "La prestacion '" + serviceOfferingId + "' no existe en el catalogo vigente");
        }
        return offering;
    }

    private static BillableService toBillableService(BillingProperties.Tenancy.Offering offering) {
        return new BillableService(offering.id(), offering.code(), offering.name(), offering.basePriceBob(),
                offering.taxProductCode(), offering.taxUnitCode(), true);
    }
}
