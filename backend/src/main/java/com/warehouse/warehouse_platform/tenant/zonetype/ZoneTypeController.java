package com.warehouse.warehouse_platform.tenant.zonetype;

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
@RequestMapping("/{tenantSlug}/zone-types")
@Validated
public class ZoneTypeController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final ZoneTypeService zoneTypeService;

    public ZoneTypeController(
            TenantAccessPolicy tenantAccessPolicy,
            ZoneTypeService zoneTypeService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.zoneTypeService = zoneTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_VIEW)")
    public ResponseEntity<List<ZoneTypeService.ZoneTypeResult>> listAll(
            @PathVariable String tenantSlug, Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(zoneTypeService.listAll());
    }

    @GetMapping("/{zoneTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_VIEW)")
    public ResponseEntity<ZoneTypeService.ZoneTypeResult> get(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(zoneTypeService.get(zoneTypeId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_CREATE)")
    public ResponseEntity<ZoneTypeService.ZoneTypeResult> create(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateRequest request,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(zoneTypeService.create(request.code(), request.displayName()));
    }

    @PutMapping("/{zoneTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_EDIT)")
    public ResponseEntity<ZoneTypeService.ZoneTypeResult> update(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneTypeId,
            @Valid @RequestBody UpdateRequest request,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(zoneTypeService.update(zoneTypeId, request.code(), request.displayName()));
    }

    @PostMapping("/{zoneTypeId}/deactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_DEACTIVATE)")
    public ResponseEntity<Void> deactivate(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        zoneTypeService.deactivate(zoneTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{zoneTypeId}/reactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_REACTIVATE)")
    public ResponseEntity<Void> reactivate(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        zoneTypeService.reactivate(zoneTypeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{zoneTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ZONE_TYPES_HARD_DELETE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        zoneTypeService.delete(zoneTypeId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String displayName) {
    }
}
