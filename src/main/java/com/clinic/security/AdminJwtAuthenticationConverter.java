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
 * Turns a JWT already validated by Spring Security's resource server into an authentication whose principal is
 * an {@link AdminPrincipal} with a {@code ROLE_<role>} authority.
 */
public class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Role role = Role.valueOf(jwt.getClaimAsString(JwtService.CLAIM_ROLE));
            AdminPrincipal principal = new AdminPrincipal(
                    Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.CLAIM_EMAIL), role);
            return new UsernamePasswordAuthenticationToken(
                    principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        } catch (RuntimeException ex) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
    }
}
