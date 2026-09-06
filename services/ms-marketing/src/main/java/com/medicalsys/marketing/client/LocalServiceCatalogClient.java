package com.medicalsys.marketing.client;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Resuelve la prestacion con datos ficticios deterministas, sin salir del proceso.
 *
 * <p>Reproduce el mismo enfoque que usa ms-facturacion mientras ms-tenencia no publique su endpoint
 * interno en el entorno de trabajo: precio derivado del UUID, estable entre ejecuciones. Al pasar a
 * {@code TENANCY_MODE=REMOTE} este componente deja de registrarse.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.tenancy", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalServiceCatalogClient implements ServiceCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(LocalServiceCatalogClient.class);

    /** Precios ficticios entre 50 y 549 bolivianos, en tramos de 50. */
    private static final long PRICE_STEPS = 10L;
    private static final BigDecimal PRICE_STEP_BOB = new BigDecimal("50.00");

    public LocalServiceCatalogClient() {
        log.warn("El catalogo de prestaciones se resuelve con datos ficticios locales (TENANCY_MODE=LOCAL). "
                + "Para consultar ms-tenencia use TENANCY_MODE=REMOTE.");
    }

    @Override
    public Optional<ServiceOfferingRef> offering(UUID organizationId, UUID serviceOfferingId) {
        long seed = Math.abs(serviceOfferingId.getLeastSignificantBits());
        BigDecimal price = PRICE_STEP_BOB.multiply(BigDecimal.valueOf(1 + seed % PRICE_STEPS));
        String name = "PRESTACION DEMO " + serviceOfferingId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return Optional.of(new ServiceOfferingRef(serviceOfferingId, name, price, true));
    }
}
