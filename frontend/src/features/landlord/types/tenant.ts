export interface TenantSummary {
  tenantId: string;
  schema: string;
}

export interface CreateTenantRequest {
  tenantId: string;
  schema: string;
}
