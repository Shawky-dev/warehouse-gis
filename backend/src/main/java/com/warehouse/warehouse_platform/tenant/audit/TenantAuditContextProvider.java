package com.warehouse.warehouse_platform.tenant.audit;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
public class TenantAuditContextProvider {

    private static final String SYSTEM_ACTOR = "system";

    public AuditContext currentContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String actorEmail = SYSTEM_ACTOR;
        List<String> actorRoles = List.of();
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            actorEmail = authentication.getName();
            actorRoles = authentication.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                    .distinct()
                    .sorted()
                    .toList();
        }

        String requestPath = null;
        String requestMethod = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            requestPath = request.getRequestURI();
            requestMethod = request.getMethod();
        }

        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "BOOTSTRAP";
        }

        return new AuditContext(actorEmail, actorRoles, tenantId, requestPath, requestMethod);
    }

    public record AuditContext(
            String actorEmail,
            List<String> actorRoles,
            String tenantId,
            String requestPath,
            String requestMethod) {
    }
}
