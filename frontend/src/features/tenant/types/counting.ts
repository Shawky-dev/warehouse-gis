export type CountStatus = "OPEN" | "POSTED" | "VOID";

export interface CountSessionListItem {
    id: string;
    name: string;
    status: CountStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
    postedBy: string | null;
    voidedAt: string | null;
    voidedBy: string | null;
    locationCount: number;
    lineCount: number;
    qrData: string;
}

export interface CountLine {
    id: string;
    sessionId: string;
    locationId: string;
    locationPathLabel: string | null;
    productId: string;
    productSku: string | null;
    productName: string | null;
    lotNumber: string | null;
    expectedQty: string;
    countedQty: string | null;
    variance: string | null;
}

export interface CountSessionDetail {
    id: string;
    name: string;
    status: CountStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
    postedBy: string | null;
    voidedAt: string | null;
    voidedBy: string | null;
    locationIds: string[];
    lines: CountLine[];
    qrData: string;
}

export interface CountSessionListResult {
    content: CountSessionListItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ListCountSessionsParams {
    page?: number;
    size?: number;
    status?: CountStatus;
    search?: string;
}

export interface OpenCountSessionRequest {
    name: string;
    locationIds: string[];
}

export interface UpdateCountLineRequest {
    countedQty: string;
}
