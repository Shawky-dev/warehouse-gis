package com.warehouse.warehouse_platform.landlord.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/landlord")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class LandlordRoleController {

    private final LandlordRoleManagementService landlordRoleManagementService;

    public LandlordRoleController(LandlordRoleManagementService landlordRoleManagementService) {
        this.landlordRoleManagementService = landlordRoleManagementService;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<LandlordRoleManagementService.RoleDetails>> listRoles() {
        return ResponseEntity.ok(landlordRoleManagementService.listRoles());
    }

    @GetMapping("/roles/{roleCode}")
    public ResponseEntity<LandlordRoleManagementService.RoleDetails> getRole(@PathVariable String roleCode) {
        return ResponseEntity.ok(landlordRoleManagementService.getRole(roleCode));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<LandlordRoleManagementService.PermissionOption>> listPermissions() {
        return ResponseEntity.ok(landlordRoleManagementService.listPermissions());
    }

    @PostMapping("/roles")
    public ResponseEntity<LandlordRoleManagementService.RoleDetails> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        LandlordRoleManagementService.RoleDetails createdRole = landlordRoleManagementService.createRole(
                request.code(),
                request.name(),
                request.description(),
                request.permissionCodes());
        return ResponseEntity.ok(createdRole);
    }

    @PutMapping("/roles/{roleCode}")
    public ResponseEntity<LandlordRoleManagementService.RoleDetails> updateRole(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRoleRequest request) {
        LandlordRoleManagementService.RoleDetails updatedRole = landlordRoleManagementService.updateRole(
                roleCode,
                request.name(),
                request.description(),
                request.permissionCodes());
        return ResponseEntity.ok(updatedRole);
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<@NotBlank String> permissionCodes) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<@NotBlank String> permissionCodes) {
    }
}
