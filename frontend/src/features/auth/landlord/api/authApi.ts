import { publicApi } from "@/lib/api";
import type { AuthResponse, LoginRequest } from "@/features/auth/shared/types";

export async function landlordLogin(payload: LoginRequest) {
  const response = await publicApi.post<AuthResponse>("/landlord/auth/login", payload);
  return response.data;
}

export async function landlordRefresh() {
  const response = await publicApi.post<AuthResponse>("/landlord/auth/refresh");
  return response.data;
}

export async function landlordLogout() {
  await publicApi.post("/landlord/auth/logout");
}
