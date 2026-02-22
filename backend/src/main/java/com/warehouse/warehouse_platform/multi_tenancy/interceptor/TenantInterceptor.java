package com.warehouse.warehouse_platform.multi_tenancy.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;

/*
    * Interceptor that extracts tenant ID from incoming requests and sets it in TenantContext.
    * It looks for tenant ID in the "X-TENANT-ID" header first, and if not found, it falls back to subdomain parsing.
*/
@Component
public class TenantInterceptor implements WebRequestInterceptor {

    private static final String BOOTSTRAP_TENANT = "BOOTSTRAP";

    @Override
    public void preHandle(WebRequest request) throws Exception {
        String tenantId = resolveTenantId(request);
        TenantContext.setTenantId(tenantId);
    }

    private String resolveTenantId(WebRequest request) {
        String headerTenantId = request.getHeader("X-TENANT-ID");
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            return headerTenantId;
        }

        String serverName = ((ServletWebRequest) request).getRequest().getServerName();
        if (serverName == null || serverName.isBlank()) {
            return BOOTSTRAP_TENANT;
        }

        String normalized = serverName.toLowerCase();
        if ("localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized)) {
            return BOOTSTRAP_TENANT;
        }

        if (!normalized.contains(".")) {
            return BOOTSTRAP_TENANT;
        }

        String candidate = normalized.split("\\.")[0];
        if (candidate.isBlank() || "www".equals(candidate)) {
            return BOOTSTRAP_TENANT;
        }

        return candidate;
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) throws Exception {
        /*
            clears the tenant contex in the Request thread
        */
        TenantContext.clear();
    }

    @Override
    public void afterCompletion(WebRequest request, Exception ex) throws Exception {
        TenantContext.clear();
    }

}
