package com.clinic.security;

import com.clinic.config.AppProperties;
import com.clinic.entity.Admin;
import com.clinic.entity.Patient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues HS256 tokens with Spring Security's {@link JwtEncoder}. Access tokens are validated by the resource server's
 * {@link JwtDecoder} (see {@link SecurityConfig}).
 * <ul>
 *   <li>Admin access token: role ADMIN, subject = admin id.</li>
 *   <li>Patient access token: role PATIENT, subject = patient id.</li>
 *   <li>Patient sign-up token: no role (so it can never be used as an access token), subject = verified phone number,
 *       purpose = patient-signup. Lets a newly verified number finish sign-up by adding a name.</li>
 * </ul>
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_PHONE = "phone";
    static final String CLAIM_PURPOSE = "purpose";
    static final String ROLE_PATIENT = "PATIENT";
    private static final String PURPOSE_SIGNUP = "patient-signup";
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, AppProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.issuer = properties.jwt().issuer();
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessTokenTtlMinutes());
    }

    public String generateAccessToken(Admin admin) {
        return encode(base(String.valueOf(admin.getId()), accessTtl)
                .claim(CLAIM_EMAIL, admin.getEmail())
                .claim(CLAIM_ROLE, admin.getRole().name())
                .build());
    }

    public String generatePatientAccessToken(Patient patient) {
        return encode(base(String.valueOf(patient.getId()), accessTtl)
                .claim(CLAIM_PHONE, patient.getPhone())
                .claim(CLAIM_ROLE, ROLE_PATIENT)
                .build());
    }

    public String generateSignupToken(String e164Phone) {
        return encode(base(e164Phone, SIGNUP_TTL).claim(CLAIM_PURPOSE, PURPOSE_SIGNUP).build());
    }

    /** Returns the verified phone number from a valid, unexpired sign-up token. */
    public Optional<String> parseSignupToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!PURPOSE_SIGNUP.equals(jwt.getClaimAsString(CLAIM_PURPOSE)) || jwt.hasClaim(CLAIM_ROLE)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jwt.getSubject());
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private JwtClaimsSet.Builder base(String subject, Duration ttl) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
