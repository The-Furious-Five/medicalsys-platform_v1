package com.medicalsys.documental.api;

import com.medicalsys.documental.api.dto.GenerateInformedConsentRequest;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.api.dto.RejectInformedConsentRequest;
import com.medicalsys.documental.api.dto.SignInformedConsentRequest;
import com.medicalsys.documental.security.RequestIdentity;
import com.medicalsys.documental.security.RequiresPermission;
import com.medicalsys.documental.service.InformedConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/consentimientos")
public class InformedConsentController {

    private final InformedConsentService service;

    public InformedConsentController(InformedConsentService service) {
        this.service = service;
    }

    @PostMapping("/generar")
    @RequiresPermission("documents.consent.generate")
    public ResponseEntity<InformedConsentResponse> generate(
            @Valid @RequestBody GenerateInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        InformedConsentResponse response = service.generate(request, identity.organizationId(), identity.userId());
        return ResponseEntity.created(URI.create("/api/v1/consentimientos/" + response.consentId())).body(response);
    }

    @PostMapping("/{consentId}/otp")
    @RequiresPermission("documents.consent.request_otp")
    public InformedConsentResponse requestOtp(@PathVariable UUID consentId, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.requestOtp(consentId, identity.organizationId(), identity.userId(),
                clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/{consentId}/firma")
    @RequiresPermission("documents.consent.sign")
    public InformedConsentResponse sign(@PathVariable UUID consentId,
            @Valid @RequestBody SignInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.sign(consentId, identity.organizationId(), identity.userId(), request,
                clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/{consentId}/rechazo")
    @RequiresPermission("documents.consent.reject")
    public InformedConsentResponse reject(@PathVariable UUID consentId,
            @Valid @RequestBody RejectInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.reject(consentId, identity.organizationId(), identity.userId(), request);
    }

    @GetMapping("/{consentId}")
    @RequiresPermission("documents.consent.read")
    public InformedConsentResponse get(@PathVariable UUID consentId, HttpServletRequest servletRequest) {
        return service.get(consentId, RequestIdentity.from(servletRequest).organizationId());
    }

    @GetMapping
    @RequiresPermission("documents.consent.read")
    public List<InformedConsentResponse> listByPatient(@RequestParam UUID patientId,
            HttpServletRequest servletRequest) {
        return service.listByPatient(patientId, RequestIdentity.from(servletRequest).organizationId());
    }

    /**
     * IP de quien solicita el OTP o firma: queda como evidencia del consentimiento. El gateway pone la IP
     * real del cliente en X-Client-Ip; getRemoteAddr() seria la del gateway.
     */
    static String clientIp(HttpServletRequest request) {
        // Solo X-Client-Ip, que pone el gateway y descarta si lo manda el cliente; X-Forwarded-For no es confiable.
        String clientIp = request.getHeader("X-Client-Ip");
        return clientIp != null && !clientIp.isBlank() ? clientIp.trim() : request.getRemoteAddr();
    }
}
