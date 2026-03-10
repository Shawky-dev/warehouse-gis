package com.warehouse.warehouse_platform.tenant.warehouse.level;

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
@RequestMapping("/{tenantSlug}/bays/{bayId}/levels")
@Validated
public class WarehouseBayLevelController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseBayLevelService warehouseBayLevelService;

    public WarehouseBayLevelController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseBayLevelService warehouseBayLevelService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseBayLevelService = warehouseBayLevelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayLevelService.LevelListResult> listLevels(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayLevelService.listLevels(bayId, active));
    }

    @GetMapping("/{levelId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayLevelService.LevelResult> getLevel(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @PathVariable UUID levelId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayLevelService.getLevel(levelId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseBayLevelService.LevelResult> createLevel(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @Valid @RequestBody CreateLevelRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayLevelService.createLevel(bayId, request.levelNum()));
    }

    @PostMapping("/{levelId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteLevel(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @PathVariable UUID levelId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayLevelService.softDeleteLevel(levelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{levelId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_RESTORE)")
    public ResponseEntity<Void> restoreLevel(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @PathVariable UUID levelId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayLevelService.restoreLevel(levelId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{levelId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteLevel(
            @PathVariable String tenantSlug,
            @PathVariable UUID bayId,
            @PathVariable UUID levelId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayLevelService.hardDeleteLevel(levelId);
        return ResponseEntity.noContent().build();
    }

    public record CreateLevelRequest(@Min(1) int levelNum) {
    }
}
