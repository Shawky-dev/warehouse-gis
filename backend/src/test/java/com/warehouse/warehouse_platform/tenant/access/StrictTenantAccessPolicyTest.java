package com.warehouse.warehouse_platform.tenant.access;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrictTenantAccessPolicyTest {

    private TenantRepository tenantRepository;
    private StrictTenantAccessPolicy policy;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        policy = new StrictTenantAccessPolicy(tenantRepository);
    }

    @Test
    void assertTenantAccess_shouldAllowMatchingJwtTenantClaim() {
        when(tenantRepository.findByTenantId("acme"))
                .thenReturn(Optional.of(Tenant.builder().tenantId("acme").schema("acme").build()));

        Authentication authentication = jwtAuthentication("acme");

        assertDoesNotThrow(() -> policy.assertTenantAccess(authentication, "acme"));
    }

    @Test
    void assertTenantAccess_shouldRejectMismatchedJwtTenantClaim() {
        when(tenantRepository.findByTenantId("beta"))
                .thenReturn(Optional.of(Tenant.builder().tenantId("beta").schema("beta").build()));

        Authentication authentication = jwtAuthentication("acme");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> policy.assertTenantAccess(authentication, "beta"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Token tenant does not match request tenant", exception.getReason());
    }

    @Test
    void assertTenantAccess_shouldRejectUnsupportedAuthenticationType() {
        when(tenantRepository.findByTenantId("acme"))
                .thenReturn(Optional.of(Tenant.builder().tenantId("acme").schema("acme").build()));

        Authentication authentication = new TestingAuthenticationToken("user@acme.local", "n/a", "ROLE_ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> policy.assertTenantAccess(authentication, "acme"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Unsupported authentication type", exception.getReason());
    }

    @Test
    void assertTenantAccess_shouldRejectMissingJwtTenantClaim() {
        when(tenantRepository.findByTenantId("acme"))
                .thenReturn(Optional.of(Tenant.builder().tenantId("acme").schema("acme").build()));

        Authentication authentication = jwtAuthentication(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> policy.assertTenantAccess(authentication, "acme"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Token tenant does not match request tenant", exception.getReason());
    }

    @Test
    void assertTenantExists_shouldRejectWhenTenantIsMissing() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> policy.assertTenantExists("missing"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Tenant not found: missing", exception.getReason());
    }

    private Authentication jwtAuthentication(String tenantClaim) {
        Instant now = Instant.now();

        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("user@acme.local")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", List.of("ROLE_ADMIN"));

        if (tenantClaim != null) {
            builder.claim("tenant", tenantClaim);
        }

        Jwt jwt = builder.build();
        return new JwtAuthenticationToken(jwt, List.of(), "user@acme.local");
    }
}
