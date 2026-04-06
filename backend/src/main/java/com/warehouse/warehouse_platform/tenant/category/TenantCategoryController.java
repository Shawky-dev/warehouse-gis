package com.warehouse.warehouse_platform.tenant.category;

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
@RequestMapping("/{tenantSlug}/categories")
@Validated
public class TenantCategoryController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantCategoryManagementService tenantCategoryManagementService;

    public TenantCategoryController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantCategoryManagementService tenantCategoryManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantCategoryManagementService = tenantCategoryManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_VIEW)")
    public ResponseEntity<TenantCategoryManagementService.CategoryPageResult> listCategories(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantCategoryManagementService.listCategories(page, size, search, active));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_VIEW)")
    public ResponseEntity<TenantCategoryManagementService.CategoryResult> getCategory(
            @PathVariable String tenantSlug,
            @PathVariable UUID categoryId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantCategoryManagementService.getCategory(categoryId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_CREATE)")
    public ResponseEntity<TenantCategoryManagementService.CategoryResult> createCategory(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateCategoryRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantCategoryManagementService.createCategory(
                request.name(), request.code(), request.displayName(), request.description(),
                request.requiredZoneTypeId()));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_EDIT)")
    public ResponseEntity<TenantCategoryManagementService.CategoryResult> updateCategory(
            @PathVariable String tenantSlug,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantCategoryManagementService.updateCategory(
                categoryId, request.name(), request.code(), request.displayName(), request.description(),
                request.requiredZoneTypeId()));
    }

    @PostMapping("/{categoryId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteCategory(
            @PathVariable String tenantSlug,
            @PathVariable UUID categoryId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantCategoryManagementService.softDeleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{categoryId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_RESTORE)")
    public ResponseEntity<Void> restoreCategory(
            @PathVariable String tenantSlug,
            @PathVariable UUID categoryId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantCategoryManagementService.restoreCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteCategory(
            @PathVariable String tenantSlug,
            @PathVariable UUID categoryId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantCategoryManagementService.hardDeleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String code,
            @Size(max = 120) String displayName,
            @Size(max = 500) String description,
            UUID requiredZoneTypeId) {
    }

    public record UpdateCategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String code,
            @Size(max = 120) String displayName,
            @Size(max = 500) String description,
            UUID requiredZoneTypeId) {
    }
}
