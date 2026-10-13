package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.UserStatus;
import java.util.List;
import java.util.UUID;

/** DTO minimo que consumen otros microservicios: sin credenciales ni datos de bloqueo. */
public record InternalUserResponse(
        UUID publicId,
        String userCode,
        UUID organizationPublicId,
        String email,
        String fullName,
        UserStatus status,
        List<String> roles) {
}
