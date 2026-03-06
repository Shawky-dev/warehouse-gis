package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenException;
import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAuthControllerCrossTenantTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void refresh_shouldRejectCrossTenantTokenFamily() {
        TenantAuthController controller = new TenantAuthController(
                new TenantAwareAuthService(),
                cookieProperties(),
                tenantRepositoryWithTenants(Set.of("acme", "beta")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "test-agent");
        request.setCookies(new Cookie("refresh_token", "acme:refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshTokenException exception = assertThrows(
                RefreshTokenException.class,
                () -> controller.refresh("beta", request, response));

        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    void refresh_shouldAllowWhenTokenBelongsToSameTenantPath() {
        TenantAuthController controller = new TenantAuthController(
                new TenantAwareAuthService(),
                cookieProperties(),
                tenantRepositoryWithTenants(Set.of("acme", "beta")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "test-agent");
        request.setCookies(new Cookie("refresh_token", "acme:refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh("acme", request, response);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("tenant-access-token", result.getBody().accessToken());
        assertEquals("admin@acme.local", result.getBody().user().email());
    }

    private static RefreshCookieProperties cookieProperties() {
        return new RefreshCookieProperties("refresh_token", "/landlord/auth", "Lax", false, null);
    }

    private static TenantRepository tenantRepositoryWithTenants(Set<String> tenantIds) {
        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("findByTenantId".equals(methodName)) {
                String tenantId = (String) args[0];
                if (tenantIds.contains(tenantId)) {
                    return Optional.of(Tenant.builder().tenantId(tenantId).schema(tenantId).build());
                }
                return Optional.empty();
            }
            if ("findBySchema".equals(methodName)) {
                String schema = (String) args[0];
                if (tenantIds.contains(schema)) {
                    return Optional.of(Tenant.builder().tenantId(schema).schema(schema).build());
                }
                return Optional.empty();
            }
            if ("toString".equals(methodName)) {
                return "TenantRepositoryProxy";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("Method not supported in test proxy: " + methodName);
        };

        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[] { TenantRepository.class },
                handler);
    }

        private static final class TenantAwareAuthService extends AuthService {
        private TenantAwareAuthService() {
            super(null, null, null, null, null);
        }

        @Override
        public AuthResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
            String[] parts = rawRefreshToken.split(":", 2);
            String tokenTenant = parts.length > 1 ? parts[0] : "";
            String activeTenant = TenantContext.getTenantId();

            if (!tokenTenant.equalsIgnoreCase(activeTenant)) {
                throw new RefreshTokenException("Invalid refresh token");
            }

            Instant now = Instant.now();
            return new AuthResult(
                    "tenant-access-token",
                    now.plusSeconds(600),
                    rawRefreshToken,
                    now.plusSeconds(1200),
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "admin@" + tokenTenant + ".local",
                    java.util.List.of("ROLE_ADMIN"),
                    java.util.List.of("tenant.users.view"));
        }
    }
}
