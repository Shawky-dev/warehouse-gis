package com.warehouse.warehouse_platform.tenant.user;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/users")
@Validated
public class TenantUserController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantUserManagementService tenantUserManagementService;

    public TenantUserController(
            TenantAccessPolicy tenantAccessPolicy,
            TenantUserManagementService tenantUserManagementService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_VIEW)")
    public ResponseEntity<TenantUserManagementService.UserPageResult> listUsers(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUserManagementService.listUsers(page, size, search, active));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_CREATE)")
    public ResponseEntity<TenantUserManagementService.UserResult> createUser(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUserManagementService.createUser(
                request.email(),
                request.password(),
                request.role(),
                isAdmin(authentication)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_EDIT)")
    public ResponseEntity<TenantUserManagementService.UserResult> updateUser(
            @PathVariable String tenantSlug,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(tenantUserManagementService.updateUser(
                userId,
                request.email(),
                request.role(),
                isAdmin(authentication)));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_RESET_PASSWORD)")
    public ResponseEntity<Void> resetPassword(
            @PathVariable String tenantSlug,
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUserManagementService.resetPassword(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_DEACTIVATE)")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable String tenantSlug,
            @PathVariable UUID userId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUserManagementService.deactivateUser(userId, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_REACTIVATE)")
    public ResponseEntity<Void> reactivateUser(
            @PathVariable String tenantSlug,
            @PathVariable UUID userId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        tenantUserManagementService.reactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String role) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email String email,
            @NotBlank String role) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8) String newPassword) {
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
