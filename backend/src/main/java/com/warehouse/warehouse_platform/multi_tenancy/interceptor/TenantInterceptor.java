package com.warehouse.warehouse_platform.multi_tenancy.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;

/*
    * Interceptor that extracts tenant ID from incoming requests and sets it in TenantContext.
    * It resolves route-specific auth paths first, then falls back to the "X-TENANT-ID" header, then subdomain parsing.
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
        HttpServletRequest httpRequest = ((ServletWebRequest) request).getRequest();
        String tenantIdFromPath = resolveTenantIdFromPath(resolveRequestPath(httpRequest));
        if (tenantIdFromPath != null) {
            return tenantIdFromPath;
        }

        String headerTenantId = request.getHeader("X-TENANT-ID");
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            return headerTenantId;
        }

        String serverName = httpRequest.getServerName();
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

    private String resolveRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || requestUri == null || requestUri.isBlank()) {
            return requestUri;
        }

        if (requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String resolveTenantIdFromPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }

        String normalizedPath = requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
        if (normalizedPath.isBlank()) {
            return null;
        }

        if (normalizedPath.equals("auth")
                || normalizedPath.startsWith("auth/")
                || normalizedPath.equals("landlord")
                || normalizedPath.startsWith("landlord/")) {
            return BOOTSTRAP_TENANT;
        }

        if (normalizedPath.equals("tenant") || normalizedPath.startsWith("tenant/")) {
            return null;
        }

        int separatorIndex = normalizedPath.indexOf('/');
        if (separatorIndex <= 0) {
            return null;
        }

        String firstPathSegment = normalizedPath.substring(0, separatorIndex);
        String remainingPath = normalizedPath.substring(separatorIndex + 1);
        if (remainingPath.equals("auth") || remainingPath.startsWith("auth/")) {
            return firstPathSegment;
        }

        return null;
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
