package com.warehouse.warehouse_platform.tenant.access;

import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StrictTenantAccessPolicy implements TenantAccessPolicy {

    private final TenantRepository tenantRepository;

    public StrictTenantAccessPolicy(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void assertTenantExists(String tenantSlug) {
        if (tenantRepository.findByTenantId(tenantSlug).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantSlug);
        }
    }

    @Override
    public void assertTenantAccess(Authentication authentication, String tenantSlug) {
        assertTenantExists(tenantSlug);

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported authentication type");
        }

        String tokenTenant = jwtAuthenticationToken.getToken().getClaimAsString("tenant");
        if (tokenTenant == null || !tokenTenant.equalsIgnoreCase(tenantSlug)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token tenant does not match request tenant");
        }
    }
}
