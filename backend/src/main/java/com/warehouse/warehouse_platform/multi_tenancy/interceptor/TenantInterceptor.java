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

    @Override
    public void preHandle(WebRequest request) throws Exception {
        String tenantId = null;
        if (request.getHeader("X-TENANT-ID") != null) {
            tenantId = request.getHeader("X-TENANT-ID");
        } else {
            tenantId = ((ServletWebRequest)request).getRequest().getServerName().split("\\.")[0];
        }
        TenantContext.setTenantId(tenantId);
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
    }

}
