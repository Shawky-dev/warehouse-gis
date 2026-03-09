package com.warehouse.warehouse_platform.tenant.product;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/products")
@Validated
public class TenantProductController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantProductManagementService tenantProductManagementService;

    public TenantProductController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantProductManagementService tenantProductManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantProductManagementService = tenantProductManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_VIEW)")
    public ResponseEntity<TenantProductManagementService.ProductPageResult> listProducts(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID baseUomId,
            @RequestParam(required = false) UUID supplierId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantProductManagementService.listProducts(
                page,
                size,
                search,
                active,
                baseUomId,
                supplierId));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_VIEW)")
    public ResponseEntity<TenantProductManagementService.ProductResult> getProduct(
            @PathVariable String tenantSlug,
            @PathVariable UUID productId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantProductManagementService.getProduct(productId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_CREATE)")
    public ResponseEntity<TenantProductManagementService.ProductResult> createProduct(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateProductRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantProductManagementService.createProduct(
                request.sku(),
                request.name(),
                request.description(),
                request.baseUomId(),
                request.trackLot(),
                request.trackExpiry(),
                request.supplierIds(),
                request.primarySupplierId()));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_EDIT)")
    public ResponseEntity<TenantProductManagementService.ProductResult> updateProduct(
            @PathVariable String tenantSlug,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantProductManagementService.updateProduct(
                productId,
                request.sku(),
                request.name(),
                request.description(),
                request.baseUomId(),
                request.trackLot(),
                request.trackExpiry(),
                request.supplierIds(),
                request.primarySupplierId()));
    }

    @PostMapping("/{productId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteProduct(
            @PathVariable String tenantSlug,
            @PathVariable UUID productId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantProductManagementService.softDeleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_RESTORE)")
    public ResponseEntity<Void> restoreProduct(
            @PathVariable String tenantSlug,
            @PathVariable UUID productId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantProductManagementService.restoreProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteProduct(
            @PathVariable String tenantSlug,
            @PathVariable UUID productId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantProductManagementService.hardDeleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 60) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull UUID baseUomId,
            Boolean trackLot,
            Boolean trackExpiry,
            Set<UUID> supplierIds,
            UUID primarySupplierId) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 60) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull UUID baseUomId,
            Boolean trackLot,
            Boolean trackExpiry,
            Set<UUID> supplierIds,
            UUID primarySupplierId) {
    }
}
