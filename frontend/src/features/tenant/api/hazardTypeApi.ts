import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    CreateHazardTypeRequest,
    HazardTypeResult,
    ListHazardTypesParams,
    UpdateHazardTypeRequest,
} from "@/features/tenant/types/f0";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/hazard-types`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listHazardTypes(
    tenantSlug: string,
    params: ListHazardTypesParams = {}
): Promise<HazardTypeResult[]> {
    const res = await api.get<HazardTypeResult[]>(basePath(tenantSlug), {
        params,
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getHazardType(
    tenantSlug: string,
    id: string
): Promise<HazardTypeResult> {
    const res = await api.get<HazardTypeResult>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function createHazardType(
    tenantSlug: string,
    payload: CreateHazardTypeRequest
): Promise<HazardTypeResult> {
    const res = await api.post<HazardTypeResult>(basePath(tenantSlug), payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateHazardType(
    tenantSlug: string,
    id: string,
    payload: UpdateHazardTypeRequest
): Promise<HazardTypeResult> {
    const res = await api.put<HazardTypeResult>(`${basePath(tenantSlug)}/${id}`, payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function deactivateHazardType(tenantSlug: string, id: string): Promise<void> {
    await api.post(`${basePath(tenantSlug)}/${id}/deactivate`, undefined, {
        headers: headers(tenantSlug),
    });
}

export async function reactivateHazardType(tenantSlug: string, id: string): Promise<void> {
    await api.post(`${basePath(tenantSlug)}/${id}/reactivate`, undefined, {
        headers: headers(tenantSlug),
    });
}

export async function hardDeleteHazardType(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

export function extractHazardTypeErrorMessage(error: unknown): string | null {
    if (!axios.isAxiosError(error)) return null;
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    return null;
}
