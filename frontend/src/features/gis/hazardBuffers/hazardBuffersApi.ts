import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type { HazardBufferResult, HazardBufferFeatureProps } from "@/features/tenant/types/gis";
import type { GeoJsonFeatureCollection } from "@/features/gis/zones/zonesApi";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/gis/hazard-buffers`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

// Re-export for convenience
export type { HazardBufferFeatureProps };

export interface HazardBufferRequest {
    name: string;
    coordinates?: number[][][];
    notes?: string | null;
    restrictedHazardTypeIds: string[];
}

export async function listHazardBuffers(tenantSlug: string): Promise<HazardBufferResult[]> {
    const res = await api.get<HazardBufferResult[]>(basePath(tenantSlug), {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getHazardBuffer(
    tenantSlug: string,
    id: string
): Promise<HazardBufferResult> {
    const res = await api.get<HazardBufferResult>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function createHazardBuffer(
    tenantSlug: string,
    payload: HazardBufferRequest
): Promise<HazardBufferResult> {
    const res = await api.post<HazardBufferResult>(basePath(tenantSlug), payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateHazardBuffer(
    tenantSlug: string,
    id: string,
    payload: HazardBufferRequest
): Promise<HazardBufferResult> {
    const res = await api.put<HazardBufferResult>(`${basePath(tenantSlug)}/${id}`, payload, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function fetchHazardBuffersGeoJson(
    tenantSlug: string
): Promise<GeoJsonFeatureCollection<HazardBufferFeatureProps>> {
    const res = await api.get<GeoJsonFeatureCollection<HazardBufferFeatureProps>>(
        `${basePath(tenantSlug)}/geojson`,
        { headers: headers(tenantSlug) }
    );
    return res.data;
}

export async function importHazardBuffers(
    tenantSlug: string,
    file: File
): Promise<HazardBufferResult[]> {
    const formData = new FormData();
    formData.append("file", file);
    const res = await api.post<HazardBufferResult[]>(
        `${basePath(tenantSlug)}/import`,
        formData,
        {
            headers: {
                ...headers(tenantSlug),
                "Content-Type": "multipart/form-data",
            },
        }
    );
    return res.data;
}

export async function deleteHazardBuffer(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

export function extractHazardBufferErrorMessage(error: unknown, fallback?: string): string | null {
    if (!axios.isAxiosError(error)) return fallback ?? null;
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    return fallback ?? null;
}
