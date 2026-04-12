import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { PATHS } from "@/shared/consts/paths";
import type { DashboardPermissionMatrixEntry } from "@/features/tenant/dashboard/types";

export const DASHBOARD_PERMISSION_MATRIX: readonly DashboardPermissionMatrixEntry[] = [
  {
    id: "overview",
    endpoint: "spatial-kpis",
    titleKey: "tenant.dashboard.sections.overview.title",
    descriptionKey: "tenant.dashboard.sections.overview.description",
    permissions: [
      TENANT_PERMISSIONS.WAREHOUSE_VIEW,
      TENANT_PERMISSIONS.INVENTORY_VIEW,
      TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW,
      TENANT_PERMISSIONS.GIS_ZONES_VIEW,
      TENANT_PERMISSIONS.GIS_HAZARD_BUFFERS_VIEW,
      TENANT_PERMISSIONS.GIS_DATA_LAYERS_VIEW,
    ],
    workspaces: [
      {
        permissions: [TENANT_PERMISSIONS.WAREHOUSE_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.warehouseLayouts(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.INVENTORY_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.inventory(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.gisFloorPlans(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.GIS_ZONES_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.gisZones(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.GIS_HAZARD_BUFFERS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.gisHazardBuffers(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.GIS_DATA_LAYERS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.gisDataLayers(tenantSlug),
      },
    ],
  },
  {
    id: "inventoryOps",
    endpoint: "inventory-ops",
    titleKey: "tenant.dashboard.sections.inventoryOps.title",
    descriptionKey: "tenant.dashboard.sections.inventoryOps.description",
    permissions: [
      TENANT_PERMISSIONS.INVENTORY_VIEW,
      TENANT_PERMISSIONS.INVENTORY_RECEIVE,
      TENANT_PERMISSIONS.INVENTORY_TRANSFER,
      TENANT_PERMISSIONS.INVENTORY_ADJUST,
      TENANT_PERMISSIONS.RECEIPTS_VIEW,
      TENANT_PERMISSIONS.DISPATCHES_VIEW,
    ],
    workspaces: [
      {
        permissions: [
          TENANT_PERMISSIONS.INVENTORY_VIEW,
          TENANT_PERMISSIONS.INVENTORY_RECEIVE,
          TENANT_PERMISSIONS.INVENTORY_TRANSFER,
          TENANT_PERMISSIONS.INVENTORY_ADJUST,
        ],
        path: (tenantSlug: string) => PATHS.TENANT.inventory(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.RECEIPTS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.receipts(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.DISPATCHES_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.dispatches(tenantSlug),
      },
    ],
  },
  {
    id: "masterData",
    endpoint: "master-data",
    titleKey: "tenant.dashboard.sections.masterData.title",
    descriptionKey: "tenant.dashboard.sections.masterData.description",
    permissions: [
      TENANT_PERMISSIONS.PRODUCTS_VIEW,
      TENANT_PERMISSIONS.CATEGORIES_VIEW,
      TENANT_PERMISSIONS.SUPPLIERS_VIEW,
      TENANT_PERMISSIONS.UOMS_VIEW,
    ],
    workspaces: [
      {
        permissions: [TENANT_PERMISSIONS.PRODUCTS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.products(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.CATEGORIES_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.categories(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.SUPPLIERS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.suppliers(tenantSlug),
      },
      {
        permissions: [TENANT_PERMISSIONS.UOMS_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.uoms(tenantSlug),
      },
    ],
  },
  {
    id: "activity",
    endpoint: "activity",
    titleKey: "tenant.dashboard.sections.activity.title",
    descriptionKey: "tenant.dashboard.sections.activity.description",
    permissions: [TENANT_PERMISSIONS.AUDIT_VIEW],
    workspaces: [
      {
        permissions: [TENANT_PERMISSIONS.AUDIT_VIEW],
        path: (tenantSlug: string) => PATHS.TENANT.auditLogs(tenantSlug),
      },
    ],
  },
] as const;
