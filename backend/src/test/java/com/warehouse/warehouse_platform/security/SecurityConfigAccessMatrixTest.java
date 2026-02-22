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
    void bootstrapManager_shouldBeAllowedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication(
                "manager@system.local",
                "BOOTSTRAP",
                List.of("ROLE_MANAGER"),
                List.of("landlord.users.view"));

        assertTrue(invokeIsBootstrapTenantAuthentication(authentication));
    }

    @Test
    void tenantAdmin_shouldBeDeniedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication("admin@acme.local", "acme", List.of("ROLE_ADMIN"), List.of());

        assertFalse(invokeIsBootstrapTenantAuthentication(authentication));
    }

    @Test
    void bootstrapUserWithoutAdminRole_shouldStillBeAllowedForLandlordAccess() throws Exception {
        Authentication authentication = jwtAuthentication("worker@system.local", "BOOTSTRAP", List.of("ROLE_WORKER"), List.of());

        assertTrue(invokeIsBootstrapTenantAuthentication(authentication));
    }

    @Test
    void unauthenticatedRequest_shouldBeDeniedForLandlordAccess() throws Exception {
        Authentication authentication = UsernamePasswordAuthenticationToken.unauthenticated(
                "anonymous",
                "n/a");

        assertFalse(invokeIsBootstrapTenantAuthentication(authentication));
    }

    @Test
    void jwtAuthenticationConverter_shouldMapRolesAndPermissionsClaimsToAuthorities() {
        Jwt jwt = jwt(
                "user@acme.local",
                "acme",
                List.of("ROLE_ADMIN", "ROLE_WORKER"),
                List.of("landlord.users.view", "landlord.users.create"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) securityConfig
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertEquals("user@acme.local", token.getName());
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_WORKER")));
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("landlord.users.view")));
        assertTrue(token.getAuthorities().contains(new SimpleGrantedAuthority("landlord.users.create")));
    }

    private boolean invokeIsBootstrapTenantAuthentication(Authentication authentication) throws Exception {
        Method method = SecurityConfig.class.getDeclaredMethod("isBootstrapTenantAuthentication", Authentication.class);
        method.setAccessible(true);
        return (boolean) method.invoke(securityConfig, authentication);
    }

    private static Authentication jwtAuthentication(
            String subject,
            String tenant,
            List<String> roles,
            List<String> permissions) {
        Jwt jwt = jwt(subject, tenant, roles, permissions);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }

    private static Jwt jwt(String subject, String tenant, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("tenant", tenant)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
    }
}
