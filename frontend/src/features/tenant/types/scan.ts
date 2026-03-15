export type ScanType =
  | "PRODUCT"
  | "LOCATION"
  | "LOT"
  | "STOCK_ROW"
  | "RECEIPT"
  | "RECEIPT_LINE"
  | "DISPATCH"
  | "COUNT_SESSION";

export interface ScanResolveResult {
  type: ScanType;
  // PRODUCT / LOT
  productId?: string;
  productSku?: string;
  productName?: string;
  trackLot?: boolean;
  trackExpiry?: boolean;
  // LOCATION
  locationId?: string;
  locationPathLabel?: string;
  locationKindName?: string;
  scanCode?: string;
  fullCode?: string;
  // LOT
  lotNumber?: string;
  // Documents
  receiptId?: string;
  dispatchId?: string;
  countSessionId?: string;
  // RECEIPT_LINE (Stock Unit)
  receiptLineId?: string;
  lineQty?: string;
  // Display
  displayLabel?: string;
}
