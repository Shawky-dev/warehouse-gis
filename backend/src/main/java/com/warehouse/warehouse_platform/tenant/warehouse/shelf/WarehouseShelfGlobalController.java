package com.warehouse.warehouse_platform.tenant.warehouse.shelf;

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
@RequestMapping("/{tenantSlug}/shelves")
@Validated
public class WarehouseShelfGlobalController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseShelfService warehouseShelfService;

    public WarehouseShelfGlobalController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseShelfService warehouseShelfService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseShelfService = warehouseShelfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseShelfService.ShelfListResult> listShelves(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) UUID levelId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseShelfService.listShelvesGlobal(levelId, active));
    }
}
