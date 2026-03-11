package com.warehouse.warehouse_platform.tenant.warehouse.layout;

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
@RequestMapping("/{tenantSlug}/warehouse-layouts")
@Validated
public class WarehouseLayoutController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseLayoutService warehouseLayoutService;

    public WarehouseLayoutController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseLayoutService warehouseLayoutService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseLayoutService = warehouseLayoutService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseLayoutService.LayoutPageResult> listLayouts(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLayoutService.listLayouts(page, size, search, active));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseLayoutService.LayoutResult> getLayout(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLayoutService.getLayout(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE)")
    public ResponseEntity<WarehouseLayoutService.LayoutResult> createLayout(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateLayoutRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLayoutService.createLayout(request.name(), request.description()));
    }

    @PostMapping("/presets/classic")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE) and (!#request.activate() or hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE))")
    public ResponseEntity<WarehouseLayoutService.LayoutResult> createClassicPreset(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateClassicPresetRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLayoutService.createClassicPreset(
                request.name(), request.description(), request.activate()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE)")
    public ResponseEntity<WarehouseLayoutService.LayoutResult> updateLayout(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLayoutRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLayoutService.updateLayout(id, request.name(), request.description()));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE)")
    public ResponseEntity<Void> activateLayout(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseLayoutService.activateLayout(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE)")
    public ResponseEntity<Void> deactivateLayout(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseLayoutService.deactivateLayout(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> deleteLayout(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseLayoutService.deleteLayout(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateLayoutRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateLayoutRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record CreateClassicPresetRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            boolean activate) {
    }
}
