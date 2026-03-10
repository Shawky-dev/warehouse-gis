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
    WAREHOUSE_AISLES_PATTERN: "/:tenantSlug/warehouse-layouts/:layoutId/aisles",
    WAREHOUSE_SIDES_PATTERN: "/:tenantSlug/aisles/:aisleId/sides",
    WAREHOUSE_BAYS_PATTERN: "/:tenantSlug/sides/:sideId/bays",
    WAREHOUSE_LEVELS_PATTERN: "/:tenantSlug/bays/:bayId/levels",
    WAREHOUSE_SHELVES_PATTERN: "/:tenantSlug/bay-levels/:levelId/shelves",
    AUDIT_LOGS_PATTERN: "/:tenantSlug/audit-logs",
    // Global warehouse entity routes (filter via query string)
    WAREHOUSE_AISLES_GLOBAL_PATTERN: "/:tenantSlug/aisles",
    WAREHOUSE_SIDES_GLOBAL_PATTERN: "/:tenantSlug/sides",
    WAREHOUSE_BAYS_GLOBAL_PATTERN: "/:tenantSlug/bays",
    WAREHOUSE_LEVELS_GLOBAL_PATTERN: "/:tenantSlug/levels",
    WAREHOUSE_SHELVES_GLOBAL_PATTERN: "/:tenantSlug/shelves",
    root: (tenantSlug: string) => `/${tenantSlug}`,
    authLogin: (tenantSlug: string) => `/${tenantSlug}/auth/login`,
    products: (tenantSlug: string) => `/${tenantSlug}/products`,
    users: (tenantSlug: string) => `/${tenantSlug}/users`,
    roles: (tenantSlug: string) => `/${tenantSlug}/roles`,
    uoms: (tenantSlug: string) => `/${tenantSlug}/uoms`,
    suppliers: (tenantSlug: string) => `/${tenantSlug}/suppliers`,
    categories: (tenantSlug: string) => `/${tenantSlug}/categories`,
    warehouseLayouts: (tenantSlug: string) => `/${tenantSlug}/warehouse-layouts`,
    warehouseAisles: (tenantSlug: string, layoutId: string) =>
      `/${tenantSlug}/warehouse-layouts/${layoutId}/aisles`,
    warehouseSides: (tenantSlug: string, aisleId: string) =>
      `/${tenantSlug}/aisles/${aisleId}/sides`,
    warehouseBays: (tenantSlug: string, sideId: string) => `/${tenantSlug}/sides/${sideId}/bays`,
    warehouseLevels: (tenantSlug: string, bayId: string) => `/${tenantSlug}/bays/${bayId}/levels`,
    warehouseShelves: (tenantSlug: string, levelId: string) =>
      `/${tenantSlug}/bay-levels/${levelId}/shelves`,
    auditLogs: (tenantSlug: string) => `/${tenantSlug}/audit-logs`,
    // Global warehouse entity builder functions
    warehouseAislesGlobal: (
      tenantSlug: string,
      filters?: { layoutId?: string }
    ) => buildUrl(`/${tenantSlug}/aisles`, filters),
    warehouseSidesGlobal: (
      tenantSlug: string,
      filters?: { layoutId?: string; aisleId?: string }
    ) => buildUrl(`/${tenantSlug}/sides`, filters),
    warehouseBaysGlobal: (
      tenantSlug: string,
      filters?: { layoutId?: string; aisleId?: string; sideId?: string }
    ) => buildUrl(`/${tenantSlug}/bays`, filters),
    warehouseLevelsGlobal: (
      tenantSlug: string,
      filters?: { layoutId?: string; aisleId?: string; sideId?: string; bayId?: string }
    ) => buildUrl(`/${tenantSlug}/levels`, filters),
    warehouseShelvesGlobal: (
      tenantSlug: string,
      filters?: { layoutId?: string; aisleId?: string; sideId?: string; bayId?: string; levelId?: string }
    ) => buildUrl(`/${tenantSlug}/shelves`, filters),
  },
};
