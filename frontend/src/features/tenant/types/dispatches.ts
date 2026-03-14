export type DispatchStatus = "DRAFT" | "POSTED" | "VOID";

export interface DispatchListItem {
    id: string;
    destination: string | null;
    reference: string | null;
    notes: string | null;
    status: DispatchStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
    postedBy: string | null;
    voidedAt: string | null;
    voidedBy: string | null;
    qrData: string;
}

export interface DispatchLine {
    id: string;
    dispatchId: string;
    productId: string;
    productSku: string | null;
    productName: string | null;
    sourceLocationId: string;
    locationPathLabel: string | null;
    qty: string;
    lotNumber: string | null;
    notes: string | null;
    position: number;
}

export interface DispatchDetail {
    id: string;
    destination: string | null;
    reference: string | null;
    notes: string | null;
    status: DispatchStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
    postedBy: string | null;
    voidedAt: string | null;
    voidedBy: string | null;
    lines: DispatchLine[];
    qrData: string;
}

export interface DispatchListResult {
    content: DispatchListItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ListDispatchesParams {
    page?: number;
    size?: number;
    status?: DispatchStatus;
    search?: string;
}

export interface CreateDispatchDraftRequest {
    destination?: string | null;
    reference?: string | null;
    notes?: string | null;
}

export interface AddDispatchLineRequest {
    productId: string;
    sourceLocationId: string;
    qty: string;
    lotNumber?: string | null;
    notes?: string | null;
}

export interface UpdateDispatchLineRequest {
    qty: string;
    lotNumber?: string | null;
    notes?: string | null;
}