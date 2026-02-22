package com.warehouse.warehouse_platform.security;

import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorityResolverTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RoleAuthorityResolver roleAuthorityResolver;

    @Test
    void resolveAuthorities_shouldReturnRoleAndPermissionAuthorities() {
        when(rolePermissionRepository.findPermissionCodesByRoleCode("ADMIN"))
                .thenReturn(List.of("landlord.users.view", "landlord.users.create"));

        List<GrantedAuthority> authorities = roleAuthorityResolver.resolveAuthorities("admin");

        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("landlord.users.view")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("landlord.users.create")));
    }

    @Test
    void splitAuthorities_shouldSeparateRolesAndPermissions() {
        RoleAuthorityResolver.AuthoritySnapshot snapshot = roleAuthorityResolver.splitAuthorities(List.of(
                new SimpleGrantedAuthority("landlord.users.create"),
                new SimpleGrantedAuthority("ROLE_MANAGER"),
                new SimpleGrantedAuthority("landlord.users.view"),
                new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertEquals(List.of("ROLE_ADMIN", "ROLE_MANAGER"), snapshot.roles());
        assertEquals(List.of("landlord.users.create", "landlord.users.view"), snapshot.permissions());
    }
}
