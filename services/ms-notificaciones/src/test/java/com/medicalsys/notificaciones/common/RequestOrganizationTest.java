package com.medicalsys.notificaciones.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.medicalsys.notificaciones.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestOrganizationTest {

    @Test
    void parsesValidUuid() {
        UUID expected = UUID.fromString("10000000-0000-0000-0000-000000000001");
        assertEquals(expected, RequestOrganization.parse(expected.toString()));
    }

    @Test
    void rejectsMalformedUuid() {
        ApiException error = assertThrows(ApiException.class, () -> RequestOrganization.parse("not-an-id"));
        assertEquals("INVALID_ORGANIZATION_ID", error.code());
        assertEquals(400, error.status().value());
    }

    @Test
    void rejectsNullHeader() {
        ApiException error = assertThrows(ApiException.class, () -> RequestOrganization.parse(null));
        assertEquals("INVALID_ORGANIZATION_ID", error.code());
    }
}
