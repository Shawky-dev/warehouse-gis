import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";

export interface DataLayerResult {
    id: string;
    name: string;
    fileName: string;
    createdAt: string;
    updatedAt: string;
}

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/gis/data-layers`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listDataLayers(tenantSlug: string): Promise<DataLayerResult[]> {
    const res = await api.get<DataLayerResult[]>(basePath(tenantSlug), {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function uploadDataLayer(
    tenantSlug: string,
    name: string,
    file: File
): Promise<DataLayerResult> {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    const res = await api.post<DataLayerResult>(basePath(tenantSlug), formData, {
        headers: {
            ...headers(tenantSlug),
            "Content-Type": "multipart/form-data",
        },
    });
    return res.data;
}

export async function renameDataLayer(
    tenantSlug: string,
    id: string,
    name: string
): Promise<DataLayerResult> {
    const res = await api.put<DataLayerResult>(
        `${basePath(tenantSlug)}/${id}`,
        null,
        {
            params: { name },
            headers: headers(tenantSlug),
        }
    );
    return res.data;
}

export async function deleteDataLayer(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

/**
 * Fetches the data layer image via axios (Bearer token auto-injected)
 * and returns a blob URL for use in ArcGIS MediaLayer.
 * Caller must call URL.revokeObjectURL() when done.
 */
export async function fetchDataLayerImageBlob(
    tenantSlug: string,
    id: string
): Promise<string> {
    const res = await api.get<Blob>(`${basePath(tenantSlug)}/${id}/image`, {
        headers: headers(tenantSlug),
        responseType: "blob",
    });
    return URL.createObjectURL(res.data);
}

export function extractDataLayerErrorMessage(error: unknown): string | null {
    if (!axios.isAxiosError(error)) return null;
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    return null;
}
