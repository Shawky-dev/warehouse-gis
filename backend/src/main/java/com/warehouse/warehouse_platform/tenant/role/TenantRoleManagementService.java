package com.warehouse.warehouse_platform.tenant.role;

import com.warehouse.warehouse_platform.rbac.role.RoleManagementCoreException;
import com.warehouse.warehouse_platform.rbac.role.RoleManagementCoreService;
import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.user.rbac.PermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TenantRoleManagementService {

    private final RoleManagementCoreService roleManagementCoreService;
    private final TenantAuditService tenantAuditService;

    public TenantRoleManagementService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            TenantAuditService tenantAuditService) {
        this.roleManagementCoreService = new RoleManagementCoreService(
                roleRepository,
                permissionRepository,
                rolePermissionRepository);
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public List<RoleDetails> listRoles() {
        try {
            return roleManagementCoreService.listRoles().stream()
                    .map(this::toDetails)
                    .toList();
        } catch (RoleManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional(readOnly = true)
    public RoleDetails getRole(String roleCode) {
        try {
            return toDetails(roleManagementCoreService.getRole(roleCode));
        } catch (RoleManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<PermissionOption> listPermissions() {
        try {
            return roleManagementCoreService.listPermissions().stream()
                    .map(this::toOption)
                    .toList();
        } catch (RoleManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public RoleDetails createRole(
            String roleCode,
            String name,
            String description,
            Set<String> permissionCodes,
            Boolean locked) {
        try {
            RoleDetails created = toDetails(roleManagementCoreService.createRole(
                    roleCode,
                    name,
                    description,
                    permissionCodes,
                    locked));
            tenantAuditService.record("ROLE_CREATE", "ROLE", created.code(), null, created);
            return created;
        } catch (RoleManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    @Transactional
    public RoleDetails updateRole(
            String roleCode,
            String name,
            String description,
            Set<String> permissionCodes,
            Boolean locked,
            boolean actorIsAdmin) {
        try {
            RoleDetails before = toDetails(roleManagementCoreService.getRole(roleCode));
            RoleDetails after = toDetails(roleManagementCoreService.updateRole(
                    roleCode,
                    name,
                    description,
                    permissionCodes,
                    locked,
                    actorIsAdmin));
            tenantAuditService.record("ROLE_UPDATE", "ROLE", after.code(), before, after);
            return after;
        } catch (RoleManagementCoreException exception) {
            throw toTenantException(exception);
        }
    }

    private RoleDetails toDetails(RoleManagementCoreService.RoleDetails details) {
        return new RoleDetails(
                details.code(),
                details.name(),
                details.description(),
                details.permissionCodes(),
                details.locked());
    }

    private PermissionOption toOption(RoleManagementCoreService.PermissionOption option) {
        return new PermissionOption(option.code(), option.description());
    }

    private TenantRoleManagementException toTenantException(RoleManagementCoreException exception) {
        return switch (exception.getCode()) {
            case "NOT_FOUND" -> TenantRoleManagementException.notFound(exception.getMessage());
            case "CONFLICT" -> TenantRoleManagementException.conflict(exception.getMessage());
            case "FORBIDDEN" -> TenantRoleManagementException.forbidden(exception.getMessage());
            default -> TenantRoleManagementException.badRequest(exception.getMessage());
        };
    }

    public record RoleDetails(
            String code,
            String name,
            String description,
            List<String> permissionCodes,
            boolean locked) {
    }

    public record PermissionOption(
            String code,
            String description) {
    }
}
