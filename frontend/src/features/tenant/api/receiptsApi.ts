import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    AddReceiptLineRequest,
    CreateReceiptDraftRequest,
    ListReceiptsParams,
    ReceiptDetail,
    ReceiptLine,
    ReceiptListResult,
    UpdateReceiptLineRequest,
} from "@/features/tenant/types/receipts";

function basePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}/receipts`;
}

function headers(tenantSlug: string): Record<string, string> {
    return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

export async function listReceipts(
    tenantSlug: string,
    params: ListReceiptsParams = {}
): Promise<ReceiptListResult> {
    const res = await api.get<ReceiptListResult>(basePath(tenantSlug), {
        params: { size: 20, ...params },
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function getReceipt(tenantSlug: string, id: string): Promise<ReceiptDetail> {
    const res = await api.get<ReceiptDetail>(`${basePath(tenantSlug)}/${id}`, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function createDraft(
    tenantSlug: string,
    body: CreateReceiptDraftRequest
): Promise<ReceiptDetail> {
    const res = await api.post<ReceiptDetail>(basePath(tenantSlug), body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function addLine(
    tenantSlug: string,
    receiptId: string,
    body: AddReceiptLineRequest
): Promise<ReceiptLine> {
    const res = await api.post<ReceiptLine>(`${basePath(tenantSlug)}/${receiptId}/lines`, body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function updateLine(
    tenantSlug: string,
    receiptId: string,
    lineId: string,
    body: UpdateReceiptLineRequest
): Promise<ReceiptLine> {
    const res = await api.put<ReceiptLine>(`${basePath(tenantSlug)}/${receiptId}/lines/${lineId}`, body, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function removeLine(tenantSlug: string, receiptId: string, lineId: string): Promise<void> {
    await api.delete(`${basePath(tenantSlug)}/${receiptId}/lines/${lineId}`, {
        headers: headers(tenantSlug),
    });
}

export async function postReceipt(tenantSlug: string, id: string): Promise<ReceiptDetail> {
    const res = await api.post<ReceiptDetail>(`${basePath(tenantSlug)}/${id}/post`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export async function voidReceipt(tenantSlug: string, id: string): Promise<ReceiptDetail> {
    const res = await api.post<ReceiptDetail>(`${basePath(tenantSlug)}/${id}/void`, undefined, {
        headers: headers(tenantSlug),
    });
    return res.data;
}

export function extractReceiptErrorMessage(error: unknown, fallback: string): string {
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
