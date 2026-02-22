import { publicApi } from "@/lib/api";
import type { AuthResponse, LoginRequest } from "@/features/auth/shared/types";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";

function tenantAuthBasePath(tenantSlug: string): string {
  const normalizedSlug = normalizeTenantSlug(tenantSlug);
  return `/${normalizedSlug}/auth`;
}

export async function tenantLogin(tenantSlug: string, payload: LoginRequest) {
  const response = await publicApi.post<AuthResponse>(`${tenantAuthBasePath(tenantSlug)}/login`, payload);
  return response.data;
}

export async function tenantRefresh(tenantSlug: string) {
  const response = await publicApi.post<AuthResponse>(`${tenantAuthBasePath(tenantSlug)}/refresh`);
  return response.data;
}

export async function tenantLogout(tenantSlug: string) {
  await publicApi.post(`${tenantAuthBasePath(tenantSlug)}/logout`);
}
