import axios from "axios";
import { api } from "@/lib/api";
import type {
  CreateLandlordRoleRequest,
  CreateLandlordUserRequest,
  LandlordPermissionOption,
  LandlordRoleDetails,
  LandlordRbacErrorResponse,
  LandlordUsersPage,
  ListLandlordUsersParams,
  ResetLandlordUserPasswordRequest,
  UpdateLandlordRoleRequest,
  UpdateLandlordUserRequest,
} from "@/features/landlord/types/rbac";

const LANDLORD_HEADERS = {
  "X-TENANT-ID": "BOOTSTRAP",
};

export async function getLandlordUsers(params: ListLandlordUsersParams) {
  const response = await api.get<LandlordUsersPage>("/landlord/users", {
    params,
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function createLandlordUser(payload: CreateLandlordUserRequest) {
  const response = await api.post("/landlord/users", payload, {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function updateLandlordUser(userId: string, payload: UpdateLandlordUserRequest) {
  const response = await api.put(`/landlord/users/${userId}`, payload, {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function resetLandlordUserPassword(userId: string, payload: ResetLandlordUserPasswordRequest) {
  await api.post(`/landlord/users/${userId}/reset-password`, payload, {
    headers: LANDLORD_HEADERS,
  });
}

export async function deactivateLandlordUser(userId: string) {
  await api.post(`/landlord/users/${userId}/deactivate`, undefined, {
    headers: LANDLORD_HEADERS,
  });
}

export async function reactivateLandlordUser(userId: string) {
  await api.post(`/landlord/users/${userId}/reactivate`, undefined, {
    headers: LANDLORD_HEADERS,
  });
}

export async function getLandlordRoles() {
  const response = await api.get<LandlordRoleDetails[]>("/landlord/roles", {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function getLandlordRole(roleCode: string) {
  const response = await api.get<LandlordRoleDetails>(`/landlord/roles/${roleCode}`, {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function createLandlordRole(payload: CreateLandlordRoleRequest) {
  const response = await api.post<LandlordRoleDetails>("/landlord/roles", payload, {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function updateLandlordRole(roleCode: string, payload: UpdateLandlordRoleRequest) {
  const response = await api.put<LandlordRoleDetails>(`/landlord/roles/${roleCode}`, payload, {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function getLandlordPermissions() {
  const response = await api.get<LandlordPermissionOption[]>("/landlord/permissions", {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export function extractRbacErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as LandlordRbacErrorResponse | string | undefined;
  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (data && typeof data === "object" && "message" in data) {
    const message = (data as LandlordRbacErrorResponse).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return null;
}
