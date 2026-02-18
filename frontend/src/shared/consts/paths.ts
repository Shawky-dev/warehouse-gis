
export const PATHS = {
  ROOT: "/",
  LOGIN: "/login",
  REGISTER: "/register",

  LANDLORD: {
    ROOT: "/landlord",
    DASHBOARD: "/landlord",
    WAREHOUSES: "/landlord/warehouses",
    USERS: "/landlord/users",
  },

  TENANT: {
    ROOT: "/tenant",
    DASHBOARD: "/tenant",
    WAREHOUSES: "/tenant/warehouses",
  },
} as const
