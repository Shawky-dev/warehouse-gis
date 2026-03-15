import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    CountLine,
    CountSessionDetail,
    CountSessionListResult,
    ListCountSessionsParams,
    OpenCountSessionRequest,
    UpdateCountLineRequest,
} from "@/features/tenant/types/counting";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/count-sessions`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listCountSessions(
    tenantSlug: string,
    params: ListCountSessionsParams = {}
): Promise<CountSessionListResult> {
    const res = await api.get<CountSessionListResult>(basePath(tenantSlug), {
        params: { size: 20, ...params },
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getCountSession(tenantSlug: string, id: string): Promise<CountSessionDetail> {
    const res = await api.get<CountSessionDetail>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function openCountSession(
    tenantSlug: string,
    body: OpenCountSessionRequest
): Promise<CountSessionDetail> {
    const res = await api.post<CountSessionDetail>(basePath(tenantSlug), body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateCountLine(
    tenantSlug: string,
    sessionId: string,
    lineId: string,
    body: UpdateCountLineRequest
): Promise<CountLine> {
    const res = await api.put<CountLine>(`${basePath(tenantSlug)}/${sessionId}/lines/${lineId}`, body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function postCountSession(tenantSlug: string, id: string): Promise<CountSessionDetail> {
    const res = await api.post<CountSessionDetail>(`${basePath(tenantSlug)}/${id}/post`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function voidCountSession(tenantSlug: string, id: string): Promise<CountSessionDetail> {
    const res = await api.post<CountSessionDetail>(`${basePath(tenantSlug)}/${id}/void`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function deleteDraftCountSession(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

export function extractCountingErrorMessage(error: unknown, fallback: string): string {
    if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response &&
        typeof error.response === "object" &&
        "data" in error.response &&
        error.response.data &&
        typeof error.response.data === "object" &&
        "message" in error.response.data &&
        typeof (error.response.data as { message: unknown }).message === "string"
    ) {
        return (error.response.data as { message: string }).message;
    }
    return fallback;
}
