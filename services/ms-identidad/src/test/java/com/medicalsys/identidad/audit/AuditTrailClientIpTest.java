package com.medicalsys.identidad.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** LOG-01: la bitácora guarda la IP del usuario que pone el gateway, no la del gateway. */
class AuditTrailClientIpTest {

    @Test
    @DisplayName("usa la IP del usuario que el gateway deja en X-Client-Ip")
    void usesTheClientIpFromTheGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.7");
        request.addHeader("X-Client-Ip", "181.115.40.7");

        assertThat(AuditTrail.clientIp(request)).isEqualTo("181.115.40.7");
    }

    @Test
    @DisplayName("sin X-Client-Ip, la IP de la conexión; X-Forwarded-For no se cree")
    void fallsBackToTheConnectionAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.7");
        request.addHeader("X-Forwarded-For", "10.6.6.6");

        assertThat(AuditTrail.clientIp(request)).isEqualTo("172.19.0.7");
        assertThat(AuditTrail.clientIp(null)).isNull();
    }
}
