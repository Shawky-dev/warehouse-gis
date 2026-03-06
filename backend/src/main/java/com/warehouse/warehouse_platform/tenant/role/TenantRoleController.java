package com.warehouse.warehouse_platform.tenant.role;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/{tenantSlug}")
@Validated
public class TenantRoleController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantRoleManagementService tenantRoleManagementService;

    public TenantRoleController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantRoleManagementService tenantRoleManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantRoleManagementService = tenantRoleManagementService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)")
    public ResponseEntity<List<TenantRoleManagementService.RoleDetails>> listRoles(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantRoleManagementService.listRoles());
    }

    @GetMapping("/roles/{roleCode}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)")
    public ResponseEntity<TenantRoleManagementService.RoleDetails> getRole(
            @PathVariable String tenantSlug,
            @PathVariable String roleCode,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantRoleManagementService.getRole(roleCode));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)")
    public ResponseEntity<List<TenantRoleManagementService.PermissionOption>> listPermissions(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantRoleManagementService.listPermissions());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenantRoleManagementService.RoleDetails> createRole(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateRoleRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        TenantRoleManagementService.RoleDetails createdRole = tenantRoleManagementService.createRole(
                request.code(),
                request.name(),
                request.description(),
                request.permissionCodes(),
                request.locked());
        return ResponseEntity.ok(createdRole);
    }

    @PutMapping("/roles/{roleCode}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)")
    public ResponseEntity<TenantRoleManagementService.RoleDetails> updateRole(
            @PathVariable String tenantSlug,
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        TenantRoleManagementService.RoleDetails updatedRole = tenantRoleManagementService.updateRole(
                roleCode,
                request.name(),
                request.description(),
                request.permissionCodes(),
                request.locked(),
                isAdmin(authentication));
        return ResponseEntity.ok(updatedRole);
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<@NotBlank String> permissionCodes,
            @NotNull Boolean locked) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<@NotBlank String> permissionCodes,
            @NotNull Boolean locked) {
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
