package com.warehouse.warehouse_platform.tenant.scan;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/{tenantSlug}/scan")
public class ScanController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final ScanResolverService scanResolverService;

    public ScanController(TenantAccessPolicy tenantAccessPolicy, ScanResolverService scanResolverService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.scanResolverService = scanResolverService;
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
    public ResponseEntity<ScanResolveResult> resolve(
            @PathVariable String tenantSlug,
            @RequestParam String code,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(scanResolverService.resolve(code));
    }
}
