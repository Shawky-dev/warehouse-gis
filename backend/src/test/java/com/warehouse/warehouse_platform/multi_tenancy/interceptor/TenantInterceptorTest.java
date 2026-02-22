package com.warehouse.warehouse_platform.multi_tenancy.interceptor;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantInterceptorTest {

    private final TenantInterceptor tenantInterceptor = new TenantInterceptor();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preHandle_shouldUseBootstrapForLandlordAuthPaths_evenWhenHeaderAndSubdomainAreTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/landlord/auth/login");
        request.addHeader("X-TENANT-ID", "acme");
        request.setServerName("acme.example.com");

        tenantInterceptor.preHandle(new ServletWebRequest(request));

        assertEquals("BOOTSTRAP", TenantContext.getTenantId());
    }

    @Test
    void preHandle_shouldUseBootstrapForLandlordEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/landlord/session");
        request.addHeader("X-TENANT-ID", "acme");

        tenantInterceptor.preHandle(new ServletWebRequest(request));

        assertEquals("BOOTSTRAP", TenantContext.getTenantId());
    }

    @Test
    void preHandle_shouldUseHeaderTenantForTenantAuthPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/acme/auth/login");
        request.addHeader("X-TENANT-ID", "beta");
        request.setServerName("beta.example.com");

        tenantInterceptor.preHandle(new ServletWebRequest(request));

        assertEquals("acme", TenantContext.getTenantId());
    }

    @Test
    void preHandle_shouldKeepHeaderFallbackForNonAuthPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/warehouses");
        request.addHeader("X-TENANT-ID", "acme");

        tenantInterceptor.preHandle(new ServletWebRequest(request));

        assertEquals("acme", TenantContext.getTenantId());
    }
}
