import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    AddDispatchLineRequest,
    CreateDispatchDraftRequest,
    DispatchDetail,
    DispatchLine,
    DispatchListResult,
    ListDispatchesParams,
    UpdateDispatchLineRequest,
} from "@/features/tenant/types/dispatches";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/dispatches`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listDispatches(
    tenantSlug: string,
    params: ListDispatchesParams = {}
): Promise<DispatchListResult> {
    const res = await api.get<DispatchListResult>(basePath(tenantSlug), {
        params: { size: 20, ...params },
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getDispatch(tenantSlug: string, id: string): Promise<DispatchDetail> {
    const res = await api.get<DispatchDetail>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function createDraft(
    tenantSlug: string,
    body: CreateDispatchDraftRequest
): Promise<DispatchDetail> {
    const res = await api.post<DispatchDetail>(basePath(tenantSlug), body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function addLine(
    tenantSlug: string,
    dispatchId: string,
    body: AddDispatchLineRequest
): Promise<DispatchLine> {
    const res = await api.post<DispatchLine>(`${basePath(tenantSlug)}/${dispatchId}/lines`, body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateLine(
    tenantSlug: string,
    dispatchId: string,
    lineId: string,
    body: UpdateDispatchLineRequest
): Promise<DispatchLine> {
    const res = await api.put<DispatchLine>(`${basePath(tenantSlug)}/${dispatchId}/lines/${lineId}`, body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function removeLine(tenantSlug: string, dispatchId: string, lineId: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${dispatchId}/lines/${lineId}`, {
        headers: headers(tenantSlug),
    });
}

export async function postDispatch(tenantSlug: string, id: string): Promise<DispatchDetail> {
    const res = await api.post<DispatchDetail>(`${basePath(tenantSlug)}/${id}/post`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function voidDispatch(tenantSlug: string, id: string): Promise<DispatchDetail> {
    const res = await api.post<DispatchDetail>(`${basePath(tenantSlug)}/${id}/void`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function deleteDraftDispatch(tenantSlug: string, id: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
}

export function extractDispatchErrorMessage(error: unknown, fallback: string): string {
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