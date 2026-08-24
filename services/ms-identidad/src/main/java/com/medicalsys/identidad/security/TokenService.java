package com.medicalsys.identidad.security;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Firma los JWT de acceso. Claims acordados con el gateway: sub, organizationId, roles, iat, exp, iss. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;

    public TokenService(JwtEncoder encoder, IdentityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(UserAccount user, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwt().getAccessTokenTtl());
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getPublicId().toString())
                .id(jti)
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("organizationId",
                        user.getOrganizationPublicId() == null ? null : user.getOrganizationPublicId().toString())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, jti, now, expiresAt);
    }
}
