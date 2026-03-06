package com.warehouse.warehouse_platform.rbac.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class UserManagementCoreService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserManagementCoreService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public UserPageResult listUsers(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = normalizeSearch(search);
        Page<User> userPage = userRepository.findAll(buildUserListSpecification(normalizedSearch, active), pageable);

        List<UserResult> content = userPage.getContent().stream()
                .map(this::toResult)
                .toList();

        return new UserPageResult(
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages());
    }

    public UserResult createUser(String email, String password, String role) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        Role targetRole = loadRoleOrThrow(normalizedRole);

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw UserManagementCoreException.conflict("Email already exists: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(targetRole.getCode())
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        return toResult(savedUser);
    }

    public UserResult updateUser(UUID userId, String email, String role) {
        User user = loadUserOrThrow(userId);

        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        Role targetRole = loadRoleOrThrow(normalizedRole);

        userRepository.findByEmail(normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw UserManagementCoreException.conflict("Email already exists: " + normalizedEmail);
                });

        user.setEmail(normalizedEmail);
        user.setRole(targetRole.getCode());

        User savedUser = userRepository.save(user);
        return toResult(savedUser);
    }

    public void resetPassword(UUID userId, String newPassword) {
        User user = loadUserOrThrow(userId);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllActiveForUser(userId, "PASSWORD_RESET");
    }

    public void deactivateUser(UUID userId, String actorEmail, boolean actorIsAdmin) {
        User user = loadUserOrThrow(userId);

        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw UserManagementCoreException.forbidden("You cannot deactivate your own account");
        }

        if (!actorIsAdmin && ADMIN_ROLE.equalsIgnoreCase(user.getRole())) {
            throw UserManagementCoreException.forbidden("Only admins can deactivate admin accounts");
        }

        if (!Boolean.FALSE.equals(user.getActive())) {
            user.setActive(false);
            user.setDeactivatedAt(Instant.now());
            userRepository.save(user);
        }

        refreshTokenService.revokeAllActiveForUser(userId, "DEACTIVATED");
    }

    public void reactivateUser(UUID userId) {
        User user = loadUserOrThrow(userId);

        if (!Boolean.TRUE.equals(user.getActive()) || user.getDeactivatedAt() != null) {
            user.setActive(true);
            user.setDeactivatedAt(null);
            userRepository.save(user);
        }
    }

    public UserResult createUser(String email, String password, String role, boolean actorIsAdmin) {
        String normalizedRole = normalizeRole(role);
        Role targetRole = loadRoleOrThrow(normalizedRole);
        if (!actorIsAdmin && isRoleLocked(targetRole)) {
            throw UserManagementCoreException.forbidden("Locked roles can only be assigned by admins");
        }
        return createUser(email, password, targetRole.getCode());
    }

    public UserResult updateUser(UUID userId, String email, String role, boolean actorIsAdmin) {
        User user = loadUserOrThrow(userId);
        Role currentRole = loadRoleOrThrow(normalizeRole(user.getRole()));
        Role targetRole = loadRoleOrThrow(normalizeRole(role));

        if (!actorIsAdmin && (isRoleLocked(currentRole) || isRoleLocked(targetRole))) {
            throw UserManagementCoreException.forbidden(
                    "Locked roles can only be assigned or removed by admins");
        }

        return updateUser(userId, email, targetRole.getCode());
    }

    private User loadUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> UserManagementCoreException.notFound("User not found: " + userId));
    }

    private Role loadRoleOrThrow(String roleCode) {
        return roleRepository.findById(roleCode)
                .orElseThrow(() -> UserManagementCoreException.badRequest("Role not found: " + roleCode));
    }

    private boolean isRoleLocked(Role role) {
        return Boolean.TRUE.equals(role.getLocked());
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw UserManagementCoreException.badRequest("role must not be blank");
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw UserManagementCoreException.badRequest("email must not be blank");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }

        String normalized = search.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Specification<User> buildUserListSpecification(String search, Boolean active) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            if (search != null) {
                String likeValue = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likeValue));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private UserResult toResult(User user) {
        return new UserResult(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                !Boolean.FALSE.equals(user.getActive()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDeactivatedAt());
    }

    public record UserResult(
            UUID id,
            String email,
            String role,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record UserPageResult(
            List<UserResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
