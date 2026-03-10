package com.warehouse.warehouse_platform.tenant.warehouse.shelf;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/bay-levels/{levelId}/shelves")
@Validated
public class WarehouseShelfController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseShelfService warehouseShelfService;

    public WarehouseShelfController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseShelfService warehouseShelfService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseShelfService = warehouseShelfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseShelfService.ShelfListResult> listShelves(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseShelfService.listShelves(levelId, active));
    }

    @GetMapping("/{shelfId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseShelfService.ShelfResult> getShelf(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @PathVariable UUID shelfId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseShelfService.getShelf(shelfId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseShelfService.ShelfResult> createShelf(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @Valid @RequestBody CreateShelfRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseShelfService.createShelf(levelId, request.shelfNum()));
    }

    @PostMapping("/{shelfId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteShelf(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @PathVariable UUID shelfId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseShelfService.softDeleteShelf(shelfId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{shelfId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_RESTORE)")
    public ResponseEntity<Void> restoreShelf(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @PathVariable UUID shelfId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseShelfService.restoreShelf(shelfId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{shelfId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteShelf(
            @PathVariable String tenantSlug,
            @PathVariable UUID levelId,
            @PathVariable UUID shelfId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseShelfService.hardDeleteShelf(shelfId);
        return ResponseEntity.noContent().build();
    }

    public record CreateShelfRequest(@Min(1) int shelfNum) {
    }
}
