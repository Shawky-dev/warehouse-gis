import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
  CreateTenantRoleRequest,
  CreateTenantUserRequest,
  ListTenantUsersParams,
  ResetTenantUserPasswordRequest,
  TenantPermissionOption,
  TenantRbacErrorResponse,
  TenantRoleDetails,
  TenantUsersPage,
  UpdateTenantRoleRequest,
  UpdateTenantUserRequest,
} from "@/features/tenant/types/rbac";

function tenantRbacBasePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}`;
}

function tenantHeaders(tenantSlug: string): Record<string, string> {
  return {
    "X-TENANT-ID": normalizeTenantSlug(tenantSlug),
  };
}

export async function getTenantUsers(tenantSlug: string, params: ListTenantUsersParams) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.get<TenantUsersPage>(`${basePath}/users`, {
    params,
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function createTenantUser(tenantSlug: string, payload: CreateTenantUserRequest) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.post(`${basePath}/users`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function updateTenantUser(
  tenantSlug: string,
  userId: string,
  payload: UpdateTenantUserRequest
) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.put(`${basePath}/users/${userId}`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function resetTenantUserPassword(
  tenantSlug: string,
  userId: string,
  payload: ResetTenantUserPasswordRequest
) {
  const basePath = tenantRbacBasePath(tenantSlug);
  await api.post(`${basePath}/users/${userId}/reset-password`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function deactivateTenantUser(tenantSlug: string, userId: string) {
  const basePath = tenantRbacBasePath(tenantSlug);
  await api.post(`${basePath}/users/${userId}/deactivate`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function reactivateTenantUser(tenantSlug: string, userId: string) {
  const basePath = tenantRbacBasePath(tenantSlug);
  await api.post(`${basePath}/users/${userId}/reactivate`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function getTenantRoles(tenantSlug: string) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.get<TenantRoleDetails[]>(`${basePath}/roles`, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function getTenantRole(tenantSlug: string, roleCode: string) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.get<TenantRoleDetails>(`${basePath}/roles/${roleCode}`, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function createTenantRole(tenantSlug: string, payload: CreateTenantRoleRequest) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.post<TenantRoleDetails>(`${basePath}/roles`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function updateTenantRole(
  tenantSlug: string,
  roleCode: string,
  payload: UpdateTenantRoleRequest
) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.put<TenantRoleDetails>(`${basePath}/roles/${roleCode}`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function getTenantPermissions(tenantSlug: string) {
  const basePath = tenantRbacBasePath(tenantSlug);
  const response = await api.get<TenantPermissionOption[]>(`${basePath}/permissions`, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export function extractTenantRbacErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as TenantRbacErrorResponse | string | undefined;
  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (data && typeof data === "object" && "message" in data) {
    const message = (data as TenantRbacErrorResponse).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return null;
}
