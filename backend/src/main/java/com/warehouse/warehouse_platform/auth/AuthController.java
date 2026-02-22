package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/landlord/auth")
public class AuthController {

    private static final String BOOTSTRAP_TENANT = "BOOTSTRAP";

    private final AuthService authService;
    private final RefreshCookieProperties refreshCookieProperties;

    public AuthController(AuthService authService, RefreshCookieProperties refreshCookieProperties) {
        this.authService = authService;
        this.refreshCookieProperties = refreshCookieProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return runInBootstrapContext(() -> {
            AuthService.AuthResult result = authService.login(
                    request,
                    resolveClientIp(httpRequest),
                    httpRequest.getHeader(HttpHeaders.USER_AGENT));

            writeRefreshCookie(httpResponse, result.refreshToken(), result.refreshTokenExpiresAt());
            return ResponseEntity.ok(AuthResponse.from(result));
        });
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return runInBootstrapContext(() -> {
            String refreshToken = extractRefreshCookie(httpRequest);

            if (refreshToken == null || refreshToken.isBlank()) {
                throw new com.warehouse.warehouse_platform.auth.session.RefreshTokenException("Missing refresh token");
            }

            AuthService.AuthResult result = authService.refresh(
                    refreshToken,
                    resolveClientIp(httpRequest),
                    httpRequest.getHeader(HttpHeaders.USER_AGENT));

            writeRefreshCookie(httpResponse, result.refreshToken(), result.refreshTokenExpiresAt());
            return ResponseEntity.ok(AuthResponse.from(result));
        });
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return runInBootstrapContext(() -> {
            String refreshToken = extractRefreshCookie(httpRequest);

            if (refreshToken != null && !refreshToken.isBlank()) {
                authService.logout(refreshToken);
            }

            clearRefreshCookie(httpResponse);
            return ResponseEntity.noContent().build();
        });
    }

    private void writeRefreshCookie(HttpServletResponse httpResponse, String refreshToken, Instant expiresAt) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());

        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(refreshCookieProperties.name(), refreshToken)
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(refreshCookieProperties.path())
                .maxAge(maxAgeSeconds);

        if (refreshCookieProperties.domain() != null && !refreshCookieProperties.domain().isBlank()) {
            cookieBuilder.domain(refreshCookieProperties.domain());
        }

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse httpResponse) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(refreshCookieProperties.name(), "")
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(refreshCookieProperties.path())
                .maxAge(0);

        if (refreshCookieProperties.domain() != null && !refreshCookieProperties.domain().isBlank()) {
            cookieBuilder.domain(refreshCookieProperties.domain());
        }

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieProperties.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private <T> T runInBootstrapContext(java.util.function.Supplier<T> action) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(BOOTSTRAP_TENANT);
        try {
            return action.get();
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }
}
