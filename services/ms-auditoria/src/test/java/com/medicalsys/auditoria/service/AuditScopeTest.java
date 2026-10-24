package com.medicalsys.auditoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.auditoria.common.error.ForbiddenException;
import com.medicalsys.auditoria.common.tenant.TestRequestContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ISO-01: el OSI lee la bitacora de toda la plataforma; nadie mas sale de su organizacion. */
class AuditScopeTest {

    private static final UUID OWN = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TestRequestContext.clear();
    }

    @Test
    void anAuditorOnlySeesItsOwnOrganization() {
        TestRequestContext.withPermissions(OWN, UUID.randomUUID(), "audit.log.read");

        assertThat(AuditScope.resolve(null)).isEqualTo(OWN);
        assertThat(AuditScope.resolve(OWN)).isEqualTo(OWN);
        assertThatThrownBy(() -> AuditScope.resolve(OTHER)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void thePlatformScopeSeesAllOrTheOneItAsksFor() {
        TestRequestContext.withPermissions(OWN, UUID.randomUUID(), "audit.log.read", AuditScope.PLATFORM_PERMISSION);

        assertThat(AuditScope.resolve(null)).isNull();
        assertThat(AuditScope.resolve(OTHER)).isEqualTo(OTHER);
        assertThat(AuditScope.resolveSingle(null)).isEqualTo(OWN);
    }
}
