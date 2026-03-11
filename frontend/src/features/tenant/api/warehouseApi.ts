import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
    AddWarehouseBlockRequest,
    AddWarehouseBlocksRequest,
    CopyWarehouseBlockSubtreeRequest,
    CreateClassicPresetRequest,
    ListWarehouseLayoutsParams,
    ListWarehouseTemplatesParams,
    MoveWarehouseBlockRequest,
    ReassignWarehouseBlockTemplateRequest,
    UpdateWarehouseBlockMetadataRequest,
    UpsertWarehouseLayoutRequest,
    UpsertWarehouseTemplateRequest,
    WarehouseApiErrorResponse,
    WarehouseBlockNode,
    WarehouseBlockOperationResult,
    WarehouseBlockResult,
    WarehouseLayoutPageResult,
    WarehouseLayoutResult,
    WarehouseTemplatePageResult,
    WarehouseTemplateResult,
} from "@/features/tenant/types/warehouse";

function tenantBasePath(tenantSlug: string): string {
    return `/${normalizeTenantSlug(tenantSlug)}`;
}

function tenantHeaders(tenantSlug: string): Record<string, string> {
    return {
        "X-TENANT-ID": normalizeTenantSlug(tenantSlug),
    };
}

export async function listWarehouseLayouts(
    tenantSlug: string,
    params?: ListWarehouseLayoutsParams
): Promise<WarehouseLayoutPageResult> {
    const response = await api.get<WarehouseLayoutPageResult>(`${tenantBasePath(tenantSlug)}/warehouse-layouts`, {
        params,
        headers: tenantHeaders(tenantSlug),
    });
    return response.data;
}

export async function getWarehouseLayout(
    tenantSlug: string,
    layoutId: string
): Promise<WarehouseLayoutResult> {
    const response = await api.get<WarehouseLayoutResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function createWarehouseLayout(
    tenantSlug: string,
    payload: UpsertWarehouseLayoutRequest
): Promise<WarehouseLayoutResult> {
    const response = await api.post<WarehouseLayoutResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function updateWarehouseLayout(
    tenantSlug: string,
    layoutId: string,
    payload: UpsertWarehouseLayoutRequest
): Promise<WarehouseLayoutResult> {
    const response = await api.put<WarehouseLayoutResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function activateWarehouseLayout(tenantSlug: string, layoutId: string): Promise<void> {
    await api.post(`${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/activate`, undefined, {
        headers: tenantHeaders(tenantSlug),
    });
}

export async function deactivateWarehouseLayout(tenantSlug: string, layoutId: string): Promise<void> {
    await api.post(`${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/deactivate`, undefined, {
        headers: tenantHeaders(tenantSlug),
    });
}

export async function deleteWarehouseLayout(tenantSlug: string, layoutId: string): Promise<void> {
    await api.delete(`${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`, {
        headers: tenantHeaders(tenantSlug),
    });
}

export async function createClassicWarehousePreset(
    tenantSlug: string,
    payload: CreateClassicPresetRequest
): Promise<WarehouseLayoutResult> {
    const response = await api.post<WarehouseLayoutResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/presets/classic`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function listWarehouseTemplates(
    tenantSlug: string,
    params?: ListWarehouseTemplatesParams
): Promise<WarehouseTemplatePageResult> {
    const response = await api.get<WarehouseTemplatePageResult>(`${tenantBasePath(tenantSlug)}/block-templates`, {
        params,
        headers: tenantHeaders(tenantSlug),
    });
    return response.data;
}

export async function createWarehouseTemplate(
    tenantSlug: string,
    payload: UpsertWarehouseTemplateRequest
): Promise<WarehouseTemplateResult> {
    const response = await api.post<WarehouseTemplateResult>(`${tenantBasePath(tenantSlug)}/block-templates`, payload, {
        headers: tenantHeaders(tenantSlug),
    });
    return response.data;
}

export async function updateWarehouseTemplate(
    tenantSlug: string,
    templateId: string,
    payload: UpsertWarehouseTemplateRequest
): Promise<WarehouseTemplateResult> {
    const response = await api.put<WarehouseTemplateResult>(
        `${tenantBasePath(tenantSlug)}/block-templates/${templateId}`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function deleteWarehouseTemplate(tenantSlug: string, templateId: string): Promise<void> {
    await api.delete(`${tenantBasePath(tenantSlug)}/block-templates/${templateId}`, {
        headers: tenantHeaders(tenantSlug),
    });
}

export async function listWarehouseLayoutBlocks(
    tenantSlug: string,
    layoutId: string
): Promise<WarehouseBlockNode[]> {
    const response = await api.get<WarehouseBlockNode[]>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks`,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function addWarehouseLayoutBlock(
    tenantSlug: string,
    layoutId: string,
    payload: AddWarehouseBlockRequest
): Promise<WarehouseBlockResult> {
    const response = await api.post<WarehouseBlockResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function addWarehouseLayoutBlocks(
    tenantSlug: string,
    layoutId: string,
    payload: AddWarehouseBlocksRequest
): Promise<WarehouseBlockOperationResult> {
    const response = await api.post<WarehouseBlockOperationResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/batch`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function copyWarehouseLayoutBlockSubtree(
    tenantSlug: string,
    layoutId: string,
    payload: CopyWarehouseBlockSubtreeRequest
): Promise<WarehouseBlockOperationResult> {
    const response = await api.post<WarehouseBlockOperationResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/copy-subtree`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function moveWarehouseLayoutBlock(
    tenantSlug: string,
    layoutId: string,
    blockId: string,
    payload: MoveWarehouseBlockRequest
): Promise<WarehouseBlockResult> {
    const response = await api.put<WarehouseBlockResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/${blockId}/move`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function reassignWarehouseLayoutBlockTemplate(
    tenantSlug: string,
    layoutId: string,
    blockId: string,
    payload: ReassignWarehouseBlockTemplateRequest
): Promise<WarehouseBlockResult> {
    const response = await api.put<WarehouseBlockResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/${blockId}/template`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function updateWarehouseLayoutBlockMetadata(
    tenantSlug: string,
    layoutId: string,
    blockId: string,
    payload: UpdateWarehouseBlockMetadataRequest
): Promise<WarehouseBlockResult> {
    const response = await api.put<WarehouseBlockResult>(
        `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/${blockId}/metadata`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return response.data;
}

export async function deleteWarehouseLayoutBlock(
    tenantSlug: string,
    layoutId: string,
    blockId: string
): Promise<void> {
    await api.delete(`${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/blocks/${blockId}`, {
        headers: tenantHeaders(tenantSlug),
    });
}

export function extractWarehouseErrorMessage(error: unknown): string | null {
    if (axios.isAxiosError<WarehouseApiErrorResponse>(error)) {
        return error.response?.data?.message ?? error.message;
    }
    if (error instanceof Error) {
        return error.message;
    }
    return null;
}