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

export type LandlordPermission = (typeof LANDLORD_PERMISSIONS)[keyof typeof LANDLORD_PERMISSIONS];
