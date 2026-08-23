package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.UserStatus;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID publicId,
        String email,
        String fullName,
        UUID organizationPublicId,
        UserStatus status,
        String language,
        List<String> roles,
        List<String> permissions) {
}
