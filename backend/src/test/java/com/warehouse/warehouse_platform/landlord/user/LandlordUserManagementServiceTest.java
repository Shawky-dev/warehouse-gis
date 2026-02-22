package com.warehouse.warehouse_platform.landlord.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandlordUserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private LandlordUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new LandlordUserManagementService(userRepository, roleRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void createUser_shouldNormalizeEmailEncodePasswordAndPersistRole() {
        when(roleRepository.existsById("MANAGER")).thenReturn(true);
        when(userRepository.findByEmail("manager@system.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
            user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return user;
        });

        LandlordUserManagementService.UserResult result = service.createUser(
                " Manager@System.local ",
                "plain-password",
                "manager");

        assertEquals("manager@system.local", result.email());
        assertEquals("MANAGER", result.role());
        assertEquals(true, result.active());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("manager@system.local", userCaptor.getValue().getEmail());
        assertEquals("encoded-password", userCaptor.getValue().getPassword());
        assertEquals("MANAGER", userCaptor.getValue().getRole());
    }

    @Test
    void createUser_shouldRejectExistingEmail() {
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("admin@system.local");

        when(roleRepository.existsById("ADMIN")).thenReturn(true);
        when(userRepository.findByEmail("admin@system.local")).thenReturn(Optional.of(existing));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.createUser("admin@system.local", "password123", "ADMIN"));

        assertEquals("CONFLICT", exception.getCode());
    }

    @Test
    void createUser_shouldAllowNewCustomRoleWhenRoleExists() {
        when(roleRepository.existsById("AUDITOR")).thenReturn(true);
        when(userRepository.findByEmail("auditor@system.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
            user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return user;
        });

        LandlordUserManagementService.UserResult result = service.createUser(
                "auditor@system.local",
                "plain-password",
                "auditor");

        assertEquals("AUDITOR", result.role());
    }

    @Test
    void listUsers_shouldReturnPagedContent() {
        User user = new User();
        user.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        user.setEmail("admin@system.local");
        user.setRole("ADMIN");
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(userRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        LandlordUserManagementService.UserPageResult result = service.listUsers(0, 20, "admin", true);

        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals("admin@system.local", result.content().getFirst().email());
    }

    @Test
    void resetPassword_shouldEncodeAndRevokeActiveSessions() {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        User user = new User();
        user.setId(userId);
        user.setEmail("manager@system.local");
        user.setRole("MANAGER");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new-password");

        service.resetPassword(userId, "newPassword123");

        assertEquals("encoded-new-password", user.getPassword());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllActiveForUser(userId, "PASSWORD_RESET");
    }

    @Test
    void deactivateUser_shouldRejectSelfDeactivation() {
        UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        User user = new User();
        user.setId(userId);
        user.setEmail("admin@system.local");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.deactivateUser(userId, "admin@system.local"));

        assertEquals("FORBIDDEN", exception.getCode());
    }
}
