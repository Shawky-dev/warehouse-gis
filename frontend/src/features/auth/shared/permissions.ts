export const LANDLORD_PERMISSIONS = {
  TENANTS_VIEW: "landlord.tenants.view",
  TENANTS_CREATE: "landlord.tenants.create",
  USERS_VIEW: "landlord.users.view",
  USERS_CREATE: "landlord.users.create",
  USERS_EDIT: "landlord.users.edit",
  USERS_RESET_PASSWORD: "landlord.users.reset_password",
  USERS_DEACTIVATE: "landlord.users.deactivate",
  USERS_REACTIVATE: "landlord.users.reactivate",
  ROLES_EDIT: "landlord.roles.edit",
} as const;

export const TENANT_PERMISSIONS = {
  USERS_VIEW: "tenant.users.view",
  USERS_CREATE: "tenant.users.create",
  USERS_EDIT: "tenant.users.edit",
  USERS_RESET_PASSWORD: "tenant.users.reset_password",
  USERS_DEACTIVATE: "tenant.users.deactivate",
  USERS_REACTIVATE: "tenant.users.reactivate",
  ROLES_EDIT: "tenant.roles.edit",
} as const;

export type LandlordPermission = (typeof LANDLORD_PERMISSIONS)[keyof typeof LANDLORD_PERMISSIONS];
export type TenantPermission = (typeof TENANT_PERMISSIONS)[keyof typeof TENANT_PERMISSIONS];
