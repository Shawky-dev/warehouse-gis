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
    AUDIT_LOGS_PATTERN: "/:tenantSlug/audit-logs",
    INVENTORY_PATTERN: "/:tenantSlug/inventory",
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
      filters?: { layoutId?: string; mode?: string; path?: string; tab?: string }
    ) => buildUrl(`/${tenantSlug}/warehouse-layouts`, filters),
    auditLogs: (tenantSlug: string) => `/${tenantSlug}/audit-logs`,
    inventory: (tenantSlug: string) => `/${tenantSlug}/inventory`,
  },
};
