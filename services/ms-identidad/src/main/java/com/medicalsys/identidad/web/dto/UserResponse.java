package com.medicalsys.identidad.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicalsys.identidad.domain.SecurityLevel;
import com.medicalsys.identidad.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID publicId,
        String userCode,
        String username,
        UUID organizationPublicId,
        String email,
        String fullName,
        String phoneE164,
        String language,
        UserStatus status,
        SecurityLevel clearance,
        boolean mfaEnabled,
        int failedAttempts,
        Instant lockedUntil,
        Instant lastAccessAt,
        Instant createdAt,
        Instant updatedAt,
        List<RoleAssignmentResponse> roles,
        // Solo en la respuesta del alta cuando el sistema genera la contraseña temporal: se muestra
        // una vez al administrador, que la entrega por un canal seguro. Nunca se guarda ni se loguea.
        @JsonInclude(JsonInclude.Include.NON_NULL) String temporaryPassword) {

    public UserResponse withTemporaryPassword(String password) {
        return new UserResponse(publicId, userCode, username, organizationPublicId, email, fullName, phoneE164, language,
                status, clearance, mfaEnabled, failedAttempts, lockedUntil, lastAccessAt, createdAt, updatedAt, roles, password);
    }
}
