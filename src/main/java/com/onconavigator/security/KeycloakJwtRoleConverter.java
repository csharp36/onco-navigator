package com.onconavigator.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Converts Keycloak JWT token claims into Spring Security {@link GrantedAuthority} objects.
 *
 * <p>Keycloak stores realm-level roles in the {@code realm_access.roles} JWT claim.
 * This converter reads that claim and maps roles prefixed with {@code ROLE_} to
 * Spring Security authorities, enabling {@code hasRole("NURSE_NAVIGATOR")} etc.
 *
 * <p>The three roles defined in the Keycloak realm are:
 * <ul>
 *   <li>{@code ROLE_NURSE_NAVIGATOR} — primary user role for day-to-day alert management</li>
 *   <li>{@code ROLE_CARE_COORDINATOR} — read-only coordination and reporting access</li>
 *   <li>{@code ROLE_ADMIN} — full system access including user management and pathway config</li>
 * </ul>
 *
 * <p>Only roles prefixed with {@code ROLE_} are mapped. Keycloak internal roles
 * (e.g., {@code offline_access}, {@code uma_authorization}) are ignored.
 */
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Extract roles from Keycloak's realm_access.roles claim
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object role : roles) {
                    String roleStr = role.toString();
                    // Only map application roles — ignore Keycloak internal roles
                    if (roleStr.startsWith("ROLE_")) {
                        authorities.add(new SimpleGrantedAuthority(roleStr));
                    }
                }
            }
        }

        return authorities;
    }
}
