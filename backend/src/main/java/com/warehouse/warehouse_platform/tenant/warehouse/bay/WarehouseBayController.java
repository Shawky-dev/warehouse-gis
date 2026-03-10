package com.warehouse.warehouse_platform.tenant.warehouse.bay;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/sides/{sideId}/bays")
@Validated
public class WarehouseBayController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final WarehouseBayService warehouseBayService;

    public WarehouseBayController(
            TenantAccessPolicy tenantAccessPolicy,
            WarehouseBayService warehouseBayService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.warehouseBayService = warehouseBayService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayService.BayPageResult> listBays(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.listBays(sideId, page, size, search, active));
    }

    @GetMapping("/{bayId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<WarehouseBayService.BayResult> getBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @PathVariable UUID bayId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.getBay(bayId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseBayService.BayResult> createBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @Valid @RequestBody CreateBayRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.createBay(sideId, request.code()));
    }

    @PutMapping("/{bayId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseBayService.BayResult> updateBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @PathVariable UUID bayId,
            @Valid @RequestBody UpdateBayRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.updateBay(bayId, request.code()));
    }

    @PostMapping("/{bayId}/soft-delete")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_SOFT_DELETE)")
    public ResponseEntity<Void> softDeleteBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @PathVariable UUID bayId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayService.softDeleteBay(bayId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bayId}/restore")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_RESTORE)")
    public ResponseEntity<Void> restoreBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @PathVariable UUID bayId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayService.restoreBay(bayId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{bayId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> hardDeleteBay(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @PathVariable UUID bayId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        warehouseBayService.hardDeleteBay(bayId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<WarehouseBayService.BulkCreateResult> createBaysBulk(
            @PathVariable String tenantSlug,
            @PathVariable UUID sideId,
            @Valid @RequestBody BulkCreateBaysRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(warehouseBayService.createBaysBulk(
                sideId, request.codes(), request.levelsPerBay(), request.shelvesPerLevel()));
    }

    public record CreateBayRequest(
            @NotBlank @Size(max = 20) String code) {
    }

    public record UpdateBayRequest(
            @NotBlank @Size(max = 20) String code) {
    }

    public record BulkCreateBaysRequest(
            @NotEmpty List<String> codes,
            @Min(1) int levelsPerBay,
            @Min(1) int shelvesPerLevel) {
    }
}
