import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type { StaticHeatmapRecord, DynamicHeatmapMetric } from "@/features/tenant/types/gis";
import type { GeoJsonFeatureCollection } from "@/features/gis/zones/zonesApi";
import type { DynamicHeatmapFeatureProps } from "@/features/tenant/types/gis";

function staticBasePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/gis/heatmaps/static`;
}

function dynamicBasePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/gis/heatmaps/dynamic`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listStaticHeatmaps(tenantSlug: string): Promise<StaticHeatmapRecord[]> {
    const res = await api.get<StaticHeatmapRecord[]>(staticBasePath(tenantSlug), {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function uploadStaticHeatmap(
    tenantSlug: string,
    name: string,
    file: File
): Promise<StaticHeatmapRecord> {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("name", name);
    const res = await api.post<StaticHeatmapRecord>(staticBasePath(tenantSlug), formData, {
        headers: {
            ...headers(tenantSlug),
            "Content-Type": "multipart/form-data",
        },
    });
    return res.data;
}

export async function setDefaultStaticHeatmap(
    tenantSlug: string,
    heatmapId: string
): Promise<StaticHeatmapRecord> {
    const res = await api.put<StaticHeatmapRecord>(
        `${staticBasePath(tenantSlug)}/${heatmapId}/default`,
        null,
        { headers: headers(tenantSlug) }
    );
    return res.data;
}

export async function deleteStaticHeatmap(
    tenantSlug: string,
    heatmapId: string
): Promise<void> {
    await api.delete(`${staticBasePath(tenantSlug)}/${heatmapId}`, {
        headers: headers(tenantSlug),
    });
}

export async function listDynamicHeatmapMetrics(tenantSlug: string): Promise<DynamicHeatmapMetric[]> {
    const res = await api.get<DynamicHeatmapMetric[]>(`${dynamicBasePath(tenantSlug)}/metrics`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getDynamicHeatmapPoints(
    tenantSlug: string,
    metricKey: string
): Promise<GeoJsonFeatureCollection<DynamicHeatmapFeatureProps>> {
    const res = await api.get<GeoJsonFeatureCollection<DynamicHeatmapFeatureProps>>(
        `${dynamicBasePath(tenantSlug)}/points`,
        {
            params: { metric: metricKey },
            headers: headers(tenantSlug),
        }
    );
    return res.data;
}

export function extractHeatmapErrorMessage(error: unknown): string | null {
    if (!axios.isAxiosError(error)) return null;
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    return null;
}
