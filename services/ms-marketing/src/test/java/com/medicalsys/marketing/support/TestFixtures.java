package com.medicalsys.marketing.support;

import com.medicalsys.marketing.common.security.Role;
import com.medicalsys.marketing.common.tenant.RequestContext;
import java.util.Set;
import java.util.UUID;

/** Identidades y constantes compartidas por las pruebas. */
public final class TestFixtures {

    /** Tenant de demostracion; el mismo que siembra ms-tenencia y el seeder de este servicio. */
    public static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    /** Segundo tenant, para comprobar que no se filtra nada entre organizaciones. */
    public static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a9");

    public static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");

    private TestFixtures() {
    }

    /** Contexto de un administrador del tenant de demostracion. */
    public static RequestContext administrator() {
        return new RequestContext(ORGANIZATION_ID, USER_ID, Set.of(Role.ADMINISTRATOR));
    }

    /** Contexto de un administrador de otro tenant. */
    public static RequestContext otherAdministrator() {
        return new RequestContext(OTHER_ORGANIZATION_ID, USER_ID, Set.of(Role.ADMINISTRATOR));
    }
}
