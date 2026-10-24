package com.medicalsys.facturacion.web;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.facturacion.common.error.GlobalExceptionHandler;
import com.medicalsys.facturacion.common.security.PermissionAuthorizationInterceptor;
import com.medicalsys.facturacion.common.tenant.TenantContextFilter;
import com.medicalsys.facturacion.service.InvoiceService;
import com.medicalsys.facturacion.web.controller.InvoiceController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InvoiceCancellationControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InvoiceController(mock(InvoiceService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TenantContextFilter())
            .addInterceptors(new PermissionAuthorizationInterceptor()).build();

    @Test
    void issuingDoesNotGrantCancellationAndTheCancellationPermissionIsRequired() throws Exception {
        String path = "/api/v1/facturas/" + UUID.randomUUID() + "/anulacion";
        for (String permission : new String[]{"billing.invoice.read,billing.invoice.issue", "billing.invoice.read"}) {
            mvc.perform(post(path).contentType("application/json").content("{\"reasonCode\":1}")
                    .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", UUID.randomUUID())
                    .header("X-Permissions", permission)).andExpect(status().isForbidden());
        }
        mvc.perform(post(path).contentType("application/json").content("{\"reasonCode\":1}")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", UUID.randomUUID())
                .header("X-Permissions", "billing.invoice.void")).andExpect(status().isOk());
        mvc.perform(post(path).contentType("application/json").content("{\"reasonCode\":9}")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", UUID.randomUUID())
                .header("X-Permissions", "billing.invoice.void")).andExpect(status().isBadRequest());
    }
}
