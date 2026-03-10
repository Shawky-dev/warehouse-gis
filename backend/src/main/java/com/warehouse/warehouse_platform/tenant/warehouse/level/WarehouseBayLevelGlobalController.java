package com.warehouse.warehouse_platform.tenant.warehouse.level;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/levels")
@Validated
public class WarehouseBayLevelGlobalController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseBayLevelService warehouseBayLevelService;

    public WarehouseBayLevelGlobalController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseBayLevelService warehouseBayLevelService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseBayLevelService = warehouseBayLevelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayLevelService.LevelListResult> listLevels(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) UUID bayId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayLevelService.listLevelsGlobal(bayId, active));
    }
}
