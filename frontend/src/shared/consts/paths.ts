function buildUrl(base: string, filters?: Record<string, string | undefined>): string {
  if (!filters) return base;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined) params.set(key, value);
  }
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

export const PATHS = {
  ROOT: "/",

  LANDLORD: {
    AUTH_LOGIN: "/landlord/auth/login",
    ROOT: "/landlord",
    DASHBOARD: "/landlord",
    WAREHOUSES: "/landlord/warehouses",
    WAREHOUSES_CREATE: "/landlord/warehouses/create",
    WAREHOUSES_LIST: "/landlord/warehouses/list",
    USERS: "/landlord/users",
    ROLES: "/landlord/roles",
  },

  TENANT: {
    ROOT_PATTERN: "/:tenantSlug",
    AUTH_LOGIN_PATTERN: "/:tenantSlug/auth/login",
    PRODUCTS_PATTERN: "/:tenantSlug/products",
    USERS_PATTERN: "/:tenantSlug/users",
    ROLES_PATTERN: "/:tenantSlug/roles",
    UOMS_PATTERN: "/:tenantSlug/uoms",
    SUPPLIERS_PATTERN: "/:tenantSlug/suppliers",
    CATEGORIES_PATTERN: "/:tenantSlug/categories",
    WAREHOUSE_LAYOUTS_PATTERN: "/:tenantSlug/warehouse-layouts",
    WAREHOUSE_TEMPLATES_PATTERN: "/:tenantSlug/warehouse-layouts/templates",
    AUDIT_LOGS_PATTERN: "/:tenantSlug/audit-logs",
    INVENTORY_PATTERN: "/:tenantSlug/inventory",
    INVENTORY_STOCK_PATTERN: "/:tenantSlug/inventory/stock",
    INVENTORY_OPERATIONS_PATTERN: "/:tenantSlug/inventory/operations",
    INVENTORY_MOVEMENTS_PATTERN: "/:tenantSlug/inventory/movements",
    RECEIPTS_PATTERN: "/:tenantSlug/receipts",
    DISPATCHES_PATTERN: "/:tenantSlug/dispatches",
    COUNT_SESSIONS_PATTERN: "/:tenantSlug/count-sessions",
    GIS_FLOOR_PLANS_PATTERN: "/:tenantSlug/gis/floor-plans",
    GIS_MAP_PATTERN: "/:tenantSlug/gis/map",
    GIS_ZONES_PATTERN: "/:tenantSlug/gis/zones",
    HAZARD_TYPES_PATTERN: "/:tenantSlug/hazard-types",
    ZONE_TYPES_PATTERN: "/:tenantSlug/zone-types",
    GIS_HAZARD_BUFFERS_PATTERN: "/:tenantSlug/gis/hazard-buffers",
    GIS_DATA_LAYERS_PATTERN: "/:tenantSlug/gis/data-layers",
    root: (tenantSlug: string) => `/${tenantSlug}`,
    authLogin: (tenantSlug: string) => `/${tenantSlug}/auth/login`,
    products: (tenantSlug: string) => `/${tenantSlug}/products`,
    users: (tenantSlug: string) => `/${tenantSlug}/users`,
    roles: (tenantSlug: string) => `/${tenantSlug}/roles`,
    uoms: (tenantSlug: string) => `/${tenantSlug}/uoms`,
    suppliers: (tenantSlug: string) => `/${tenantSlug}/suppliers`,
    categories: (tenantSlug: string) => `/${tenantSlug}/categories`,
    warehouseLayouts: (
      tenantSlug: string,
      filters?: { layoutId?: string; mode?: string; path?: string }
    ) => buildUrl(`/${tenantSlug}/warehouse-layouts`, filters),
    warehouseTemplates: (tenantSlug: string) => `/${tenantSlug}/warehouse-layouts/templates`,
    auditLogs: (tenantSlug: string) => `/${tenantSlug}/audit-logs`,
    inventory: (tenantSlug: string) => `/${tenantSlug}/inventory`,
    inventoryStock: (tenantSlug: string) => `/${tenantSlug}/inventory/stock`,
    inventoryOperations: (tenantSlug: string) => `/${tenantSlug}/inventory/operations`,
    inventoryMovements: (tenantSlug: string) => `/${tenantSlug}/inventory/movements`,
    receipts: (tenantSlug: string) => `/${tenantSlug}/receipts`,
    dispatches: (tenantSlug: string) => `/${tenantSlug}/dispatches`,
    countSessions: (tenantSlug: string) => `/${tenantSlug}/count-sessions`,
    gisFloorPlans: (tenantSlug: string) => `/${tenantSlug}/gis/floor-plans`,
    gisMap: (tenantSlug: string) => `/${tenantSlug}/gis/map`,
    gisZones: (tenantSlug: string) => `/${tenantSlug}/gis/zones`,
    hazardTypes: (tenantSlug: string) => `/${tenantSlug}/hazard-types`,
    zoneTypes: (tenantSlug: string) => `/${tenantSlug}/zone-types`,
    gisHazardBuffers: (tenantSlug: string) => `/${tenantSlug}/gis/hazard-buffers`,
    gisDataLayers: (tenantSlug: string) => `/${tenantSlug}/gis/data-layers`,
  },
};
