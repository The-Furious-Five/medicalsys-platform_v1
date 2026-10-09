package com.medicalsys.tenencia.support;

import com.medicalsys.tenencia.common.security.Role;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.domain.enums.Department;
import com.medicalsys.tenencia.domain.enums.OrganizationType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
import com.medicalsys.tenencia.web.dto.CreateBranchRequest;
import com.medicalsys.tenencia.web.dto.CreateOrganizationRequest;
import com.medicalsys.tenencia.web.dto.CreateServiceOfferingRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Datos ficticios reutilizables por las pruebas. Ningun valor corresponde a una entidad real. */
public final class TestFixtures {

    /** Tenant sembrado por V2__seed_demo.sql. */
    public static final UUID DEMO_ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    /** Casa matriz sembrada por V2__seed_demo.sql. */
    public static final UUID DEMO_BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    /**
     * Prestaciones de demostracion.
     *
     * <p>Los mismos UUID que ms-facturacion declara en su catalogo local, para que su modo REMOTE
     * contra este servicio devuelva codigos y precios identicos.
     */
    public static final UUID CONSULTATION_ID = UUID.fromString("00000000-0000-4000-8000-000000000501");
    public static final UUID X_RAY_ID = UUID.fromString("00000000-0000-4000-8000-000000000502");
    public static final BigDecimal CONSULTATION_PRICE = new BigDecimal("150.00");
    public static final BigDecimal X_RAY_PRICE = new BigDecimal("250.00");

    /** Tenant que no existe en la base: sirve para comprobar el aislamiento. */
    public static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a9");

    public static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");

    private TestFixtures() {
    }

    /** Contexto de un administrador del tenant de demostracion. */
    public static RequestContext administratorContext() {
        return new RequestContext(DEMO_ORGANIZATION_ID, USER_ID, Set.of(Role.ADMINISTRATOR));
    }

    /** Contexto de una recepcionista del tenant de demostracion. */
    public static RequestContext receptionistContext() {
        return new RequestContext(DEMO_ORGANIZATION_ID, USER_ID, Set.of(Role.RECEPTIONIST));
    }

    /** Contexto de la misma identidad apuntando a una organizacion ajena. */
    public static RequestContext otherTenantContext() {
        return new RequestContext(OTHER_ORGANIZATION_ID, USER_ID, Set.of(Role.ADMINISTRATOR));
    }

    /** Contexto de un administrador de la organizacion indicada. */
    public static RequestContext contextFor(UUID organizationId) {
        return new RequestContext(organizationId, USER_ID, Set.of(Role.ADMINISTRATOR));
    }

    /** Alta de una organizacion ficticia con codigo y NIT unicos. */
    public static CreateOrganizationRequest newOrganization(String code, String nit) {
        return new CreateOrganizationRequest(code, OrganizationType.PRIVATE_CLINIC,
                "CLINICA " + code + " S.R.L.", "Clinica " + code, nit,
                "contacto@" + code.toLowerCase() + ".invalid", "+59120000009", "La Paz", Department.LA_PAZ,
                null, null, null);
    }

    /** Alta de una sede ficticia. */
    public static CreateBranchRequest newBranch(String code, boolean headquarters) {
        return new CreateBranchRequest(code, "Sucursal " + code, headquarters,
                "Av. Demostracion 200, entorno local", "La Paz", Department.LA_PAZ, null, null, null, null);
    }

    /** Alta de una prestacion ficticia. */
    public static CreateServiceOfferingRequest newOffering(String code, BigDecimal price) {
        return new CreateServiceOfferingRequest(code, "Prestacion " + code, null,
                ServiceOfferingType.CONSULTATION, null, 30, price, false, null, null, null, null);
    }
}
