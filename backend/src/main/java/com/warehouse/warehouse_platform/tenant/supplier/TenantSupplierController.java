package com.warehouse.warehouse_platform.tenant.supplier;

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
@RequestMapping("/{tenantSlug}/suppliers")
@Validated
public class TenantSupplierController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantSupplierManagementService tenantSupplierManagementService;

    public TenantSupplierController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantSupplierManagementService tenantSupplierManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantSupplierManagementService = tenantSupplierManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_VIEW)")
    public ResponseEntity<TenantSupplierManagementService.SupplierPageResult> listSuppliers(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantSupplierManagementService.listSuppliers(page, size, search, active));
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_VIEW)")
    public ResponseEntity<TenantSupplierManagementService.SupplierResult> getSupplier(
            @PathVariable String tenantSlug,
            @PathVariable UUID supplierId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantSupplierManagementService.getSupplier(supplierId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_CREATE)")
    public ResponseEntity<TenantSupplierManagementService.SupplierResult> createSupplier(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateSupplierRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantSupplierManagementService.createSupplier(
                request.code(),
                request.name(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone(),
                request.notes()));
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_EDIT)")
    public ResponseEntity<TenantSupplierManagementService.SupplierResult> updateSupplier(
            @PathVariable String tenantSlug,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateSupplierRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantSupplierManagementService.updateSupplier(
                supplierId,
                request.code(),
                request.name(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone(),
                request.notes()));
    }

    @PostMapping("/{supplierId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteSupplier(
            @PathVariable String tenantSlug,
            @PathVariable UUID supplierId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantSupplierManagementService.softDeleteSupplier(supplierId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{supplierId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_RESTORE)")
    public ResponseEntity<Void> restoreSupplier(
            @PathVariable String tenantSlug,
            @PathVariable UUID supplierId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantSupplierManagementService.restoreSupplier(supplierId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{supplierId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteSupplier(
            @PathVariable String tenantSlug,
            @PathVariable UUID supplierId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantSupplierManagementService.hardDeleteSupplier(supplierId);
        return ResponseEntity.noContent().build();
    }

    public record CreateSupplierRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactName,
            @Size(max = 255) String contactEmail,
            @Size(max = 60) String contactPhone,
            @Size(max = 500) String notes) {
    }

    public record UpdateSupplierRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactName,
            @Size(max = 255) String contactEmail,
            @Size(max = 60) String contactPhone,
            @Size(max = 500) String notes) {
    }
}
