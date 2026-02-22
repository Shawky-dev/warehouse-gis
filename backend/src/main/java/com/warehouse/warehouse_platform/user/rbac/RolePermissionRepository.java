package com.warehouse.warehouse_platform.user.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    @Query("select rp.permission.code from RolePermission rp where rp.role.code = :roleCode order by rp.permission.code")
    List<String> findPermissionCodesByRoleCode(@Param("roleCode") String roleCode);

    void deleteByRole_Code(String roleCode);
}
