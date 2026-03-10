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
  UOMS_VIEW: "tenant.uoms.view",
  UOMS_CREATE: "tenant.uoms.create",
  UOMS_EDIT: "tenant.uoms.edit",
  UOMS_SOFT_DELETE: "tenant.uoms.soft_delete",
  UOMS_RESTORE: "tenant.uoms.restore",
  UOMS_HARD_DELETE: "tenant.uoms.hard_delete",
  SUPPLIERS_VIEW: "tenant.suppliers.view",
  SUPPLIERS_CREATE: "tenant.suppliers.create",
  SUPPLIERS_EDIT: "tenant.suppliers.edit",
  SUPPLIERS_SOFT_DELETE: "tenant.suppliers.soft_delete",
  SUPPLIERS_RESTORE: "tenant.suppliers.restore",
  SUPPLIERS_HARD_DELETE: "tenant.suppliers.hard_delete",
  PRODUCTS_VIEW: "tenant.products.view",
  PRODUCTS_CREATE: "tenant.products.create",
  PRODUCTS_EDIT: "tenant.products.edit",
  PRODUCTS_SOFT_DELETE: "tenant.products.soft_delete",
  PRODUCTS_RESTORE: "tenant.products.restore",
  PRODUCTS_HARD_DELETE: "tenant.products.hard_delete",
  AUDIT_VIEW: "tenant.audit.view",
} as const;

export type LandlordPermission = (typeof LANDLORD_PERMISSIONS)[keyof typeof LANDLORD_PERMISSIONS];
export type TenantPermission = (typeof TENANT_PERMISSIONS)[keyof typeof TENANT_PERMISSIONS];
