package com.medicalsys.identidad.web.dto;

import java.time.Instant;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        Instant issuedAt,
        Instant expiresAt,
        AuthenticatedUser user) {
}
