package com.warehouse.warehouse_platform.tenant.warehouse.locationkind;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/warehouse-location-kinds")
@Validated
public class WarehouseLocationKindController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseLocationKindService warehouseLocationKindService;

    public WarehouseLocationKindController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseLocationKindService warehouseLocationKindService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseLocationKindService = warehouseLocationKindService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<List<WarehouseLocationKindService.LocationKindResult>> listLocationKinds(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLocationKindService.listLocationKinds());
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_TEMPLATE_MANAGE)")
    public ResponseEntity<WarehouseLocationKindService.LocationKindResult> createLocationKind(
            @PathVariable String tenantSlug,
            @Valid @RequestBody UpsertLocationKindRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLocationKindService.createLocationKind(request.name()));
    }

    @PutMapping("/{locationKindId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_TEMPLATE_MANAGE)")
    public ResponseEntity<WarehouseLocationKindService.LocationKindResult> updateLocationKind(
            @PathVariable String tenantSlug,
            @PathVariable UUID locationKindId,
            @Valid @RequestBody UpsertLocationKindRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseLocationKindService.updateLocationKind(locationKindId, request.name()));
    }

    @DeleteMapping("/{locationKindId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> deleteLocationKind(
            @PathVariable String tenantSlug,
            @PathVariable UUID locationKindId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseLocationKindService.deleteLocationKind(locationKindId);
        return ResponseEntity.noContent().build();
    }

    public record UpsertLocationKindRequest(@NotBlank @Size(max = 80) String name) {
    }
}
