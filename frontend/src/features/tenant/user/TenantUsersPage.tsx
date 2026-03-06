import { useMemo } from "react";
import { useParams } from "react-router-dom";
import RbacUsersPage from "@/features/rbac/components/RbacUsersPage";
import type { RbacUsersPageAdapter } from "@/features/rbac/types";
import {
  createTenantUser,
  deactivateTenantUser,
  extractTenantRbacErrorMessage,
  getTenantRoles,
  getTenantUsers,
  reactivateTenantUser,
  resetTenantUserPassword,
  updateTenantUser,
} from "@/features/tenant/api/rbacApi";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";

const TenantUsersPage = () => {
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  const adapter = useMemo<RbacUsersPageAdapter>(
    () => ({
      listUsers: (params) => getTenantUsers(normalizedSlug, params),
      createUser: (payload) => createTenantUser(normalizedSlug, payload),
      updateUser: (userId, payload) => updateTenantUser(normalizedSlug, userId, payload),
      resetUserPassword: (userId, payload) => resetTenantUserPassword(normalizedSlug, userId, payload),
      deactivateUser: (userId) => deactivateTenantUser(normalizedSlug, userId),
      reactivateUser: (userId) => reactivateTenantUser(normalizedSlug, userId),
      listRoles: () => getTenantRoles(normalizedSlug),
      extractErrorMessage: extractTenantRbacErrorMessage,
    }),
    [normalizedSlug]
  );

  return <RbacUsersPage adapter={adapter} permissions={TENANT_PERMISSIONS} />;
};

export default TenantUsersPage;
