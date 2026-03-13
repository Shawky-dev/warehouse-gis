// ── Movement types ────────────────────────────────────────────────────────────

export type MovementType = "RECEIVE" | "TRANSFER_IN" | "TRANSFER_OUT" | "ADJUST" | "PICK";

export interface MovementResult {
  id: string;
  locationId: string;
  productId: string;
  qty: string; // BigDecimal serialized as string
  type: MovementType;
  referenceId: string | null;
  lotNumber: string | null;
  expiryDate: string | null; // ISO date (YYYY-MM-DD)
  notes: string | null;
  createdBy: string;
  createdAt: string; // ISO instant
}

export interface MovementPageResult {
  content: MovementResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Transfer result (two legs) ────────────────────────────────────────────────

export interface TransferResult {
  referenceId: string;
  out: MovementResult;
  in: MovementResult;
}

// ── On-hand ───────────────────────────────────────────────────────────────────

export interface OnHandEntry {
  locationId: string;
  productId: string;
  qtyOnHand: string; // BigDecimal serialized as string
}

// ── Request types ─────────────────────────────────────────────────────────────

export interface ReceiveRequest {
  locationId: string;
  productId: string;
  qty: string;
  lotNumber?: string | null;
  expiryDate?: string | null;
  notes?: string | null;
}

export interface TransferRequest {
  fromLocationId: string;
  toLocationId: string;
  productId: string;
  qty: string;
  lotNumber?: string | null;
  notes?: string | null;
}

export interface AdjustRequest {
  locationId: string;
  productId: string;
  qty: string;
  notes: string;
}
