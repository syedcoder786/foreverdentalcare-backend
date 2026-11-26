package com.clinic.security;

import com.clinic.config.AppProperties;
import com.clinic.entity.Admin;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues short-lived HS256 access tokens with Spring Security's {@link JwtEncoder}.
 * Validation is done by the resource server's {@code JwtDecoder} (see {@link SecurityConfig}).
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.issuer = properties.jwt().issuer();
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessTokenTtlMinutes());
    }

    public String generateAccessToken(Admin admin) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(admin.getId()))
                .claim(CLAIM_EMAIL, admin.getEmail())
                .claim(CLAIM_ROLE, admin.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
