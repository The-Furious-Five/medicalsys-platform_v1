package com.medicalsys.identidad.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * El cliente de la bitácora se arma sin depender de un RestClient.Builder autoconfigurado: en el
 * contenedor no existe ese bean y, cuando la configuración lo pedía, ms-identidad no arrancaba.
 */
class AuditTrailConfigTest {

    @Test
    @DisplayName("el cliente de auditoría se construye sin beans de Spring, con o sin bitácora configurada")
    void buildsTheClientWithoutAutoconfiguredBuilder() {
        AuditTrailConfig config = new AuditTrailConfig();

        RestClient enabled = config.auditRestClient(new AuditTrailProperties("http://ms-auditoria:8091", null, null, null));
        RestClient disabled = config.auditRestClient(new AuditTrailProperties("", null, null, null));

        assertThat(enabled).isNotNull();
        assertThat(disabled).isNotNull();
    }
}
