import { publicApi } from "@/lib/api";
import type { AuthResponse, LoginRequest } from "@/features/auth/types";

export async function login(payload: LoginRequest) {
  const response = await publicApi.post<AuthResponse>("/landlord/auth/login", payload);
  return response.data;
}

export async function refresh() {
  const response = await publicApi.post<AuthResponse>("/landlord/auth/refresh");
  return response.data;
}

export async function logout() {
  await publicApi.post("/landlord/auth/logout");
}
