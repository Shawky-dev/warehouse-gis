import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type { DashboardEndpoint, DashboardSectionResponse } from "@/features/tenant/dashboard/types";

function basePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}/dashboard`;
}

function headers(tenantSlug: string): Record<string, string> {
  return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function getDashboardSection(
  tenantSlug: string,
  endpoint: DashboardEndpoint
): Promise<DashboardSectionResponse> {
  const response = await api.get<DashboardSectionResponse>(`${basePath(tenantSlug)}/${endpoint}`, {
    headers: headers(tenantSlug),
  });
  return response.data;
}

export function getSpatialKpis(tenantSlug: string) {
  return getDashboardSection(tenantSlug, "spatial-kpis");
}

export function getInventoryOps(tenantSlug: string) {
  return getDashboardSection(tenantSlug, "inventory-ops");
}

export function getWarnings(tenantSlug: string) {
  return getDashboardSection(tenantSlug, "warnings");
}

export function getMasterData(tenantSlug: string) {
  return getDashboardSection(tenantSlug, "master-data");
}

export function getActivity(tenantSlug: string) {
  return getDashboardSection(tenantSlug, "activity");
}

export function extractDashboardErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<{ message?: string } | string>(error)) {
    const responseData = error.response?.data;

    if (typeof responseData === "string" && responseData.trim()) {
      return responseData;
    }

    if (
      responseData &&
      typeof responseData === "object" &&
      "message" in responseData &&
      typeof responseData.message === "string" &&
      responseData.message.trim()
    ) {
      return responseData.message;
    }

    if (error.message.trim()) {
      return error.message;
    }
  }

  return fallback;
}
