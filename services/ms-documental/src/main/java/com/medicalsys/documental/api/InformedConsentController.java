package com.medicalsys.documental.api;

import com.medicalsys.documental.api.dto.GenerateInformedConsentRequest;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.api.dto.RejectInformedConsentRequest;
import com.medicalsys.documental.api.dto.SignInformedConsentRequest;
import com.medicalsys.documental.security.RequestIdentity;
import com.medicalsys.documental.security.RequiresRole;
import com.medicalsys.documental.security.Role;
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
@RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
public class InformedConsentController {

    private final InformedConsentService service;

    public InformedConsentController(InformedConsentService service) {
        this.service = service;
    }

    @PostMapping("/generar")
    public ResponseEntity<InformedConsentResponse> generate(
            @Valid @RequestBody GenerateInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        InformedConsentResponse response = service.generate(request, identity.organizationId(), identity.userId());
        return ResponseEntity.created(URI.create("/api/v1/consentimientos/" + response.consentId())).body(response);
    }

    @PostMapping("/{consentId}/otp")
    public InformedConsentResponse requestOtp(@PathVariable UUID consentId, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.requestOtp(consentId, identity.organizationId(), identity.userId(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/{consentId}/firma")
    public InformedConsentResponse sign(@PathVariable UUID consentId,
            @Valid @RequestBody SignInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.sign(consentId, identity.organizationId(), identity.userId(), request,
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/{consentId}/rechazo")
    public InformedConsentResponse reject(@PathVariable UUID consentId,
            @Valid @RequestBody RejectInformedConsentRequest request, HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        return service.reject(consentId, identity.organizationId(), identity.userId(), request);
    }

    @GetMapping("/{consentId}")
    public InformedConsentResponse get(@PathVariable UUID consentId, HttpServletRequest servletRequest) {
        return service.get(consentId, RequestIdentity.from(servletRequest).organizationId());
    }

    @GetMapping
    public List<InformedConsentResponse> listByPatient(@RequestParam UUID patientId,
            HttpServletRequest servletRequest) {
        return service.listByPatient(patientId, RequestIdentity.from(servletRequest).organizationId());
    }
}
