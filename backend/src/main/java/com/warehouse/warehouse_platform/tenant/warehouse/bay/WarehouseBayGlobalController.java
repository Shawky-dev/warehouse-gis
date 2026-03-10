package com.warehouse.warehouse_platform.tenant.warehouse.bay;

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
@RequestMapping("/{tenantSlug}/bays")
@Validated
public class WarehouseBayGlobalController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseBayService warehouseBayService;

    public WarehouseBayGlobalController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseBayService warehouseBayService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseBayService = warehouseBayService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayService.BayPageResult> listBays(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) UUID sideId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.listBaysGlobal(sideId, page, size, search, active));
    }
}
