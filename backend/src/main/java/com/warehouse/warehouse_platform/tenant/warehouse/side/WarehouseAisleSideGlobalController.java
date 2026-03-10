package com.warehouse.warehouse_platform.tenant.warehouse.side;

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
@RequestMapping("/{tenantSlug}/sides")
@Validated
public class WarehouseAisleSideGlobalController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseAisleSideService warehouseAisleSideService;

    public WarehouseAisleSideGlobalController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseAisleSideService warehouseAisleSideService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseAisleSideService = warehouseAisleSideService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleSideService.SideListResult> listSides(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) UUID aisleId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleSideService.listSidesGlobal(aisleId, active));
    }
}
