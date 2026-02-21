import axios from "axios";
import { api } from "@/lib/api";
import type { CreateTenantRequest, TenantSummary } from "@/features/landlord/types/tenant";

const LANDLORD_HEADERS = {
  "X-TENANT-ID": "BOOTSTRAP",
};

export async function getTenants() {
  const response = await api.get<TenantSummary[]>("/landlord/tenants", {
    headers: LANDLORD_HEADERS,
  });
  return response.data;
}

export async function createTenant(payload: CreateTenantRequest) {
  await api.post("/landlord/tenants", payload, {
    headers: LANDLORD_HEADERS,
  });
}

export function extractTenantErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return "Failed to create tenant.";
  }

  const data = error.response?.data;
  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (typeof data?.message === "string" && data.message.trim()) {
    return data.message;
  }

  return "Failed to create tenant.";
}
