package com.medicalsys.notificaciones.common;

import com.medicalsys.notificaciones.error.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class RequestOrganization {

    public static final String HEADER = "X-Organization-Id";

    private RequestOrganization() {
    }

    public static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_ID",
                    "X-Organization-Id debe contener un UUID valido");
        }
    }
}
