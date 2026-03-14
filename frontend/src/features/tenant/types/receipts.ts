export type ReceiptStatus = "DRAFT" | "POSTED" | "VOID";

export interface ReceiptListItem {
    id: string;
    supplierId: string | null;
    supplierName: string | null;
    reference: string | null;
    notes: string | null;
    status: ReceiptStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
}

export interface ReceiptLine {
    id: string;
    receiptId: string;
    productId: string;
    productSku: string | null;
    productName: string | null;
    destinationLocationId: string;
    locationPathLabel: string | null;
    qty: string;
    lotNumber: string | null;
    expiryDate: string | null;
    notes: string | null;
    position: number;
}

export interface ReceiptDetail {
    id: string;
    supplierId: string | null;
    supplierName: string | null;
    reference: string | null;
    notes: string | null;
    status: ReceiptStatus;
    createdBy: string;
    createdAt: string;
    postedAt: string | null;
    postedBy: string | null;
    voidedAt: string | null;
    voidedBy: string | null;
    lines: ReceiptLine[];
}

export interface ReceiptListResult {
    content: ReceiptListItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ListReceiptsParams {
    page?: number;
    size?: number;
    status?: ReceiptStatus;
    search?: string;
}

export interface CreateReceiptDraftRequest {
    supplierId?: string | null;
    reference?: string | null;
    notes?: string | null;
}

export interface AddReceiptLineRequest {
    productId: string;
    destinationLocationId: string;
    qty: string;
    lotNumber?: string | null;
    expiryDate?: string | null;
    notes?: string | null;
}

export interface UpdateReceiptLineRequest {
    qty: string;
    lotNumber?: string | null;
    expiryDate?: string | null;
    notes?: string | null;
}
