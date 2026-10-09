package com.medicalsys.facturacion.service;

import com.medicalsys.facturacion.client.TaxAuthorityGateway;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.CufdGrant;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.CuisGrant;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.PointOfSaleIdentity;
import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.common.error.NotFoundException;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.domain.enums.PointOfSaleStatus;
import com.medicalsys.facturacion.domain.enums.PointOfSaleType;
import com.medicalsys.facturacion.domain.model.BillingConfiguration;
import com.medicalsys.facturacion.domain.model.Cufd;
import com.medicalsys.facturacion.domain.model.Cuis;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import com.medicalsys.facturacion.repository.BillingConfigurationRepository;
import com.medicalsys.facturacion.repository.CufdRepository;
import com.medicalsys.facturacion.repository.CuisRepository;
import com.medicalsys.facturacion.repository.PointOfSaleRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el contexto fiscal necesario para emitir: configuracion, punto de venta y CUFD vigente.
 *
 * <p>En el entorno local, con {@code medicalsys.billing.demo-setup.enabled=true}, crea por si mismo
 * lo que falte con valores ficticios. Es lo que permite levantar el stack desde una base vacia y
 * emitir la primera factura sin ningun paso manual, que es la "configuracion DEMO" comprometida en
 * el Excel del sprint.
 *
 * <p>Con el interruptor apagado el servicio no inventa nada: exige que un administrador haya cargado
 * la configuracion y devuelve 422 si no existe. Asi el mismo codigo sirve para un entorno real.
 *
 * <p>El CUFD, en cambio, se renueva siempre a traves del {@link TaxAuthorityGateway}: es una
 * responsabilidad del sistema fiscal tanto en la simulacion como en una integracion real.
 */
@Service
public class FiscalSetupService {

    private static final Logger log = LoggerFactory.getLogger(FiscalSetupService.class);

    private final BillingConfigurationRepository configurations;
    private final PointOfSaleRepository pointsOfSale;
    private final CuisRepository cuisCodes;
    private final CufdRepository cufdCodes;
    private final InvoiceNumberGenerator invoiceNumbers;
    private final TaxAuthorityGateway taxAuthority;
    private final BillingProperties properties;

    public FiscalSetupService(BillingConfigurationRepository configurations, PointOfSaleRepository pointsOfSale,
            CuisRepository cuisCodes, CufdRepository cufdCodes, InvoiceNumberGenerator invoiceNumbers,
            TaxAuthorityGateway taxAuthority, BillingProperties properties) {
        this.configurations = configurations;
        this.pointsOfSale = pointsOfSale;
        this.cuisCodes = cuisCodes;
        this.cufdCodes = cufdCodes;
        this.invoiceNumbers = invoiceNumbers;
        this.taxAuthority = taxAuthority;
        this.properties = properties;
    }

    /**
     * Contexto fiscal listo para emitir un documento.
     *
     * @param configuration configuracion fiscal del tenant
     * @param pointOfSale   caja emisora
     * @param cufd          codigo de facturacion diaria vigente
     */
    public record FiscalContext(BillingConfiguration configuration, PointOfSale pointOfSale, Cufd cufd) {

        /** Identidad fiscal que acompana toda solicitud al sistema fiscal. */
        public PointOfSaleIdentity identity() {
            return new PointOfSaleIdentity(configuration.getNit(), pointOfSale.getTaxBranchCode(),
                    pointOfSale.getPointOfSaleCode());
        }
    }

    /**
     * Selección explicita de caja emisora dentro de la organizacion.
     *
     * @param taxBranchCode   codigo de sucursal del SIN; 0 es casa matriz
     * @param pointOfSaleCode codigo del punto de venta dentro de esa sucursal
     */
    public record PointOfSaleSelector(int taxBranchCode, int pointOfSaleCode) {
    }

