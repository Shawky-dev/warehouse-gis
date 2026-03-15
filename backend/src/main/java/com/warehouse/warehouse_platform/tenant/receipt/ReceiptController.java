package com.warehouse.warehouse_platform.tenant.receipt;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/receipts")
@Validated
public class ReceiptController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final ReceiptService receiptService;
    private final InventoryLedgerService inventoryLedgerService;

    public ReceiptController(
            TenantAccessPolicy tenantAccessPolicy,
            ReceiptService receiptService,
            InventoryLedgerService inventoryLedgerService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.receiptService = receiptService;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VIEW)")
    public ResponseEntity<ReceiptService.ReceiptPageResult> listReceipts(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ReceiptStatus status,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.listReceipts(page, size, status, search));
    }

    @GetMapping("/{receiptId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VIEW)")
    public ResponseEntity<ReceiptService.ReceiptDetailResult> getReceipt(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.getReceipt(receiptId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_CREATE)")
    public ResponseEntity<ReceiptService.ReceiptDetailResult> createDraft(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateReceiptDraftRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.createDraft(
                request.supplierId(),
                request.reference(),
                request.notes(),
                authentication.getName()));
    }

    @PostMapping("/{receiptId}/lines")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_EDIT)")
    public ResponseEntity<ReceiptService.ReceiptLineResult> addLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            @Valid @RequestBody AddReceiptLineRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.addLine(
                receiptId,
                request.productId(),
                request.destinationLocationId(),
                request.qty(),
                request.lotNumber(),
                request.expiryDate(),
                request.notes(),
                authentication.getName()));
    }

    @PutMapping("/{receiptId}/lines/{lineId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_EDIT)")
    public ResponseEntity<ReceiptService.ReceiptLineResult> updateLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateReceiptLineRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.updateLine(
                receiptId,
                lineId,
                request.qty(),
                request.lotNumber(),
                request.expiryDate(),
                request.notes(),
                authentication.getName()));
    }

    @DeleteMapping("/{receiptId}/lines/{lineId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_EDIT)")
    public ResponseEntity<Void> removeLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            @PathVariable UUID lineId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        receiptService.removeLine(receiptId, lineId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{receiptId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_EDIT)")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        receiptService.deleteDraft(receiptId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{receiptId}/post")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_POST)")
    public ResponseEntity<ReceiptService.ReceiptDetailResult> postReceipt(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.postReceipt(receiptId, authentication.getName()));
    }

    @GetMapping("/{receiptId}/movements")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VIEW)")
    public ResponseEntity<List<InventoryLedgerService.MovementResult>> getReceiptMovements(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(inventoryLedgerService.getMovementsBySourceDocument(receiptId));
    }

    @PostMapping("/{receiptId}/void")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VOID)")
    public ResponseEntity<ReceiptService.ReceiptDetailResult> voidReceipt(
            @PathVariable String tenantSlug,
            @PathVariable UUID receiptId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(receiptService.voidReceipt(receiptId, authentication.getName()));
    }

    public record CreateReceiptDraftRequest(
            UUID supplierId,
            @Size(max = 120) String reference,
            @Size(max = 500) String notes) {
    }

    public record AddReceiptLineRequest(
            @NotNull UUID productId,
            @NotNull UUID destinationLocationId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @Size(max = 100) String lotNumber,
            LocalDate expiryDate,
            @Size(max = 500) String notes) {
    }

    public record UpdateReceiptLineRequest(
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @Size(max = 100) String lotNumber,
            LocalDate expiryDate,
            @Size(max = 500) String notes) {
    }
}
