package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenException;
import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthService authService;
    private AuthController controller;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        RefreshCookieProperties cookieProperties = new RefreshCookieProperties(
                "refresh_token",
                "/landlord/auth",
                "Lax",
                false,
                null);
        controller = new AuthController(authService, cookieProperties);
    }

    @Test
    void login_shouldReturnBody_andSetRefreshCookie() {
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);

        when(authService.login(eq(new LoginRequest("admin@system.local", "admin123")), eq("127.0.0.1"), eq("bruno")))
                .thenReturn(new AuthService.AuthResult(
                        "access-token",
                        accessExp,
                        "refresh-token",
                        refreshExp,
                        UUID.randomUUID(),
                        "admin@system.local",
                        java.util.List.of("ROLE_ADMIN"),
                        java.util.List.of("landlord.users.view")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "bruno");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.login(new LoginRequest("admin@system.local", "admin123"), request, response);

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("access-token", entity.getBody().accessToken());

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("refresh_token=refresh-token"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Path=/landlord/auth"));
    }

    @Test
    void login_shouldForceBootstrapTenantContext_andRestorePreviousContext() {
        TenantContext.setTenantId("acme");
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);

        when(authService.login(eq(new LoginRequest("admin@system.local", "admin123")), eq("127.0.0.1"), eq("bruno")))
                .thenAnswer(invocation -> {
                    assertEquals("BOOTSTRAP", TenantContext.getTenantId());
                    return new AuthService.AuthResult(
                            "access-token",
                            accessExp,
                            "refresh-token",
                            refreshExp,
                            UUID.randomUUID(),
                            "admin@system.local",
                            java.util.List.of("ROLE_ADMIN"),
                            java.util.List.of("landlord.users.view"));
                });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "bruno");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.login(new LoginRequest("admin@system.local", "admin123"), request, response);

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("acme", TenantContext.getTenantId());
    }

    @Test
    void refresh_shouldThrow_whenRefreshCookieMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> controller.refresh(request, response));

        assertEquals("Missing refresh token", ex.getMessage());
    }

    @Test
    void refresh_shouldUseCookieValue_andRotateCookie() {
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);

        when(authService.refresh("old-refresh", "127.0.0.1", "bruno"))
                .thenReturn(new AuthService.AuthResult(
                        "new-access",
                        accessExp,
                        "new-refresh",
                        refreshExp,
                        UUID.randomUUID(),
                        "admin@system.local",
                        java.util.List.of("ROLE_ADMIN"),
                        java.util.List.of("landlord.users.view")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "bruno");
        request.setCookies(new Cookie("refresh_token", "old-refresh"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.refresh(request, response);

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("new-access", entity.getBody().accessToken());
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("refresh_token=new-refresh"));
    }

    @Test
    void logout_shouldClearCookie_andRevokeWhenCookieExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "to-revoke"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = controller.logout(request, response);

        assertEquals(204, entity.getStatusCode().value());
        verify(authService).logout("to-revoke");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("refresh_token="));
        assertTrue(setCookie.contains("Max-Age=0"));
    }
}
