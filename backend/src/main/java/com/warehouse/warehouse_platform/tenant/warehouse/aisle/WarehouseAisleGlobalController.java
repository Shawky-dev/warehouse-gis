package com.warehouse.warehouse_platform.tenant.warehouse.aisle;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/{tenantSlug}/aisles")
@Validated
public class WarehouseAisleGlobalController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseAisleService warehouseAisleService;

    public WarehouseAisleGlobalController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseAisleService warehouseAisleService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseAisleService = warehouseAisleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleService.AislePageResult> listAisles(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) UUID layoutId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleService.listAislesGlobal(layoutId, page, size, search, active));
    }
}
