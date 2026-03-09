package com.warehouse.warehouse_platform.tenant.user;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.rbac.user.UserManagementCoreException;
import com.warehouse.warehouse_platform.rbac.user.UserManagementCoreService;
import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.user.UserRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TenantUserManagementService {

    private final UserManagementCoreService userManagementCoreService;
    private final TenantAuditService tenantAuditService;

    public TenantUserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            TenantAuditService tenantAuditService) {
        this.userManagementCoreService = new UserManagementCoreService(
                userRepository,
                roleRepository,
                passwordEncoder,
                refreshTokenService);
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public UserPageResult listUsers(int page, int size, String search, Boolean active) {
        try {
            return toResult(userManagementCoreService.listUsers(page, size, search, active));
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public UserResult createUser(String email, String password, String role, boolean actorIsAdmin) {
        try {
            UserResult created = toResult(userManagementCoreService.createUser(email, password, role, actorIsAdmin));
            tenantAuditService.record("USER_CREATE", "USER", created.id().toString(), null, created);
            return created;
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public UserResult updateUser(UUID userId, String email, String role, boolean actorIsAdmin) {
        try {
            UserResult before = toResult(userManagementCoreService.getUser(userId));
            UserResult after = toResult(userManagementCoreService.updateUser(userId, email, role, actorIsAdmin));
            tenantAuditService.record("USER_UPDATE", "USER", userId.toString(), before, after);
            return after;
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        try {
            UserResult before = toResult(userManagementCoreService.getUser(userId));
            userManagementCoreService.resetPassword(userId, newPassword);
            UserResult after = toResult(userManagementCoreService.getUser(userId));
            tenantAuditService.record("USER_RESET_PASSWORD", "USER", userId.toString(), before, after);
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public void deactivateUser(UUID userId, String actorEmail, boolean actorIsAdmin) {
        try {
            UserResult before = toResult(userManagementCoreService.getUser(userId));
            userManagementCoreService.deactivateUser(userId, actorEmail, actorIsAdmin);
            UserResult after = toResult(userManagementCoreService.getUser(userId));
            tenantAuditService.record("USER_DEACTIVATE", "USER", userId.toString(), before, after);
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public void reactivateUser(UUID userId) {
        try {
            UserResult before = toResult(userManagementCoreService.getUser(userId));
            userManagementCoreService.reactivateUser(userId);
            UserResult after = toResult(userManagementCoreService.getUser(userId));
            tenantAuditService.record("USER_REACTIVATE", "USER", userId.toString(), before, after);
        } catch (UserManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    private UserResult toResult(UserManagementCoreService.UserResult result) {
        return new UserResult(
                result.id(),
                result.email(),
                result.role(),
                result.active(),
                result.createdAt(),
                result.updatedAt(),
                result.deactivatedAt());
    }

    private UserPageResult toResult(UserManagementCoreService.UserPageResult result) {
        return new UserPageResult(
                result.content().stream().map(this::toResult).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private TenantUserManagementException toTenantException(UserManagementCoreException exception) {
        return switch (exception.getCode()) {
            case "NOT_FOUND" -> TenantUserManagementException.notFound(exception.getMessage());
            case "CONFLICT" -> TenantUserManagementException.conflict(exception.getMessage());
            case "FORBIDDEN" -> TenantUserManagementException.forbidden(exception.getMessage());
            default -> TenantUserManagementException.badRequest(exception.getMessage());
        };
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
