package com.warehouse.warehouse_platform.landlord.role;

import com.warehouse.warehouse_platform.user.rbac.Permission;
import com.warehouse.warehouse_platform.user.rbac.PermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.Role;
import com.warehouse.warehouse_platform.user.rbac.RolePermission;
import com.warehouse.warehouse_platform.user.rbac.RolePermissionRepository;
import com.warehouse.warehouse_platform.user.rbac.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandlordRoleManagementServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private LandlordRoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new LandlordRoleManagementService(roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void createRole_shouldPersistRoleAndPermissions() {
        Permission usersView = Permission.builder().code("landlord.users.view").build();
        Permission usersCreate = Permission.builder().code("landlord.users.create").build();

        when(roleRepository.existsById("AUDITOR")).thenReturn(false);
        when(permissionRepository.findAllById(Set.of("landlord.users.view", "landlord.users.create")))
                .thenReturn(List.of(usersView, usersCreate));
        when(rolePermissionRepository.findPermissionCodesByRoleCode("AUDITOR"))
                .thenReturn(List.of("landlord.users.create", "landlord.users.view"));

        LandlordRoleManagementService.RoleDetails result = service.createRole(
                "auditor",
                "Auditor",
                "Read only reporting role",
                Set.of("landlord.users.view", "landlord.users.create"));

        assertEquals("AUDITOR", result.code());
        assertEquals("Auditor", result.name());
        assertEquals("Read only reporting role", result.description());
        assertEquals(List.of("landlord.users.create", "landlord.users.view"), result.permissionCodes());

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertEquals("AUDITOR", roleCaptor.getValue().getCode());

        verify(rolePermissionRepository).saveAll(any(List.class));
    }

    @Test
    void createRole_shouldThrowConflictWhenRoleAlreadyExists() {
        when(roleRepository.existsById("ADMIN")).thenReturn(true);

        LandlordRoleManagementException exception = assertThrows(
                LandlordRoleManagementException.class,
                () -> service.createRole(
                        "ADMIN",
                        "Admin",
                        "System role",
                        Set.of("landlord.users.view")));

        assertEquals("CONFLICT", exception.getCode());
    }

    @Test
    void updateRole_shouldReplaceRoleMetadataAndPermissions() {
        Role managerRole = Role.builder()
                .code("MANAGER")
                .name("Manager")
                .description("Legacy description")
                .build();

        Permission usersView = Permission.builder().code("landlord.users.view").build();
        Permission usersCreate = Permission.builder().code("landlord.users.create").build();

        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(managerRole));
        when(permissionRepository.findAllById(Set.of("landlord.users.view", "landlord.users.create")))
                .thenReturn(List.of(usersView, usersCreate));
        when(rolePermissionRepository.findPermissionCodesByRoleCode("MANAGER"))
                .thenReturn(List.of("landlord.users.create", "landlord.users.view"));

        LandlordRoleManagementService.RoleDetails result = service.updateRole(
                "manager",
                "Operations Manager",
                "Manages user operations",
                Set.of("landlord.users.view", "landlord.users.create"));

        assertEquals("MANAGER", result.code());
        assertEquals("Operations Manager", result.name());
        assertEquals("Manages user operations", result.description());
        assertEquals(List.of("landlord.users.create", "landlord.users.view"), result.permissionCodes());

        verify(roleRepository).save(managerRole);
        verify(rolePermissionRepository).deleteByRole_Code("MANAGER");

        ArgumentCaptor<List<RolePermission>> rolePermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(rolePermissionsCaptor.capture());
        assertEquals(2, rolePermissionsCaptor.getValue().size());
    }

    @Test
    void updateRole_shouldThrowOnUnknownPermissionCodes() {
        Role managerRole = Role.builder()
                .code("MANAGER")
                .name("Manager")
                .build();

        when(roleRepository.findById("MANAGER")).thenReturn(Optional.of(managerRole));
        when(permissionRepository.findAllById(Set.of("landlord.users.view", "landlord.users.unknown")))
                .thenReturn(List.of(Permission.builder().code("landlord.users.view").build()));

        LandlordRoleManagementException exception = assertThrows(
                LandlordRoleManagementException.class,
                () -> service.updateRole(
                        "MANAGER",
                        "Manager",
                        "",
                        Set.of("landlord.users.view", "landlord.users.unknown")));

        assertEquals("BAD_REQUEST", exception.getCode());
    }

    @Test
    void getRole_shouldThrowWhenRoleMissing() {
        when(roleRepository.findById("MISSING")).thenReturn(Optional.empty());

        LandlordRoleManagementException exception = assertThrows(
                LandlordRoleManagementException.class,
                () -> service.getRole("missing"));

        assertEquals("NOT_FOUND", exception.getCode());
    }
}
