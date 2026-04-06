import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    CreateZoneTypeRequest,
    ListZoneTypesParams,
    UpdateZoneTypeRequest,
    ZoneTypeResult,
} from "@/features/tenant/types/f0";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/zone-types`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listZoneTypes(
    tenantSlug: string,
    params: ListZoneTypesParams = {}
): Promise<ZoneTypeResult[]> {
    const res = await api.get<ZoneTypeResult[]>(basePath(tenantSlug), {
        params,
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getZoneType(
    tenantSlug: string,
    id: string
): Promise<ZoneTypeResult> {
    const res = await api.get<ZoneTypeResult>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function createZoneType(
    tenantSlug: string,
    payload: CreateZoneTypeRequest
): Promise<ZoneTypeResult> {
    const res = await api.post<ZoneTypeResult>(basePath(tenantSlug), payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateZoneType(
    tenantSlug: string,
    id: string,
    payload: UpdateZoneTypeRequest
): Promise<ZoneTypeResult> {
    const res = await api.put<ZoneTypeResult>(`${basePath(tenantSlug)}/${id}`, payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function deactivateZoneType(tenantSlug: string, id: string): Promise<void> {
    await api.post(`${basePath(tenantSlug)}/${id}/deactivate`, undefined, {
        headers: headers(tenantSlug),
    });
}

export async function reactivateZoneType(tenantSlug: string, id: string): Promise<void> {
    await api.post(`${basePath(tenantSlug)}/${id}/reactivate`, undefined, {
        headers: headers(tenantSlug),
    });
}

export async function hardDeleteZoneType(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

export function extractZoneTypeErrorMessage(error: unknown): string | null {
    if (!axios.isAxiosError(error)) return null;
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    return null;
}
