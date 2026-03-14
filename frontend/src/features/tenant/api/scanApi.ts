import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type { ScanResolveResult } from "@/features/tenant/types/scan";

export async function resolveCode(
  tenantSlug: string,
  code: string
): Promise<ScanResolveResult> {
  const slug = normalizeTenantSlug(tenantSlug);
  const response = await api.get<ScanResolveResult>(
    `/${slug}/scan/resolve`,
    {
      params: { code },
      headers: { "X-TENANT-ID": slug },
    }
  );
  return response.data;
}
