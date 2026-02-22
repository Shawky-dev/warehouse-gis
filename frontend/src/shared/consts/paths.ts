
export const PATHS = {
  ROOT: "/",

  LANDLORD: {
    AUTH_LOGIN: "/landlord/auth/login",
    ROOT: "/landlord",
    DASHBOARD: "/landlord",
    WAREHOUSES: "/landlord/warehouses",
    USERS: "/landlord/users",
    ROLES: "/landlord/roles",
  },

  TENANT: {
    ROOT_PATTERN: "/:tenantSlug",
    AUTH_LOGIN_PATTERN: "/:tenantSlug/auth/login",
    PRODUCTS_PATTERN: "/:tenantSlug/products",
    root: (tenantSlug: string) => `/${tenantSlug}`,
    authLogin: (tenantSlug: string) => `/${tenantSlug}/auth/login`,
    products: (tenantSlug: string) => `/${tenantSlug}/products`,
  },
} as const
