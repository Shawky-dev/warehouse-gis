package com.warehouse.warehouse_platform.tenant.uom;

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
@RequestMapping("/{tenantSlug}/uoms")
@Validated
public class TenantUomController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantUomManagementService tenantUomManagementService;

    public TenantUomController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantUomManagementService tenantUomManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantUomManagementService = tenantUomManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_VIEW)")
    public ResponseEntity<TenantUomManagementService.UomPageResult> listUoms(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUomManagementService.listUoms(page, size, search, active));
    }

    @GetMapping("/{uomId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_VIEW)")
    public ResponseEntity<TenantUomManagementService.UomResult> getUom(
            @PathVariable String tenantSlug,
            @PathVariable UUID uomId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUomManagementService.getUom(uomId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_CREATE)")
    public ResponseEntity<TenantUomManagementService.UomResult> createUom(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateUomRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUomManagementService.createUom(request.code(), request.name(), request.symbol()));
    }

    @PutMapping("/{uomId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_EDIT)")
    public ResponseEntity<TenantUomManagementService.UomResult> updateUom(
            @PathVariable String tenantSlug,
            @PathVariable UUID uomId,
            @Valid @RequestBody UpdateUomRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUomManagementService.updateUom(uomId, request.code(), request.name(), request.symbol()));
    }

    @PostMapping("/{uomId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteUom(
            @PathVariable String tenantSlug,
            @PathVariable UUID uomId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUomManagementService.softDeleteUom(uomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uomId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_RESTORE)")
    public ResponseEntity<Void> restoreUom(
            @PathVariable String tenantSlug,
            @PathVariable UUID uomId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUomManagementService.restoreUom(uomId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{uomId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteUom(
            @PathVariable String tenantSlug,
            @PathVariable UUID uomId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUomManagementService.hardDeleteUom(uomId);
        return ResponseEntity.noContent().build();
    }

    public record CreateUomRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String symbol) {
    }

    public record UpdateUomRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String symbol) {
    }
}
