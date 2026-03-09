package com.warehouse.warehouse_platform.tenant.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantUserManagementService(
                userRepository,
                roleRepository,
                passwordEncoder,
                refreshTokenService,
                tenantAuditService);
    }

    @Test
    void createUser_shouldAuditWriteAction() {
        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(role("MANAGER", false)));
        when(userRepository.findByEmail("manager@acme.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
            user.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            user.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return user;
        });

        TenantUserManagementService.UserResult result = service.createUser(
                "manager@acme.local",
                "password123",
                "manager",
                true);

        assertEquals("MANAGER", result.role());
        verify(tenantAuditService).record(eq("USER_CREATE"), eq("USER"), eq(result.id().toString()), eq(null), any());
    }

    @Test
    void updateUser_shouldAuditBeforeAndAfter() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        User user = user(userId, "old@acme.local", "MANAGER", true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(role("MANAGER", false)));
        when(userRepository.findByEmail("new@acme.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantUserManagementService.UserResult result = service.updateUser(
                userId,
                "new@acme.local",
                "MANAGER",
                true);

        assertEquals("new@acme.local", result.email());
        verify(tenantAuditService).record(eq("USER_UPDATE"), eq("USER"), eq(userId.toString()), any(), any());
    }

    @Test
    void resetPassword_shouldAuditAction() {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        User user = user(userId, "manager@acme.local", "MANAGER", true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new");

        service.resetPassword(userId, "newPassword123");

        verify(refreshTokenService).revokeAllActiveForUser(userId, "PASSWORD_RESET");
        verify(tenantAuditService).record(eq("USER_RESET_PASSWORD"), eq("USER"), eq(userId.toString()), any(), any());
    }

    private Role role(String code, boolean locked) {
        return Role.builder()
                .code(code)
                .name(code)
                .locked(locked)
                .build();
    }

    private User user(UUID id, String email, String role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
