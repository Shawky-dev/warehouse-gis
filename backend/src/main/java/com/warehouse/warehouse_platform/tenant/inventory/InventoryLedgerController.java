package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/inventory")
@Validated
public class InventoryLedgerController {

        private final TenantAccessPolicy tenantAccessPolicy;
        private final InventoryLedgerService inventoryLedgerService;

        public InventoryLedgerController(
                        TenantAccessPolicy tenantAccessPolicy,
                        InventoryLedgerService inventoryLedgerService) {
                this.tenantAccessPolicy = tenantAccessPolicy;
                this.inventoryLedgerService = inventoryLedgerService;
        }

        // -------------------------------------------------------------------------
        // Stock queries
        // -------------------------------------------------------------------------

        @GetMapping("/stock")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<List<InventoryLedgerService.StockResult>> getStock(
                        @PathVariable String tenantSlug,
                        @RequestParam(required = false) UUID productId,
                        @RequestParam(required = false) UUID locationId,
                        @RequestParam(required = false) String locationKind,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getStock(productId, locationId, locationKind));
        }

        @GetMapping("/stock/by-location/{locationId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<List<InventoryLedgerService.StockResult>> getStockByLocation(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID locationId,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getStock(null, locationId));
        }

        @GetMapping("/stock/by-product/{productId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<List<InventoryLedgerService.StockResult>> getStockByProduct(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID productId,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getStock(productId, null));
        }

        @GetMapping("/lookups/products")
        @PreAuthorize("hasAnyAuthority("
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST"
                        + ")")
        public ResponseEntity<InventoryLedgerService.ProductLookupPageResult> getProductLookups(
                        @PathVariable String tenantSlug,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.listProductLookups(page, size, search));
        }

        @GetMapping("/lookups/locations")
        @PreAuthorize("hasAnyAuthority("
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER,"
                        + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST"
                        + ")")
        public ResponseEntity<InventoryLedgerService.LocationLookupPageResult> getLocationLookups(
                        @PathVariable String tenantSlug,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.listLocationLookups(page, size, search));
        }

        // -------------------------------------------------------------------------
        // Movement history
        // -------------------------------------------------------------------------

        @GetMapping("/movements")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<InventoryLedgerService.MovementPageResult> getMovements(
                        @PathVariable String tenantSlug,
                        @RequestParam(required = false) UUID productId,
                        @RequestParam(required = false) UUID locationId,
                        @RequestParam(required = false) UUID sourceDocumentId,
                        @RequestParam(required = false) MovementType movementType,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getMovements(productId, locationId, sourceDocumentId, movementType, page, size));
        }

        @GetMapping("/movements/by-location/{locationId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<InventoryLedgerService.MovementPageResult> getMovementsByLocation(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID locationId,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getMovements(null, locationId, page, size));
        }

        @GetMapping("/movements/by-product/{productId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)")
        public ResponseEntity<InventoryLedgerService.MovementPageResult> getMovementsByProduct(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID productId,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(inventoryLedgerService.getMovements(productId, null, page, size));
        }

        // -------------------------------------------------------------------------
        // Operations
        // -------------------------------------------------------------------------

        @PostMapping("/receive")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE)")
        public ResponseEntity<InventoryLedgerService.MovementResult> receive(
                        @PathVariable String tenantSlug,
                        @Valid @RequestBody ReceiveRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                String actor = authentication.getName();
                return ResponseEntity.ok(inventoryLedgerService.receive(
                                request.locationId(),
                                request.productId(),
                                request.qty(),
                                request.lotNumber(),
                                request.expiryDate(),
                                request.notes(),
                                null,
                                null,
                                actor));
        }

        @PostMapping("/transfer")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER)")
        public ResponseEntity<InventoryLedgerService.TransferResult> transfer(
                        @PathVariable String tenantSlug,
                        @Valid @RequestBody TransferRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                String actor = authentication.getName();
                return ResponseEntity.ok(inventoryLedgerService.transfer(
                                request.fromLocationId(),
                                request.toLocationId(),
                                request.productId(),
                                request.qty(),
                                request.lotNumber(),
                                request.notes(),
                                null,
                                actor));
        }

        @PostMapping("/adjust")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST)")
        public ResponseEntity<InventoryLedgerService.MovementResult> adjust(
                        @PathVariable String tenantSlug,
                        @Valid @RequestBody AdjustRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                String actor = authentication.getName();
                return ResponseEntity.ok(inventoryLedgerService.adjust(
                                request.locationId(),
                                request.productId(),
                                request.qty(),
                                request.lotNumber(),
                                request.notes(),
                                null,
                                request.reasonCode(),
                                actor));
        }

        // -------------------------------------------------------------------------
        // Request records
        // -------------------------------------------------------------------------

        public record ReceiveRequest(
                        @NotNull UUID locationId,
                        @NotNull UUID productId,
                        @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
                        @Size(max = 100) String lotNumber,
                        LocalDate expiryDate,
                        @Size(max = 500) String notes) {
        }

        public record TransferRequest(
                        @NotNull UUID fromLocationId,
                        @NotNull UUID toLocationId,
                        @NotNull UUID productId,
                        @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
                        @Size(max = 100) String lotNumber,
                        @Size(max = 500) String notes) {
        }

        public record AdjustRequest(
                        @NotNull UUID locationId,
                        @NotNull UUID productId,
                        @NotNull BigDecimal qty,
                        @Size(max = 100) String lotNumber,
                        @NotNull @Size(min = 1, max = 500) String notes,
                        @Size(max = 50) String reasonCode) {
        }
}
