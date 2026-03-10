package com.warehouse.warehouse_platform.tenant.warehouse.aisle;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/warehouse-layouts/{layoutId}/aisles")
@Validated
public class WarehouseAisleController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseAisleService warehouseAisleService;

    public WarehouseAisleController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseAisleService warehouseAisleService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseAisleService = warehouseAisleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleService.AislePageResult> listAisles(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleService.listAisles(layoutId, page, size, search, active));
    }

    @GetMapping("/{aisleId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseAisleService.AisleResult> getAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @PathVariable UUID aisleId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleService.getAisle(aisleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseAisleService.AisleResult> createAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @Valid @RequestBody CreateAisleRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleService.createAisle(layoutId, request.code(), request.name()));
    }

    @PutMapping("/{aisleId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseAisleService.AisleResult> updateAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @PathVariable UUID aisleId,
            @Valid @RequestBody UpdateAisleRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseAisleService.updateAisle(aisleId, request.code(), request.name()));
    }

    @PostMapping("/{aisleId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @PathVariable UUID aisleId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleService.softDeleteAisle(aisleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{aisleId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_RESTORE)")
    public ResponseEntity<Void> restoreAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @PathVariable UUID aisleId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleService.restoreAisle(aisleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{aisleId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteAisle(
            @PathVariable String tenantSlug,
            @PathVariable UUID layoutId,
            @PathVariable UUID aisleId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseAisleService.hardDeleteAisle(aisleId);
        return ResponseEntity.noContent().build();
    }

    public record CreateAisleRequest(
            @NotBlank @Size(max = 20) String code,
            @Size(max = 120) String name) {
    }

    public record UpdateAisleRequest(
            @NotBlank @Size(max = 20) String code,
            @Size(max = 120) String name) {
    }
}
