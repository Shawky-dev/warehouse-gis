package com.warehouse.warehouse_platform.landlord;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warehouse.warehouse_platform.multi_tenancy.service.TenantCreationException;
import com.warehouse.warehouse_platform.multi_tenancy.service.TenantManagementService;
import com.warehouse.warehouse_platform.multi_tenancy.service.TenantSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/landlord")
public class LandlordController {

    private final LandlordAccessService landlordAccessService;
    private final TenantManagementService tenantManagementService;

    public LandlordController(
            LandlordAccessService landlordAccessService,
            TenantManagementService tenantManagementService) {
        this.landlordAccessService = landlordAccessService;
        this.tenantManagementService = tenantManagementService;
    }

    @GetMapping("/session")
    public ResponseEntity<LandlordAccessService.LandlordSessionResponse> session(Authentication authentication) {
        return ResponseEntity.ok(landlordAccessService.getAdminSession(authentication));
    }

    @PostMapping("/tenants")
    public ResponseEntity<Void> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        tenantManagementService.createTenant(request.tenantId(), request.schema());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantSummaryResponse>> getTenants() {
        List<TenantSummaryResponse> tenants = tenantManagementService.getTenants().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(tenants);
    }

    @ExceptionHandler(TenantCreationException.class)
    public ResponseEntity<String> onTenantCreationError(TenantCreationException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank String schema) {
    }

    public record TenantSummaryResponse(
            String tenantId,
            String schema) {
    }

    private TenantSummaryResponse toResponse(TenantSummary tenantSummary) {
        return new TenantSummaryResponse(tenantSummary.tenantId(), tenantSummary.schema());
    }
}
