package com.warehouse.warehouse_platform.tenant.counting;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/count-sessions")
@Validated
public class CountSessionController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final CountSessionService countSessionService;
    private final InventoryLedgerService inventoryLedgerService;

    public CountSessionController(
            TenantAccessPolicy tenantAccessPolicy,
            CountSessionService countSessionService,
            InventoryLedgerService inventoryLedgerService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.countSessionService = countSessionService;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VIEW)")
    public ResponseEntity<CountSessionService.CountSessionPageResult> listSessions(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) CountStatus status,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.listSessions(page, size, status, search));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VIEW)")
    public ResponseEntity<CountSessionService.CountSessionDetailResult> getSession(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.getSession(sessionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_CREATE)")
    public ResponseEntity<CountSessionService.CountSessionDetailResult> openSession(
            @PathVariable String tenantSlug,
            @Valid @RequestBody OpenSessionRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.openSession(
                request.name(),
                request.locationIds(),
                authentication.getName()));
    }

    @PutMapping("/{sessionId}/lines/{lineId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_CREATE)")
    public ResponseEntity<CountSessionService.CountLineResult> updateCountLine(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateCountLineRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.updateCountLine(
                sessionId,
                lineId,
                request.countedQty(),
                authentication.getName()));
    }

    @PostMapping("/{sessionId}/post")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_POST)")
    public ResponseEntity<CountSessionService.CountSessionDetailResult> postSession(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.postSession(sessionId, authentication.getName()));
    }

    @GetMapping("/{sessionId}/movements")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VIEW)")
    public ResponseEntity<List<InventoryLedgerService.MovementResult>> getSessionMovements(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(inventoryLedgerService.getMovementsBySourceDocument(sessionId));
    }

    @PostMapping("/{sessionId}/void")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VOID)")
    public ResponseEntity<CountSessionService.CountSessionDetailResult> voidSession(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(countSessionService.voidSession(sessionId, authentication.getName()));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_CREATE)")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String tenantSlug,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        countSessionService.deleteDraft(sessionId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    public record OpenSessionRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty List<@NotNull UUID> locationIds) {
    }

    public record UpdateCountLineRequest(
            @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal countedQty) {
    }
}