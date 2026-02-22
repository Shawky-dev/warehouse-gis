package com.warehouse.warehouse_platform.landlord.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LandlordUserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public LandlordUserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
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

    @Transactional
    public UserResult createUser(String email, String password, String role) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        validateRoleIsAssignable(normalizedRole);

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw LandlordUserManagementException.conflict("Email already exists: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(normalizedRole)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        return toResult(savedUser);
    }

    @Transactional
    public UserResult updateUser(UUID userId, String email, String role) {
        User user = loadUserOrThrow(userId);

        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        validateRoleIsAssignable(normalizedRole);

        userRepository.findByEmail(normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw LandlordUserManagementException.conflict("Email already exists: " + normalizedEmail);
                });

        user.setEmail(normalizedEmail);
        user.setRole(normalizedRole);

        User savedUser = userRepository.save(user);
        return toResult(savedUser);
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User user = loadUserOrThrow(userId);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllActiveForUser(userId, "PASSWORD_RESET");
    }

    @Transactional
    public void deactivateUser(UUID userId, String actorEmail) {
        User user = loadUserOrThrow(userId);

        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw LandlordUserManagementException.forbidden("You cannot deactivate your own account");
        }

        if (!Boolean.FALSE.equals(user.getActive())) {
            user.setActive(false);
            user.setDeactivatedAt(Instant.now());
            userRepository.save(user);
        }

        refreshTokenService.revokeAllActiveForUser(userId, "DEACTIVATED");
    }

    private User loadUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> LandlordUserManagementException.notFound("User not found: " + userId));
    }

    private void validateRoleIsAssignable(String role) {
        if (!roleRepository.existsById(role)) {
            throw LandlordUserManagementException.badRequest("Role not found: " + role);
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw LandlordUserManagementException.badRequest("role must not be blank");
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw LandlordUserManagementException.badRequest("email must not be blank");
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
