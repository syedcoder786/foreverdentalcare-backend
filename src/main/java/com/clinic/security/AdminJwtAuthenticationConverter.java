package com.clinic.security;

import com.clinic.enums.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

/**
 * Turns a JWT already validated by Spring Security's resource server into an authentication:
 * <ul>
 *   <li>role PATIENT: principal {@link PatientPrincipal}, authority ROLE_PATIENT</li>
 *   <li>admin roles: principal {@link AdminPrincipal}, authority ROLE_&lt;role&gt;</li>
 * </ul>
 * Tokens without a role (e.g. patient sign-up tokens) are rejected.
 */
public class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            String role = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
            if (JwtService.ROLE_PATIENT.equals(role)) {
                PatientPrincipal patient = new PatientPrincipal(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.CLAIM_PHONE));
                return new UsernamePasswordAuthenticationToken(patient, jwt, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
            }
            Role adminRole = Role.valueOf(role);
            AdminPrincipal principal = new AdminPrincipal(
                    Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.CLAIM_EMAIL), adminRole);
            return new UsernamePasswordAuthenticationToken(
                    principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + adminRole.name())));
        } catch (RuntimeException ex) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
    }
}
