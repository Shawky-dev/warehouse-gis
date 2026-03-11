package com.warehouse.warehouse_platform.tenant.warehouse.block;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/block-templates")
@Validated
public class BlockTemplateController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final BlockTemplateService blockTemplateService;

    public BlockTemplateController(
            TenantAccessPolicy tenantAccessPolicy,
            BlockTemplateService blockTemplateService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.blockTemplateService = blockTemplateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<BlockTemplateService.TemplatePageResult> listTemplates(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(blockTemplateService.listTemplates(page, size, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
    public ResponseEntity<BlockTemplateService.TemplateResult> getTemplate(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(blockTemplateService.getTemplate(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<BlockTemplateService.TemplateResult> createTemplate(
            @PathVariable String tenantSlug,
            @Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(blockTemplateService.createTemplate(
                request.name(), request.identifierFormat(), request.sideConfig(),
                request.sideOptions(), request.required(), request.description(), request.iconName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_EDIT)")
    public ResponseEntity<BlockTemplateService.TemplateResult> updateTemplate(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            @Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(blockTemplateService.updateTemplate(
                id, request.name(), request.identifierFormat(), request.sideConfig(),
                request.sideOptions(), request.required(), request.description(), request.iconName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        blockTemplateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull BlockTemplate.IdentifierFormat identifierFormat,
            BlockTemplate.SideConfig sideConfig,
            List<String> sideOptions,
            boolean required,
            @Size(max = 500) String description,
            @Size(max = 100) String iconName) {
    }
}
