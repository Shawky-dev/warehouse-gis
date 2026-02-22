package com.warehouse.warehouse_platform.landlord.user;

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
@RequestMapping("/landlord/users")
@Validated
public class LandlordUserController {

    private final LandlordUserManagementService landlordUserManagementService;

    public LandlordUserController(LandlordUserManagementService landlordUserManagementService) {
        this.landlordUserManagementService = landlordUserManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_VIEW)")
    public ResponseEntity<LandlordUserManagementService.UserPageResult> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(landlordUserManagementService.listUsers(page, size, search, active));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_CREATE)")
    public ResponseEntity<LandlordUserManagementService.UserResult> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(landlordUserManagementService.createUser(
                request.email(),
                request.password(),
                request.role()));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_EDIT)")
    public ResponseEntity<LandlordUserManagementService.UserResult> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(landlordUserManagementService.updateUser(
                userId,
                request.email(),
                request.role()));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_RESET_PASSWORD)")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        landlordUserManagementService.resetPassword(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_DEACTIVATE)")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID userId,
            Authentication authentication) {
        landlordUserManagementService.deactivateUser(userId, authentication.getName());
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
}
