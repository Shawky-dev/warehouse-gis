package com.warehouse.warehouse_platform.landlord.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
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
        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(role("MANAGER", false)));
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

        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(role("ADMIN", true)));
        when(userRepository.findByEmail("admin@system.local")).thenReturn(Optional.of(existing));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.createUser("admin@system.local", "password123", "ADMIN"));

        assertEquals("CONFLICT", exception.getCode());
    }

    @Test
    void createUser_shouldAllowNewCustomRoleWhenRoleExists() {
        when(roleRepository.findById("AUDITOR")).thenReturn(Optional.of(role("AUDITOR", false)));
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
    void createUser_shouldRejectLockedRoleForNonAdmin() {
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(role("ADMIN", true)));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.createUser("new.admin@system.local", "password123", "ADMIN", false));

        assertEquals("FORBIDDEN", exception.getCode());
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
    void updateUser_shouldRejectLockedRoleTransitionsForNonAdmin() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        User user = new User();
        user.setId(userId);
        user.setEmail("locked@system.local");
        user.setRole("ADMIN");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(role("ADMIN", true)));
        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(role("MANAGER", false)));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.updateUser(userId, "locked@system.local", "MANAGER", false));

        assertEquals("FORBIDDEN", exception.getCode());
    }

    @Test
    void updateUser_shouldAllowLockedRoleTransitionsForAdmin() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        User user = new User();
        user.setId(userId);
        user.setEmail("manager@system.local");
        user.setRole("MANAGER");
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(role("MANAGER", false)));
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(role("ADMIN", true)));
        when(userRepository.findByEmail("manager@system.local")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LandlordUserManagementService.UserResult result = service.updateUser(
                userId,
                "manager@system.local",
                "ADMIN",
                true);

        assertEquals("ADMIN", result.role());
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
                () -> service.deactivateUser(userId, "admin@system.local", true));

        assertEquals("FORBIDDEN", exception.getCode());
    }

    @Test
    void deactivateUser_shouldRejectNonAdminWhenTargetUserIsAdmin() {
        UUID userId = UUID.fromString("efefefef-efef-efef-efef-efefefefefef");
        User user = new User();
        user.setId(userId);
        user.setEmail("admin@system.local");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LandlordUserManagementException exception = assertThrows(
                LandlordUserManagementException.class,
                () -> service.deactivateUser(userId, "manager@system.local", false));

        assertEquals("FORBIDDEN", exception.getCode());
        assertEquals("Only admins can deactivate admin accounts", exception.getMessage());
    }

    @Test
    void deactivateUser_shouldAllowAdminWhenTargetUserIsAdmin() {
        UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        User user = new User();
        user.setId(userId);
        user.setEmail("other.admin@system.local");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.deactivateUser(userId, "super.admin@system.local", true);

        assertEquals(false, user.getActive());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllActiveForUser(userId, "DEACTIVATED");
    }

    @Test
    void reactivateUser_shouldSetActiveAndClearDeactivatedAt() {
        UUID userId = UUID.fromString("abababab-abab-abab-abab-abababababab");
        User user = new User();
        user.setId(userId);
        user.setEmail("manager@system.local");
        user.setRole("MANAGER");
        user.setActive(false);
        user.setDeactivatedAt(Instant.parse("2026-01-15T00:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.reactivateUser(userId);

        assertEquals(true, user.getActive());
        assertEquals(null, user.getDeactivatedAt());
        verify(userRepository).save(user);
    }

    private Role role(String code, boolean locked) {
        return Role.builder()
                .code(code)
                .name(code)
                .locked(locked)
                .build();
    }
}
