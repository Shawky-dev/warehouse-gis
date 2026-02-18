import { api } from "../../../lib/api";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  /* e.g., eventually JWT token */
  token: string;
}

export const login = async (credentials: LoginRequest) => {
  const response = await api.post("/auth/login", credentials);
  return response.data;
};