    /**
     * Devuelve el contexto fiscal del tenant, creando lo que falte si el setup de demostracion esta
     * habilitado.
     *
     * @param context  identidad propagada por el gateway
     * @param selector caja solicitada; {@code null} usa la primera caja activa de la organizacion
     * @param at       instante de emision, que decide que CUFD esta vigente
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FiscalContext resolve(RequestContext context, PointOfSaleSelector selector, Instant at) {
        BillingConfiguration configuration = resolveConfiguration(context.organizationId());
        PointOfSale pointOfSale = resolvePointOfSale(context.organizationId(), selector);
        Cufd cufd = resolveCufd(configuration, pointOfSale, at);
        return new FiscalContext(configuration, pointOfSale, cufd);
    }

    private BillingConfiguration resolveConfiguration(UUID organizationId) {
        Optional<BillingConfiguration> existing = configurations.findByOrganizationPublicId(organizationId);
        if (existing.isPresent()) {
            BillingConfiguration configuration = existing.get();
            if (!configuration.allowsIssuing()) {
                throw new BusinessRuleException(ErrorCode.BILLING_CONFIGURATION_NOT_READY,
                        "La configuracion fiscal de la organizacion esta en estado "
                                + configuration.getStatus() + " y no permite emitir");
            }
            return configuration;
        }
        BillingProperties.Billing.DemoSetup demo = demoSetupOrFail(
                "La organizacion no tiene configuracion fiscal cargada");
        BillingConfiguration created = configurations.save(BillingConfiguration.demo(organizationId, demo.nit(),
                demo.legalName(), properties.taxAuthority().demoCaption()));
        log.warn("Configuracion fiscal de DEMOSTRACION creada automaticamente para la organizacion {} "
                + "(nit ficticio {}). Desactive medicalsys.billing.demo-setup.enabled en un entorno real.",
                organizationId, demo.nit());
        return created;
    }

    private PointOfSale resolvePointOfSale(UUID organizationId, PointOfSaleSelector selector) {
        if (selector != null) {
            PointOfSale requested = pointsOfSale
                    .findByOrganizationPublicIdAndTaxBranchCodeAndPointOfSaleCode(organizationId,
                            selector.taxBranchCode(), selector.pointOfSaleCode())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.POINT_OF_SALE_NOT_FOUND,
                            "El punto de venta " + selector.taxBranchCode() + "/" + selector.pointOfSaleCode()
                                    + " no existe en esta organizacion"));
            if (!requested.allowsIssuing()) {
                throw new BusinessRuleException(ErrorCode.POINT_OF_SALE_NOT_ACTIVE,
                        "El punto de venta solicitado esta " + requested.getStatus() + " y no puede emitir");
            }
            return requested;
        }
        List<PointOfSale> active = pointsOfSale
                .findByOrganizationPublicIdAndStatusOrderByPointOfSaleCodeAsc(organizationId,
                        PointOfSaleStatus.ACTIVE);
        if (!active.isEmpty()) {
            return active.getFirst();
        }
        BillingProperties.Billing.DemoSetup demo = demoSetupOrFail(
                "La organizacion no tiene ningun punto de venta activo");
        // Sin ms-tenencia integrada no hay UUID de sucursal que consultar: la caja de demostracion se
        // ancla a la propia organizacion, que es un identificador estable y evidentemente local.
        PointOfSale created = pointsOfSale.save(PointOfSale.open(organizationId, organizationId,
                demo.taxBranchCode(), demo.pointOfSaleCode(), demo.pointOfSaleName(),
                PointOfSaleType.COLLECTION_DESK));
        invoiceNumbers.initializeCounters(created.getId());
        log.warn("Punto de venta de DEMOSTRACION {}/{} creado automaticamente para la organizacion {}",
                demo.taxBranchCode(), demo.pointOfSaleCode(), organizationId);
        return created;
    }

    private Cufd resolveCufd(BillingConfiguration configuration, PointOfSale pointOfSale, Instant at) {
        Optional<Cufd> valid = cufdCodes.findValidAt(pointOfSale.getId(), at);
        if (valid.isPresent()) {
            return valid.get();
        }
        Cuis cuis = resolveCuis(configuration, pointOfSale, at);
        PointOfSaleIdentity identity = new PointOfSaleIdentity(configuration.getNit(),
                pointOfSale.getTaxBranchCode(), pointOfSale.getPointOfSaleCode());
        CufdGrant grant = taxAuthority.requestCufd(identity, cuis.getCode(), at);
        Cufd cufd = cufdCodes.save(Cufd.register(pointOfSale, cuis, grant.code(), grant.controlCode(),
                properties.billing().demoSetup().address(), grant.validFrom(), grant.validUntil()));
        log.info("CUFD renovado para el punto de venta {} con vigencia hasta {}", pointOfSale.getId(),
                grant.validUntil());
        return cufd;
    }

    private Cuis resolveCuis(BillingConfiguration configuration, PointOfSale pointOfSale, Instant at) {
        return cuisCodes.findActiveOn(pointOfSale.getId(), at.atOffset(ZoneOffset.UTC).toLocalDate())
                .orElseGet(() -> {
                    PointOfSaleIdentity identity = new PointOfSaleIdentity(configuration.getNit(),
                            pointOfSale.getTaxBranchCode(), pointOfSale.getPointOfSaleCode());
                    CuisGrant grant = taxAuthority.requestCuis(identity, at);
                    log.info("CUIS solicitado para el punto de venta {} con vigencia hasta {}",
                            pointOfSale.getId(), grant.validUntil());
                    return cuisCodes.save(Cuis.register(pointOfSale, grant.code(), grant.validUntil()));
                });
    }

    private BillingProperties.Billing.DemoSetup demoSetupOrFail(String reason) {
        BillingProperties.Billing.DemoSetup demo = properties.billing().demoSetup();
        if (!demo.enabled()) {
            throw new BusinessRuleException(ErrorCode.BILLING_CONFIGURATION_NOT_READY,
                    reason + " y el alta automatica de demostracion esta desactivada");
        }
        return demo;
    }
}
