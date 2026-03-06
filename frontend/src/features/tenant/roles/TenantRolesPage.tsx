import { useMemo } from "react";
import { useParams } from "react-router-dom";
import RbacRolesPage from "@/features/rbac/components/RbacRolesPage";
import type { RbacRolesPageAdapter } from "@/features/rbac/types";
import {
  createTenantRole,
  extractTenantRbacErrorMessage,
  getTenantPermissions,
  getTenantRoles,
  updateTenantRole,
} from "@/features/tenant/api/rbacApi";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";

const TenantRolesPage = () => {
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  const adapter = useMemo<RbacRolesPageAdapter>(
    () => ({
      listRoles: () => getTenantRoles(normalizedSlug),
      listPermissions: () => getTenantPermissions(normalizedSlug),
      createRole: (payload) => createTenantRole(normalizedSlug, payload),
      updateRole: (roleCode, payload) => updateTenantRole(normalizedSlug, roleCode, payload),
      extractErrorMessage: extractTenantRbacErrorMessage,
    }),
    [normalizedSlug]
  );

  return <RbacRolesPage adapter={adapter} />;
};

export default TenantRolesPage;
