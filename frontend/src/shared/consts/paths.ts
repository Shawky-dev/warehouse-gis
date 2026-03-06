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
    root: (tenantSlug: string) => `/${tenantSlug}`,
    authLogin: (tenantSlug: string) => `/${tenantSlug}/auth/login`,
    products: (tenantSlug: string) => `/${tenantSlug}/products`,
    users: (tenantSlug: string) => `/${tenantSlug}/users`,
    roles: (tenantSlug: string) => `/${tenantSlug}/roles`,
  },
} as const;
