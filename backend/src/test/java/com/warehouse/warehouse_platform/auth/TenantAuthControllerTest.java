package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenException;
import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAuthControllerTest {

    private AuthService authService;
    private TenantRepository tenantRepository;
    private TenantAuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        tenantRepository = mock(TenantRepository.class);
        RefreshCookieProperties cookieProperties = new RefreshCookieProperties(
                "refresh_token",
                "/landlord/auth",
                "Lax",
                false,
                null);
        controller = new TenantAuthController(authService, cookieProperties, tenantRepository);
    }

    @Test
    void login_shouldUseTenantContext_andSetTenantCookiePath() {
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));

        when(authService.login(eq(new LoginRequest("admin@acme.local", "admin1234")), eq("127.0.0.1"), eq("bruno")))
                .thenAnswer(invocation -> {
                    return new AuthService.AuthResult(
                            "access-token",
                            accessExp,
                            "refresh-token",
                            refreshExp,
                            UUID.randomUUID(),
                            "admin@acme.local",
                            List.of("ROLE_ADMIN"),
                            List.of("landlord.users.view"));
                });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "bruno");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.login("acme", new LoginRequest("admin@acme.local", "admin1234"), request, response);

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("access-token", entity.getBody().accessToken());

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("refresh_token=refresh-token"));
        assertTrue(setCookie.contains("Path=/acme/auth"));
    }

    @Test
    void login_shouldPrefixTenantCookiePath_whenForwardedPrefixHeaderIsPresent() {
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));

        when(authService.login(eq(new LoginRequest("admin@acme.local", "admin1234")), eq("127.0.0.1"), eq("bruno")))
                .thenReturn(new AuthService.AuthResult(
                        "access-token",
                        accessExp,
                        "refresh-token",
                        refreshExp,
                        UUID.randomUUID(),
                        "admin@acme.local",
                        List.of("ROLE_ADMIN"),
                        List.of("landlord.users.view")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "bruno");
        request.addHeader("X-Forwarded-Prefix", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login("acme", new LoginRequest("admin@acme.local", "admin1234"), request, response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("Path=/api/acme/auth"));
    }

    @Test
    void refresh_shouldThrow_whenRefreshCookieMissing() {
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> controller.refresh("acme", request, response));

        assertEquals("Missing refresh token", ex.getMessage());
    }

    @Test
    void logout_shouldClearCookie_andRevokeTokenFamily() {
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "tenant-refresh"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.logout("acme", request, response);

        assertEquals(204, entity.getStatusCode().value());
        verify(authService).logout("tenant-refresh");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("Path=/acme/auth"));
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void login_shouldReturnNotFound_whenTenantDoesNotExist() {
        when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.login("missing", new LoginRequest("admin@acme.local", "admin1234"), request, response));

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("Tenant not found: missing", exception.getReason());
    }

    @Test
    void session_shouldReturnTenantSession_whenTokenTenantMatchesPath() {
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));

        Authentication authentication = tenantJwtAuthentication("acme", "admin@acme.local", List.of("ROLE_ADMIN"));

        var entity = controller.session("acme", authentication);

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("admin@acme.local", entity.getBody().subject());
        assertEquals("acme", entity.getBody().tenant());
        assertTrue(entity.getBody().authorities().contains("ROLE_ADMIN"));
    }

    @Test
    void session_shouldReturnForbidden_whenTokenTenantDiffersFromPath() {
        when(tenantRepository.findByTenantId("beta")).thenReturn(Optional.of(Tenant.builder().tenantId("beta").build()));

        Authentication authentication = tenantJwtAuthentication("acme", "admin@acme.local", List.of("ROLE_ADMIN"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.session("beta", authentication));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals("Token tenant does not match request tenant", exception.getReason());
    }

    @Test
    void session_shouldReturnForbidden_whenAuthenticationIsNotJwt() {
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(Tenant.builder().tenantId("acme").build()));

        Authentication authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.session("acme", authentication));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals("Unsupported authentication type", exception.getReason());
    }

    private Authentication tenantJwtAuthentication(String tenant, String subject, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("tenant", tenant)
                .claim("roles", roles)
                .build();

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new JwtAuthenticationToken(jwt, authorities, subject);
    }
}
