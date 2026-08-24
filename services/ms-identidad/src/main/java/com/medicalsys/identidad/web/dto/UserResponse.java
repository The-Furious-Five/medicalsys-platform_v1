package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID publicId,
        UUID organizationPublicId,
        String email,
        String fullName,
        String phoneE164,
        String language,
        UserStatus status,
        boolean mfaEnabled,
        int failedAttempts,
        Instant lockedUntil,
        Instant lastAccessAt,
        Instant createdAt,
        Instant updatedAt,
        List<RoleAssignmentResponse> roles) {
}
