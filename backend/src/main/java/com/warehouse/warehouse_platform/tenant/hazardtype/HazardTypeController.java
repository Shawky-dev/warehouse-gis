package com.warehouse.warehouse_platform.tenant.hazardtype;

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
@RequestMapping("/{tenantSlug}/hazard-types")
@Validated
public class HazardTypeController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final HazardTypeService hazardTypeService;

    public HazardTypeController(
            TenantAccessPolicy tenantAccessPolicy,
            HazardTypeService hazardTypeService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.hazardTypeService = hazardTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_VIEW)")
    public ResponseEntity<List<HazardTypeService.HazardTypeResult>> listAll(
            @PathVariable String tenantSlug, Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardTypeService.listAll());
    }

    @GetMapping("/{hazardTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_VIEW)")
    public ResponseEntity<HazardTypeService.HazardTypeResult> get(
            @PathVariable String tenantSlug,
            @PathVariable UUID hazardTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardTypeService.get(hazardTypeId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_CREATE)")
    public ResponseEntity<HazardTypeService.HazardTypeResult> create(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateRequest request,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardTypeService.create(request.code(), request.displayName()));
    }

    @PutMapping("/{hazardTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_EDIT)")
    public ResponseEntity<HazardTypeService.HazardTypeResult> update(
            @PathVariable String tenantSlug,
            @PathVariable UUID hazardTypeId,
            @Valid @RequestBody UpdateRequest request,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardTypeService.update(hazardTypeId, request.code(), request.displayName()));
    }

    @PostMapping("/{hazardTypeId}/deactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_DEACTIVATE)")
    public ResponseEntity<Void> deactivate(
            @PathVariable String tenantSlug,
            @PathVariable UUID hazardTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        hazardTypeService.deactivate(hazardTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{hazardTypeId}/reactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_REACTIVATE)")
    public ResponseEntity<Void> reactivate(
            @PathVariable String tenantSlug,
            @PathVariable UUID hazardTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        hazardTypeService.reactivate(hazardTypeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{hazardTypeId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).HAZARD_TYPES_HARD_DELETE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID hazardTypeId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        hazardTypeService.delete(hazardTypeId);
        return ResponseEntity.noContent().build();
    }

    // ── request records ───────────────────────────────────────────────────────

    public record CreateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String displayName) {
    }
}
