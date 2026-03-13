import { PATHS } from "@/shared/consts/paths";
import TenantDashboardPage from "@/features/tenant/dashboard/TenantDashboardPage";
import TenantProductsPage from "@/features/tenant/products/TenantProductsPage";
import TenantUsersPage from "@/features/tenant/user/TenantUsersPage";
import TenantRolesPage from "@/features/tenant/roles/TenantRolesPage";
import TenantUomsPage from "@/features/tenant/uom/TenantUomsPage";
import TenantSuppliersPage from "@/features/tenant/supplier/TenantSuppliersPage";
import TenantAuditLogsPage from "@/features/tenant/audit/TenantAuditLogsPage";
import TenantCategoriesPage from "@/features/tenant/category/TenantCategoriesPage";
import WarehouseLayoutsPage from "@/features/tenant/warehouse/WarehouseLayoutsPage";
import InventoryPage from "@/features/tenant/inventory/InventoryPage";
import { RequirePermission } from "@/features/auth/guards/RequirePermission";
import { RequireAnyPermission } from "@/features/auth/guards/RequireAnyPermission";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const INVENTORY_ROUTE_PERMISSIONS = [
  TENANT_PERMISSIONS.INVENTORY_VIEW,
  TENANT_PERMISSIONS.INVENTORY_RECEIVE,
  TENANT_PERMISSIONS.INVENTORY_TRANSFER,
  TENANT_PERMISSIONS.INVENTORY_ADJUST,
];

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
    path: PATHS.TENANT.CATEGORIES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.CATEGORIES_VIEW}>
        <TenantCategoriesPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.WAREHOUSE_LAYOUTS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.WAREHOUSE_VIEW}>
        <WarehouseLayoutsPage />
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
  {
    path: PATHS.TENANT.INVENTORY_PATTERN,
    element: (
      <RequireAnyPermission permissions={INVENTORY_ROUTE_PERMISSIONS}>
        <InventoryPage />
      </RequireAnyPermission>
    ),
  },
];
