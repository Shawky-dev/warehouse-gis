package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/{tenantSlug}/auth")
public class TenantAuthController {

    private final AuthService authService;
    private final RefreshCookieProperties refreshCookieProperties;
    private final TenantRepository tenantRepository;

    public TenantAuthController(
            AuthService authService,
            RefreshCookieProperties refreshCookieProperties,
            TenantRepository tenantRepository) {
        this.authService = authService;
        this.refreshCookieProperties = refreshCookieProperties;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @PathVariable String tenantSlug,
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureTenantExists(tenantSlug);
        return runInTenantContext(tenantSlug, () -> {
            AuthService.AuthResult result = authService.login(
                    request,
                    resolveClientIp(httpRequest),
                    httpRequest.getHeader(HttpHeaders.USER_AGENT));

            writeRefreshCookie(httpResponse, result.refreshToken(), result.refreshTokenExpiresAt(), tenantSlug);
            return ResponseEntity.ok(AuthResponse.from(result));
        });
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @PathVariable String tenantSlug,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureTenantExists(tenantSlug);
        return runInTenantContext(tenantSlug, () -> {
            String refreshToken = extractRefreshCookie(httpRequest);

            if (refreshToken == null || refreshToken.isBlank()) {
                throw new com.warehouse.warehouse_platform.auth.session.RefreshTokenException("Missing refresh token");
            }

            AuthService.AuthResult result = authService.refresh(
                    refreshToken,
                    resolveClientIp(httpRequest),
                    httpRequest.getHeader(HttpHeaders.USER_AGENT));

            writeRefreshCookie(httpResponse, result.refreshToken(), result.refreshTokenExpiresAt(), tenantSlug);
            return ResponseEntity.ok(AuthResponse.from(result));
        });
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @PathVariable String tenantSlug,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureTenantExists(tenantSlug);
        return runInTenantContext(tenantSlug, () -> {
            String refreshToken = extractRefreshCookie(httpRequest);

            if (refreshToken != null && !refreshToken.isBlank()) {
                authService.logout(refreshToken);
            }

            clearRefreshCookie(httpResponse, tenantSlug);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/session")
    public ResponseEntity<TenantSessionResponse> session(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        ensureTenantExists(tenantSlug);
        ensureTokenTenantMatchesPath(authentication, tenantSlug);

        return runInTenantContext(tenantSlug, () -> {
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .toList();

            return ResponseEntity.ok(new TenantSessionResponse(
                    authentication.getName(),
                    tenantSlug,
                    authorities,
                    Instant.now(),
                    "Tenant access granted"));
        });
    }

    private void writeRefreshCookie(
            HttpServletResponse httpResponse,
            String refreshToken,
            Instant expiresAt,
            String tenantSlug) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());

        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(refreshCookieProperties.name(), refreshToken)
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(resolveTenantRefreshPath(tenantSlug))
                .maxAge(maxAgeSeconds);

        if (refreshCookieProperties.domain() != null && !refreshCookieProperties.domain().isBlank()) {
            cookieBuilder.domain(refreshCookieProperties.domain());
        }

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse httpResponse, String tenantSlug) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(refreshCookieProperties.name(), "")
                .httpOnly(true)
                .secure(refreshCookieProperties.secure())
                .sameSite(refreshCookieProperties.sameSite())
                .path(resolveTenantRefreshPath(tenantSlug))
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

    private String resolveTenantRefreshPath(String tenantSlug) {
        String basePath = refreshCookieProperties.path();
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        return "/" + tenantSlug + basePath;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private <T> T runInTenantContext(String tenantSlug, java.util.function.Supplier<T> action) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantSlug);
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

    private void ensureTenantExists(String tenantSlug) {
        if (tenantRepository.findByTenantId(tenantSlug).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantSlug);
        }
    }

    private void ensureTokenTenantMatchesPath(Authentication authentication, String tenantSlug) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported authentication type");
        }

        String tokenTenant = jwtAuthenticationToken.getToken().getClaimAsString("tenant");
        if (tokenTenant == null || !tokenTenant.equalsIgnoreCase(tenantSlug)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token tenant does not match path tenant");
        }
    }

    public record TenantSessionResponse(
            String subject,
            String tenant,
            List<String> authorities,
            Instant serverTime,
            String message) {
    }
}
