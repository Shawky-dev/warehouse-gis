export type WarehouseIdentifierFormat = "NUMERIC" | "ALPHA" | "CUSTOM" | "FREE_TEXT";
export type WarehouseSideConfig = "NONE" | "LR" | "AB" | "CUSTOM";

export interface WarehouseLayoutResult {
    id: string;
    name: string;
    description: string | null;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface WarehouseLayoutPageResult {
    content: WarehouseLayoutResult[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ListWarehouseLayoutsParams {
    page?: number;
    size?: number;
    search?: string;
    active?: boolean;
}

export interface UpsertWarehouseLayoutRequest {
    name: string;
    description?: string | null;
}

export interface CreateClassicPresetRequest {
    name: string;
    description?: string | null;
    activate: boolean;
}

export interface WarehouseTemplateResult {
    id: string;
    name: string;
    identifierFormat: WarehouseIdentifierFormat;
    sideConfig: WarehouseSideConfig;
    sideOptions: string[] | null;
    required: boolean;
    description: string | null;
    iconName: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface WarehouseTemplatePageResult {
    content: WarehouseTemplateResult[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ListWarehouseTemplatesParams {
    page?: number;
    size?: number;
    search?: string;
}

export interface UpsertWarehouseTemplateRequest {
    name: string;
    identifierFormat: WarehouseIdentifierFormat;
    sideConfig?: WarehouseSideConfig;
    sideOptions?: string[] | null;
    required: boolean;
    description?: string | null;
    iconName?: string | null;
}

export interface WarehouseBlockResult {
    id: string;
    layoutId: string;
    blockTemplateId: string;
    parentId: string | null;
    position: number;
    identifier: string | null;
    side: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface WarehouseBlockNode {
    block: WarehouseBlockResult;
    children: WarehouseBlockNode[];
}

export interface AddWarehouseBlockRequest {
    blockTemplateId: string;
    parentId?: string | null;
    position?: number | null;
    side?: string | null;
}

export interface AddWarehouseBlocksRequest extends AddWarehouseBlockRequest {
    count: number;
}

export interface CopyWarehouseBlockSubtreeRequest {
    sourceBlockId: string;
    targetParentId?: string | null;
    position?: number | null;
    copies: number;
}

export interface WarehouseBlockOperationResult {
    createdBlocks: WarehouseBlockResult[];
    totalCreated: number;
    rootCount: number;
}

export interface MoveWarehouseBlockRequest {
    parentId?: string | null;
    position: number;
}

export interface ReassignWarehouseBlockTemplateRequest {
    blockTemplateId: string;
}

export interface UpdateWarehouseBlockMetadataRequest {
    side?: string | null;
}

export interface WarehouseApiErrorResponse {
    code?: string;
    message?: string;
}

export interface WarehouseFlattenedNode {
    node: WarehouseBlockNode;
    depth: number;
    path: string[];
}