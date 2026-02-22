package com.warehouse.warehouse_platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigAccessMatrixTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void bootstrapAdmin_shouldBeAllowedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication("admin@system.local", "BOOTSTRAP", List.of("ROLE_ADMIN"));

        assertTrue(invokeIsBootstrapAdmin(authentication));
    }

    @Test
    void tenantAdmin_shouldBeDeniedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication("admin@acme.local", "acme", List.of("ROLE_ADMIN"));

        assertFalse(invokeIsBootstrapAdmin(authentication));
    }

    @Test
    void bootstrapUserWithoutAdminRole_shouldBeDeniedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication("worker@system.local", "BOOTSTRAP", List.of("ROLE_WORKER"));

        assertFalse(invokeIsBootstrapAdmin(authentication));
    }

    @Test
    void unauthenticatedRequest_shouldBeDeniedForLandlordAccess() throws Exception {
        Authentication authentication = UsernamePasswordAuthenticationToken.unauthenticated(
                "anonymous",
                "n/a");

        assertFalse(invokeIsBootstrapAdmin(authentication));
    }

    @Test
    void jwtAuthenticationConverter_shouldMapRolesClaimToAuthorities() {
        Jwt jwt = jwt("user@acme.local", "acme", List.of("ROLE_ADMIN", "ROLE_WORKER"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) securityConfig
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertEquals("user@acme.local", token.getName());
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_WORKER")));
    }

    private boolean invokeIsBootstrapAdmin(Authentication authentication) throws Exception {
        Method method = SecurityConfig.class.getDeclaredMethod("isBootstrapAdmin", Authentication.class);
        method.setAccessible(true);
        return (boolean) method.invoke(securityConfig, authentication);
    }

    private static Authentication jwtAuthentication(String subject, String tenant, List<String> roles) {
        Jwt jwt = jwt(subject, tenant, roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }

    private static Jwt jwt(String subject, String tenant, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("tenant", tenant)
                .claim("roles", roles)
                .build();
    }
}

