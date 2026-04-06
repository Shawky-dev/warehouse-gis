export type MovementType = "RECEIVE" | "TRANSFER_IN" | "TRANSFER_OUT" | "ADJUST" | "PICK";

export interface ProductLookupItem {
  id: string;
  sku: string;
  name: string;
  baseUomCode: string;
  trackLot: boolean;
  trackExpiry: boolean;
  active: boolean;
}

export interface ProductLookupPageResult {
  content: ProductLookupItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LocationLookupItem {
  id: string;
  layoutId: string;
  layoutName: string | null;
  label: string;
  pathLabel: string;
  identifier: string | null;
  side: string | null;
  locationKind: string | null;
  scanCode: string | null;
}

export interface LocationLookupPageResult {
  content: LocationLookupItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StockEntry {
  locationId: string;
  productId: string;
  lotNumber: string | null;
  qtyStock: string;
  locationLabel: string | null;
  locationPathLabel: string | null;
  layoutId: string | null;
  layoutName: string | null;
  locationIdentifier: string | null;
  locationSide: string | null;
  productSku: string | null;
  productName: string | null;
  baseUomCode: string | null;
  trackLot: boolean | null;
  trackExpiry: boolean | null;
  stockRowQrData: string | null;
}

export interface MovementResult {
  id: string;
  locationId: string;
  productId: string;
  qty: string;
  type: MovementType;
  referenceId: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  locationLabel: string | null;
  locationPathLabel: string | null;
  layoutId: string | null;
  layoutName: string | null;
  locationIdentifier: string | null;
  locationSide: string | null;
  productSku: string | null;
  productName: string | null;
  baseUomCode: string | null;
  trackLot: boolean | null;
  trackExpiry: boolean | null;
  counterpartLocationId: string | null;
  counterpartLocationLabel: string | null;
  counterpartLocationPathLabel: string | null;
  sourceDocumentId: string | null;
  reasonCode: string | null;
}

export interface MovementPageResult {
  content: MovementResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TransferResult {
  referenceId: string;
  out: MovementResult;
  in: MovementResult;
}

export interface InventoryListParams {
  productId?: string;
  locationId?: string;
  locationKind?: string;
  sourceDocumentId?: string;
  movementType?: MovementType;
  page?: number;
  size?: number;
}

export interface ProductLookupParams {
  search?: string;
  page?: number;
  size?: number;
}

export interface LocationLookupParams {
  search?: string;
  page?: number;
  size?: number;
}

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
  reasonCode?: string | null;
}

// ── Storage rule violation types ──────────────────────────────────────────────

export interface ViolatedAreaSummary {
  id: string;
  name: string;
  type: string;
}

export interface ZoneSummary {
  id: string;
  name: string;
}

export interface HazardTypeSummary {
  id: string;
  code: string;
  displayName: string;
}

export interface ZoneTypeSummary {
  id: string;
  code: string;
  displayName: string;
}

export interface HazardBufferViolation {
  ruleType: "HAZARD_BUFFER";
  violationAction: "BLOCK";
  error: string;
  message: string;
  violatedArea: ViolatedAreaSummary;
  restrictedHazardTypes: HazardTypeSummary[];
}

export interface ZoneRuleViolation {
  ruleType: "ZONE";
  violationAction: "BLOCK" | "WARN";
  error: string;
  message: string;
  violatedArea: ViolatedAreaSummary;
  suggestedZones: ZoneSummary[];
}

export interface RequiredZoneViolation {
  ruleType: "REQUIRED_ZONE";
  violationAction: "WARN";
  error: string;
  message: string;
  violatedArea?: ViolatedAreaSummary;
  requiredZoneType: ZoneTypeSummary;
  suggestedZones: ZoneSummary[];
}

export type StorageRuleViolation =
  | HazardBufferViolation
  | ZoneRuleViolation
  | RequiredZoneViolation;
