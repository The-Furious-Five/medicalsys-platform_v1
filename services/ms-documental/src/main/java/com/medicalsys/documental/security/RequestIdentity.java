package com.medicalsys.documental.security;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public record RequestIdentity(UUID organizationId, UUID userId) {

    public static RequestIdentity from(HttpServletRequest request) {
        return new RequestIdentity(
                requireUuid(request, "X-Organization-Id"),
                requireUuid(request, "X-User-Id"));
    }

    private static UUID requireUuid(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "La identidad validada por el gateway no fue propagada completamente");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "La identidad propagada por el gateway tiene un formato invalido");
        }
    }
}
