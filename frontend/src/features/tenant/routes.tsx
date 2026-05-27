import { PATHS } from "@/shared/consts/paths";
import TenantDashboardPage from "@/features/tenant/dashboard/TenantDashboardPage";
import TenantProductsPage from "@/features/tenant/products/TenantProductsPage";
import TenantUsersPage from "@/features/tenant/user/TenantUsersPage";
import TenantRolesPage from "@/features/tenant/roles/TenantRolesPage";
import TenantUomsPage from "@/features/tenant/uom/TenantUomsPage";
import TenantSuppliersPage from "@/features/tenant/supplier/TenantSuppliersPage";
import TenantAuditLogsPage from "@/features/tenant/audit/TenantAuditLogsPage";
import TenantCategoriesPage from "@/features/tenant/category/TenantCategoriesPage";
import TenantHazardTypesPage from "@/features/tenant/hazardtype/TenantHazardTypesPage";
import TenantZoneTypesPage from "@/features/tenant/zonetype/TenantZoneTypesPage";
import WarehouseLayoutsPage from "@/features/tenant/warehouse/WarehouseLayoutsPage";
import InventoryPage from "@/features/tenant/inventory/InventoryPage";
import ReceiptsPage from "@/features/tenant/receipts/ReceiptsPage";
import DispatchesPage from "@/features/tenant/dispatches/DispatchesPage";
import CountSessionsPage from "@/features/tenant/counting/CountSessionsPage";
import FloorPlansPage from "@/features/gis/floorplans/FloorPlansPage";
import WarehouseMapPage from "@/features/gis/viewer/WarehouseMapPage";
import ZoneManagementPage from "@/features/gis/zones/ZoneManagementPage";
import HazardBufferManagementPage from "@/features/gis/hazardBuffers/HazardBufferManagementPage";
import DataLayersPage from "@/features/gis/dataLayers/DataLayersPage";
import IfcViewerPage from "@/features/ifc/IfcViewerPage";
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
    path: PATHS.TENANT.WAREHOUSE_TEMPLATES_PATTERN,
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
        <InventoryPage section="stock" />
      </RequireAnyPermission>
    ),
  },
  {
    path: PATHS.TENANT.INVENTORY_STOCK_PATTERN,
    element: (
      <RequireAnyPermission permissions={INVENTORY_ROUTE_PERMISSIONS}>
        <InventoryPage section="stock" />
      </RequireAnyPermission>
    ),
  },
  {
    path: PATHS.TENANT.INVENTORY_OPERATIONS_PATTERN,
    element: (
      <RequireAnyPermission permissions={INVENTORY_ROUTE_PERMISSIONS}>
        <InventoryPage section="operations" />
      </RequireAnyPermission>
    ),
  },
  {
    path: PATHS.TENANT.INVENTORY_MOVEMENTS_PATTERN,
    element: (
      <RequireAnyPermission permissions={INVENTORY_ROUTE_PERMISSIONS}>
        <InventoryPage section="movements" />
      </RequireAnyPermission>
    ),
  },
  {
    path: PATHS.TENANT.RECEIPTS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.RECEIPTS_VIEW}>
        <ReceiptsPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.DISPATCHES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.DISPATCHES_VIEW}>
        <DispatchesPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.COUNT_SESSIONS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.COUNTING_VIEW}>
        <CountSessionsPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.GIS_FLOOR_PLANS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW}>
        <FloorPlansPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.GIS_MAP_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW}>
        <WarehouseMapPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.GIS_ZONES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.GIS_ZONES_VIEW}>
        <ZoneManagementPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.HAZARD_TYPES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.HAZARD_TYPES_VIEW}>
        <TenantHazardTypesPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.ZONE_TYPES_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.ZONE_TYPES_VIEW}>
        <TenantZoneTypesPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.GIS_HAZARD_BUFFERS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.GIS_HAZARD_BUFFERS_VIEW}>
        <HazardBufferManagementPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.GIS_DATA_LAYERS_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.GIS_DATA_LAYERS_VIEW}>
        <DataLayersPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.TENANT.IFC_VIEWER_PATTERN,
    element: (
      <RequirePermission permission={TENANT_PERMISSIONS.IFC_VIEW}>
        <IfcViewerPage />
      </RequirePermission>
    ),
  },
];
