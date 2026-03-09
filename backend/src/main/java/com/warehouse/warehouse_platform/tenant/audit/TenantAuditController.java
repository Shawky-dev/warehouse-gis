package com.warehouse.warehouse_platform.tenant.audit;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/{tenantSlug}/audit-logs")
@Validated
public class TenantAuditController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantAuditService tenantAuditService;

    public TenantAuditController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantAuditService tenantAuditService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantAuditService = tenantAuditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).AUDIT_VIEW)")
    public ResponseEntity<TenantAuditService.AuditPageResult> listAuditLogs(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantAuditService.listAuditLogs(
                page,
                size,
                actorEmail,
                action,
                entityType,
                entityId,
                fromDate,
                toDate));
    }
}
