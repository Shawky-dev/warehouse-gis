import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
  AdjustRequest,
  InventoryListParams,
  LocationLookupPageResult,
  LocationLookupParams,
  MovementPageResult,
  MovementResult,
  StockEntry,
  ProductLookupPageResult,
  ProductLookupParams,
  ReceiveRequest,
  TransferRequest,
  TransferResult,
} from "@/features/tenant/types/inventory";

export const INVENTORY_LOOKUP_DEFAULT_SIZE = 20;
export const INVENTORY_LOOKUP_MAX_SIZE = 100;

function basePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}/inventory`;
}

function headers(tenantSlug: string): Record<string, string> {
  return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

function normalizeLookupSize(size: number | undefined): number {
  if (size === undefined || !Number.isFinite(size)) {
    return INVENTORY_LOOKUP_DEFAULT_SIZE;
  }

  const normalized = Math.floor(size);
  if (normalized < 1) {
    return 1;
  }
  if (normalized > INVENTORY_LOOKUP_MAX_SIZE) {
    return INVENTORY_LOOKUP_MAX_SIZE;
  }
  return normalized;
}

export async function getStock(
  tenantSlug: string,
  params: InventoryListParams = {}
): Promise<StockEntry[]> {
  const res = await api.get<StockEntry[]>(`${basePath(tenantSlug)}/stock`, {
    params,
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getMovements(
  tenantSlug: string,
  params: InventoryListParams = {}
): Promise<MovementPageResult> {
  const res = await api.get<MovementPageResult>(`${basePath(tenantSlug)}/movements`, {
    params: { size: 50, ...params },
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getProductLookups(
  tenantSlug: string,
  params: ProductLookupParams = {}
): Promise<ProductLookupPageResult> {
  const { size, ...restParams } = params;
  const res = await api.get<ProductLookupPageResult>(`${basePath(tenantSlug)}/lookups/products`, {
    params: { ...restParams, size: normalizeLookupSize(size) },
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getLocationLookups(
  tenantSlug: string,
  params: LocationLookupParams = {}
): Promise<LocationLookupPageResult> {
  const { size, ...restParams } = params;
  const res = await api.get<LocationLookupPageResult>(`${basePath(tenantSlug)}/lookups/locations`, {
    params: { ...restParams, size: normalizeLookupSize(size) },
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getMovementsByLocation(
  tenantSlug: string,
  locationId: string,
  page = 0,
  size = 50
): Promise<MovementPageResult> {
  return getMovements(tenantSlug, { locationId, page, size });
}

export async function getMovementsByProduct(
  tenantSlug: string,
  productId: string,
  page = 0,
  size = 50
): Promise<MovementPageResult> {
  return getMovements(tenantSlug, { productId, page, size });
}

export async function receiveStock(
  tenantSlug: string,
  payload: ReceiveRequest,
  override = false
): Promise<MovementResult> {
  const res = await api.post<MovementResult>(`${basePath(tenantSlug)}/receive`, payload, {
    headers: { ...headers(tenantSlug), ...(override ? { "X-Zone-Override": "true" } : {}) },
  });
  return res.data;
}

export async function transferStock(
  tenantSlug: string,
  payload: TransferRequest,
  override = false
): Promise<TransferResult> {
  const res = await api.post<TransferResult>(`${basePath(tenantSlug)}/transfer`, payload, {
    headers: { ...headers(tenantSlug), ...(override ? { "X-Zone-Override": "true" } : {}) },
  });
  return res.data;
}

export async function adjustStock(
  tenantSlug: string,
  payload: AdjustRequest
): Promise<MovementResult> {
  const res = await api.post<MovementResult>(`${basePath(tenantSlug)}/adjust`, payload, {
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getDocumentMovements(
  tenantSlug: string,
  docPath: string,
  docId: string
): Promise<MovementResult[]> {
  const slug = normalizeTenantSlug(tenantSlug);
  const res = await api.get<MovementResult[]>(`/${slug}/${docPath}/${docId}/movements`, {
    headers: headers(tenantSlug),
  });
  return res.data;
}

export function extractInventoryErrorMessage(error: unknown, fallback: string): string {
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
