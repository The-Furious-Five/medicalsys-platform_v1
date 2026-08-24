package com.medicalsys.facturacion.support;

import com.medicalsys.facturacion.client.BillableService;
import com.medicalsys.facturacion.client.FiscalCustomer;
import com.medicalsys.facturacion.common.security.Role;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.domain.model.BillingConfiguration;
import com.medicalsys.facturacion.domain.model.Cufd;
import com.medicalsys.facturacion.domain.model.Cuis;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import com.medicalsys.facturacion.domain.enums.PointOfSaleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Datos ficticios reutilizables por las pruebas. Ningun valor corresponde a una persona real. */
public final class TestFixtures {

    public static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a9");
    public static final UUID BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    public static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    public static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    public static final UUID APPOINTMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000a11");
    public static final UUID ENCOUNTER_ID = UUID.fromString("00000000-0000-4000-8000-000000000d11");

    /** Prestaciones del catalogo de demostracion, iguales a las de application.yaml. */
    public static final UUID CONSULTATION_ID = UUID.fromString("00000000-0000-4000-8000-000000000501");
    public static final UUID X_RAY_ID = UUID.fromString("00000000-0000-4000-8000-000000000502");
    public static final BigDecimal CONSULTATION_PRICE = new BigDecimal("150.00");
    public static final BigDecimal X_RAY_PRICE = new BigDecimal("250.00");

    /** Instante fijo de emision, para que el CUF simulado sea comprobable. */
    public static final Instant ISSUED_AT = Instant.parse("2026-08-24T14:00:00Z");

    private TestFixtures() {
    }

    /** Contexto de una recepcionista autenticada del tenant de demostracion. */
    public static RequestContext receptionistContext() {
        return new RequestContext(ORGANIZATION_ID, USER_ID, Set.of(Role.RECEPTIONIST));
    }

    /** Contexto de la misma identidad en otra organizacion. */
    public static RequestContext otherTenantContext() {
        return new RequestContext(OTHER_ORGANIZATION_ID, USER_ID, Set.of(Role.RECEPTIONIST));
    }

    /** DTO fiscal ficticio del paciente de demostracion. */
    public static FiscalCustomer fiscalCustomer() {
        return new FiscalCustomer(PATIENT_ID, "CI", "12345678", null, "PACIENTE DEMO UNO", null);
    }

    /** Consulta medica del catalogo de demostracion. */
    public static BillableService consultation() {
        return new BillableService(CONSULTATION_ID, "CONSULTATION", "Consulta medica general (demostracion)",
                CONSULTATION_PRICE, "99100", 58, true);
    }

    /** Estudio de rayos X del catalogo de demostracion. */
    public static BillableService xRay() {
        return new BillableService(X_RAY_ID, "X_RAY", "Estudio de rayos X (demostracion)", X_RAY_PRICE,
                "99200", 58, true);
    }

    /** Configuracion fiscal de demostracion persistida. */
    public static BillingConfiguration billingConfiguration() {
        BillingConfiguration configuration = BillingConfiguration.demo(ORGANIZATION_ID, "0000000000",
                "CLINICA DEMO MEDICALSYS", "NO VALIDA TRIBUTARIAMENTE");
        ReflectionTestUtils.setField(configuration, "id", 1L);
        return configuration;
    }

    /** Punto de venta persistido, con identificador tecnico asignado. */
    public static PointOfSale pointOfSale() {
        PointOfSale pointOfSale = PointOfSale.open(ORGANIZATION_ID, BRANCH_ID, 0, 1, "CAJA DEMO",
                PointOfSaleType.COLLECTION_DESK);
        ReflectionTestUtils.setField(pointOfSale, "id", 1L);
        return pointOfSale;
    }

    /** CUFD vigente ficticio del punto de venta. */
    public static Cufd cufd(PointOfSale pointOfSale) {
        Cuis cuis = Cuis.register(pointOfSale, "DEMO-CUIS0001", LocalDate.of(2027, 1, 1));
        ReflectionTestUtils.setField(cuis, "id", 1L);
        Cufd cufd = Cufd.register(pointOfSale, cuis, "DEMO-CUFD0001", "DEMO-CTRL", "DIRECCION DEMO",
                ISSUED_AT.minusSeconds(3600), ISSUED_AT.plusSeconds(3600));
        ReflectionTestUtils.setField(cufd, "id", 1L);
        return cufd;
    }
}
