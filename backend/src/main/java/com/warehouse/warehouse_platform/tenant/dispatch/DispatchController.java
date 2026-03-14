package com.warehouse.warehouse_platform.tenant.dispatch;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/dispatches")
@Validated
public class DispatchController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final DispatchService dispatchService;
    private final InventoryLedgerService inventoryLedgerService;

    public DispatchController(
            TenantAccessPolicy tenantAccessPolicy,
            DispatchService dispatchService,
            InventoryLedgerService inventoryLedgerService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.dispatchService = dispatchService;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VIEW)")
    public ResponseEntity<DispatchService.DispatchPageResult> listDispatches(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) DispatchStatus status,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.listDispatches(page, size, status, search));
    }

    @GetMapping("/{dispatchId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VIEW)")
    public ResponseEntity<DispatchService.DispatchDetailResult> getDispatch(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.getDispatch(dispatchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_CREATE)")
    public ResponseEntity<DispatchService.DispatchDetailResult> createDraft(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateDispatchDraftRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.createDraft(
                request.destination(),
                request.reference(),
                request.notes(),
                authentication.getName()));
    }

    @PostMapping("/{dispatchId}/lines")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_EDIT)")
    public ResponseEntity<DispatchService.DispatchLineResult> addLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            @Valid @RequestBody AddDispatchLineRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.addLine(
                dispatchId,
                request.productId(),
                request.sourceLocationId(),
                request.qty(),
                request.lotNumber(),
                request.notes(),
                authentication.getName()));
    }

    @PutMapping("/{dispatchId}/lines/{lineId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_EDIT)")
    public ResponseEntity<DispatchService.DispatchLineResult> updateLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateDispatchLineRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.updateLine(
                dispatchId,
                lineId,
                request.qty(),
                request.lotNumber(),
                request.notes(),
                authentication.getName()));
    }

    @DeleteMapping("/{dispatchId}/lines/{lineId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_EDIT)")
    public ResponseEntity<Void> removeLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            @PathVariable UUID lineId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        dispatchService.removeLine(dispatchId, lineId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{dispatchId}/post")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_POST)")
    public ResponseEntity<DispatchService.DispatchDetailResult> postDispatch(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.postDispatch(dispatchId, authentication.getName()));
    }

    @GetMapping("/{dispatchId}/movements")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VIEW)")
    public ResponseEntity<List<InventoryLedgerService.MovementResult>> getDispatchMovements(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(inventoryLedgerService.getMovementsBySourceDocument(dispatchId));
    }

    @PostMapping("/{dispatchId}/void")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VOID)")
    public ResponseEntity<DispatchService.DispatchDetailResult> voidDispatch(
            @PathVariable String tenantSlug,
            @PathVariable UUID dispatchId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dispatchService.voidDispatch(dispatchId, authentication.getName()));
    }

    public record CreateDispatchDraftRequest(
            @Size(max = 200) String destination,
            @Size(max = 120) String reference,
            @Size(max = 500) String notes) {
    }

    public record AddDispatchLineRequest(
            @NotNull UUID productId,
            @NotNull UUID sourceLocationId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @Size(max = 100) String lotNumber,
            @Size(max = 500) String notes) {
    }

    public record UpdateDispatchLineRequest(
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @Size(max = 100) String lotNumber,
            @Size(max = 500) String notes) {
    }
}
