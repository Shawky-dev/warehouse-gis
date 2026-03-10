import { PATHS } from "@/shared/consts/paths";
import TenantDashboardPage from "@/features/tenant/dashboard/TenantDashboardPage";
import TenantProductsPage from "@/features/tenant/products/TenantProductsPage";
import TenantUsersPage from "@/features/tenant/user/TenantUsersPage";
import TenantRolesPage from "@/features/tenant/roles/TenantRolesPage";
import TenantUomsPage from "@/features/tenant/uom/TenantUomsPage";
import TenantSuppliersPage from "@/features/tenant/supplier/TenantSuppliersPage";
import TenantAuditLogsPage from "@/features/tenant/audit/TenantAuditLogsPage";
import { RequirePermission } from "@/features/auth/guards/RequirePermission";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

export const tenantRoutes = [
  {
    path: PATHS.TENANT.ROOT_PATTERN,
    element: <TenantDashboardPage />,
  },
  {
    path: PATHS.TENANT.PRODUCTS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.PRODUCTS_VIEW}>
        <TenantProductsPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.USERS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.USERS_VIEW}>
        <TenantUsersPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.ROLES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.ROLES_EDIT}>
        <TenantRolesPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.UOMS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.UOMS_VIEW}>
        <TenantUomsPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.SUPPLIERS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.SUPPLIERS_VIEW}>
        <TenantSuppliersPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.AUDIT_LOGS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.AUDIT_VIEW}>
        <TenantAuditLogsPage />
      </RequirePermission>
    ),
  },
];
