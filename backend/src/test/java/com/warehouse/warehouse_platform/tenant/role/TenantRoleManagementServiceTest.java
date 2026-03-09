package com.warehouse.warehouse_platform.tenant.role;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.user.rbac.Permission;
import com.warehouse.warehouse_platform.user.rbac.PermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRoleManagementServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantRoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantRoleManagementService(
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                tenantAuditService);
    }

    @Test
    void createRole_shouldAuditWriteAction() {
        when(roleRepository.existsById("AUDITOR")).thenReturn(false);
        when(permissionRepository.findAllById(Set.of("tenant.users.view")))
                .thenReturn(List.of(Permission.builder().code("tenant.users.view").description("view users").build()));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePermissionRepository.findPermissionCodesByRoleCode("AUDITOR")).thenReturn(List.of("tenant.users.view"));

        TenantRoleManagementService.RoleDetails result = service.createRole(
                "auditor",
                "Auditor",
                "Audit role",
                Set.of("tenant.users.view"),
                false);

        assertEquals("AUDITOR", result.code());
        verify(tenantAuditService).record(eq("ROLE_CREATE"), eq("ROLE"), eq("AUDITOR"), eq(null), any());
    }

    @Test
    void updateRole_shouldAuditBeforeAndAfter() {
        Role manager = Role.builder()
                .code("MANAGER")
                .name("Manager")
                .description("desc")
                .locked(false)
                .build();

        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(manager));
        when(permissionRepository.findAllById(Set.of("tenant.users.view")))
                .thenReturn(List.of(Permission.builder().code("tenant.users.view").description("view users").build()));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePermissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePermissionRepository.findPermissionCodesByRoleCode("MANAGER")).thenReturn(List.of("tenant.users.view"));

        TenantRoleManagementService.RoleDetails result = service.updateRole(
                "manager",
                "Manager Updated",
                "updated",
                Set.of("tenant.users.view"),
                false,
                true);

        assertEquals("MANAGER", result.code());
        verify(tenantAuditService).record(eq("ROLE_UPDATE"), eq("ROLE"), eq("MANAGER"), any(), any());
    }
}
