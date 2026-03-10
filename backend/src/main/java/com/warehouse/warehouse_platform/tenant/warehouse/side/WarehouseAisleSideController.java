package com.warehouse.warehouse_platform.tenant.warehouse.side;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/{tenantSlug}/aisles/{aisleId}/sides")
@Validated
public class WarehouseAisleSideController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseAisleSideService warehouseAisleSideService;

    public WarehouseAisleSideController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseAisleSideService warehouseAisleSideService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseAisleSideService = warehouseAisleSideService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleSideService.SideListResult> listSides(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleSideService.listSides(aisleId, active));
    }

    @GetMapping("/{sideId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleSideService.SideResult> getSide(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @PathVariable UUID sideId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleSideService.getSide(sideId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseAisleSideService.SideResult> createSide(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @Valid @RequestBody CreateSideRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleSideService.createSide(aisleId, request.side()));
    }

    @PostMapping("/{sideId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteSide(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @PathVariable UUID sideId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleSideService.softDeleteSide(sideId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sideId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_RESTORE)")
    public ResponseEntity<Void> restoreSide(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @PathVariable UUID sideId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleSideService.restoreSide(sideId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sideId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteSide(
            @PathVariable String tenantSlug,
            @PathVariable UUID aisleId,
            @PathVariable UUID sideId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleSideService.hardDeleteSide(sideId);
        return ResponseEntity.noContent().build();
    }

    public record CreateSideRequest(
            @NotBlank @Size(max = 1) String side) {
    }
}
