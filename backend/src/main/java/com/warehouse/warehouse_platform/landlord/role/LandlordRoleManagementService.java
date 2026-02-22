package com.warehouse.warehouse_platform.landlord.role;

import com.warehouse.warehouse_platform.user.rbac.Permission;
import com.warehouse.warehouse_platform.user.rbac.PermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
import com.warehouse.warehouse_platform.user.rbac.RolePermission;
import com.warehouse.warehouse_platform.user.rbac.RolePermissionId;
import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class LandlordRoleManagementService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,50}$");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public LandlordRoleManagementService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleDetails> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(role -> new RoleDetails(
                        role.getCode(),
                        role.getName(),
                        role.getDescription(),
                        rolePermissionRepository.findPermissionCodesByRoleCode(role.getCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDetails getRole(String roleCode) {
        Role role = loadRole(roleCode);
        return toDetails(role);
    }

    @Transactional(readOnly = true)
    public List<PermissionOption> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(permission -> new PermissionOption(permission.getCode(), permission.getDescription()))
                .toList();
    }

    @Transactional
    public RoleDetails createRole(
            String roleCode,
            String name,
            String description,
            Set<String> permissionCodes) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        if (roleRepository.existsById(normalizedRoleCode)) {
            throw LandlordRoleManagementException.conflict("Role already exists: " + normalizedRoleCode);
        }

        Set<String> normalizedPermissionCodes = normalizePermissionCodes(permissionCodes);
        Map<String, Permission> permissionsByCode = resolvePermissionsByCode(normalizedPermissionCodes);

        Role role = Role.builder()
                .code(normalizedRoleCode)
                .name(normalizeName(name))
                .description(normalizeDescription(description))
                .build();
        roleRepository.save(role);

        replaceRolePermissions(role, normalizedPermissionCodes, permissionsByCode);
        return toDetails(role);
    }

    @Transactional
    public RoleDetails updateRole(
            String roleCode,
            String name,
            String description,
            Set<String> permissionCodes) {
        Role role = loadRole(roleCode);
        Set<String> normalizedPermissionCodes = normalizePermissionCodes(permissionCodes);
        Map<String, Permission> permissionsByCode = resolvePermissionsByCode(normalizedPermissionCodes);

        role.setName(normalizeName(name));
        role.setDescription(normalizeDescription(description));
        roleRepository.save(role);

        replaceRolePermissions(role, normalizedPermissionCodes, permissionsByCode);

        return toDetails(role);
    }

    private RoleDetails toDetails(Role role) {
        return new RoleDetails(
                role.getCode(),
                role.getName(),
                role.getDescription(),
                rolePermissionRepository.findPermissionCodesByRoleCode(role.getCode()));
    }

    private Role loadRole(String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return roleRepository.findById(normalizedRoleCode)
                .orElseThrow(() -> LandlordRoleManagementException.notFound("Role not found: " + normalizedRoleCode));
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw LandlordRoleManagementException.badRequest("roleCode must not be blank");
        }
        String normalizedRoleCode = roleCode.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(normalizedRoleCode).matches()) {
            throw LandlordRoleManagementException.badRequest(
                    "roleCode must contain only letters, numbers, or underscore (2-50 chars)");
        }
        return normalizedRoleCode;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw LandlordRoleManagementException.badRequest("name must not be blank");
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Set<String> normalizePermissionCodes(Set<String> permissionCodes) {
        if (permissionCodes == null) {
            throw LandlordRoleManagementException.badRequest("permissionCodes must not be null");
        }

        Set<String> normalized = permissionCodes.stream()
                .map(code -> code == null ? "" : code.trim())
                .map(code -> code.toLowerCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (normalized.size() != permissionCodes.size()) {
            throw LandlordRoleManagementException.badRequest("permissionCodes must not contain blank values");
        }

        return normalized;
    }

    private Map<String, Permission> resolvePermissionsByCode(Set<String> normalizedPermissionCodes) {
        if (normalizedPermissionCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, Permission> permissionsByCode = permissionRepository.findAllById(normalizedPermissionCodes).stream()
                .collect(java.util.stream.Collectors.toMap(Permission::getCode, Function.identity()));

        if (permissionsByCode.size() != normalizedPermissionCodes.size()) {
            Set<String> missing = new java.util.TreeSet<>(normalizedPermissionCodes);
            missing.removeAll(permissionsByCode.keySet());
            throw LandlordRoleManagementException.badRequest("Unknown permission codes: " + String.join(", ", missing));
        }

        return permissionsByCode;
    }

    private void replaceRolePermissions(
            Role role,
            Set<String> normalizedPermissionCodes,
            Map<String, Permission> permissionsByCode) {
        rolePermissionRepository.deleteByRole_Code(role.getCode());

        if (normalizedPermissionCodes.isEmpty()) {
            return;
        }

        List<RolePermission> rolePermissions = new ArrayList<>();
        for (String permissionCode : normalizedPermissionCodes) {
            Permission permission = permissionsByCode.get(permissionCode);
            rolePermissions.add(RolePermission.builder()
                    .id(new RolePermissionId(role.getCode(), permissionCode))
                    .role(role)
                    .permission(permission)
                    .build());
        }
        rolePermissionRepository.saveAll(rolePermissions);
    }

    public record RoleDetails(
            String code,
            String name,
            String description,
            List<String> permissionCodes) {
    }

    public record PermissionOption(
            String code,
            String description) {
    }
}
