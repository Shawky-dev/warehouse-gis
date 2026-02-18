import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { getAccessToken, refreshAccessTokenOnce } from "@/features/auth/session/authSessionManager";

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const baseURL = import.meta.env.VITE_API_URL;

export const publicApi = axios.create({
  baseURL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

export const api = axios.create({
  baseURL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

const AUTH_PATHS = new Set(["/auth/login", "/auth/refresh", "/auth/logout"]);

function isAuthPath(url?: string) {
  if (!url) {
    return false;
  }

  const normalized = url.startsWith("http") ? new URL(url).pathname : url;
  return AUTH_PATHS.has(normalized);
}

api.interceptors.request.use((config) => {
  const token = getAccessToken();

  if (token && !isAuthPath(config.url)) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }

  config.withCredentials = true;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const responseStatus = error.response?.status;
    const originalRequest = error.config as RetryableRequestConfig | undefined;

    if (!originalRequest || responseStatus !== 401 || originalRequest._retry || isAuthPath(originalRequest.url)) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const refreshedSession = await refreshAccessTokenOnce();
      originalRequest.headers.set("Authorization", `Bearer ${refreshedSession.accessToken}`);
      return api(originalRequest);
    } catch {
      return Promise.reject(error);
    }
  }
);
