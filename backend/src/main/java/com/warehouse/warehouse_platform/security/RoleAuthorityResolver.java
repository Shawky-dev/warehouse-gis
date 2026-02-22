package com.warehouse.warehouse_platform.security;

import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class RoleAuthorityResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    private final RolePermissionRepository rolePermissionRepository;

    public RoleAuthorityResolver(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<GrantedAuthority> resolveAuthorities(String roleCode) {
        return resolveAuthorityCodes(roleCode).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public List<String> resolveAuthorityCodes(String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);

        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        authorities.add(ROLE_PREFIX + normalizedRoleCode);
        authorities.addAll(rolePermissionRepository.findPermissionCodesByRoleCode(normalizedRoleCode));

        return List.copyOf(authorities);
    }

    public AuthoritySnapshot splitAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return new AuthoritySnapshot(List.of(), List.of());
        }

        List<String> roleAuthorities = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(this::isRoleAuthority)
                .distinct()
                .sorted()
                .toList();

        List<String> permissionAuthorities = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !isRoleAuthority(authority))
                .distinct()
                .sorted()
                .toList();

        return new AuthoritySnapshot(roleAuthorities, permissionAuthorities);
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode must not be blank");
        }
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isRoleAuthority(String authority) {
        return authority != null && authority.startsWith(ROLE_PREFIX);
    }

    public record AuthoritySnapshot(
            List<String> roles,
            List<String> permissions) {
    }
}
