export interface TenantSummary {
  tenantId: string;
  schema: string;
}

export interface CreateTenantAdminRequest {
  email: string;
  password: string;
}

export interface CreateTenantRequest {
  tenantId: string;
  schema: string;
  admin: CreateTenantAdminRequest;
}
