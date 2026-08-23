package com.medicalsys.identidad.security;

import java.time.Instant;

public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {
}
