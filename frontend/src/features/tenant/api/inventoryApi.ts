import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
  AdjustRequest,
  MovementPageResult,
  MovementResult,
  OnHandEntry,
  ReceiveRequest,
  TransferRequest,
  TransferResult,
} from "@/features/tenant/types/inventory";

function basePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}/inventory`;
}

function headers(tenantSlug: string): Record<string, string> {
  return { "X-TENANT-ID": normalizeTenantSlug(tenantSlug) };
}

// ── On-hand ───────────────────────────────────────────────────────────────────

export async function getAllOnHand(tenantSlug: string): Promise<OnHandEntry[]> {
  const res = await api.get<OnHandEntry[]>(`${basePath(tenantSlug)}/on-hand`, {
    headers: headers(tenantSlug),
  });
  return res.data;
}

export async function getOnHandByLocation(
  tenantSlug: string,
  locationId: string
): Promise<OnHandEntry[]> {
  const res = await api.get<OnHandEntry[]>(
    `${basePath(tenantSlug)}/on-hand/by-location/${locationId}`,
    { headers: headers(tenantSlug) }
  );
  return res.data;
}

export async function getOnHandByProduct(
  tenantSlug: string,
  productId: string
): Promise<OnHandEntry[]> {
  const res = await api.get<OnHandEntry[]>(
    `${basePath(tenantSlug)}/on-hand/by-product/${productId}`,
    { headers: headers(tenantSlug) }
  );
  return res.data;
}

// ── Movement history ──────────────────────────────────────────────────────────

export async function getMovementsByLocation(
  tenantSlug: string,
  locationId: string,
  page = 0,
  size = 50
): Promise<MovementPageResult> {
  const res = await api.get<MovementPageResult>(
    `${basePath(tenantSlug)}/movements/by-location/${locationId}`,
    { params: { page, size }, headers: headers(tenantSlug) }
  );
  return res.data;
}

export async function getMovementsByProduct(
  tenantSlug: string,
  productId: string,
  page = 0,
  size = 50
): Promise<MovementPageResult> {
  const res = await api.get<MovementPageResult>(
    `${basePath(tenantSlug)}/movements/by-product/${productId}`,
    { params: { page, size }, headers: headers(tenantSlug) }
  );
  return res.data;
}

// ── Operations ────────────────────────────────────────────────────────────────

export async function receiveStock(
  tenantSlug: string,
  payload: ReceiveRequest
): Promise<MovementResult> {
  const res = await api.post<MovementResult>(
    `${basePath(tenantSlug)}/receive`,
    payload,
    { headers: headers(tenantSlug) }
  );
  return res.data;
}

export async function transferStock(
  tenantSlug: string,
  payload: TransferRequest
): Promise<TransferResult> {
  const res = await api.post<TransferResult>(
    `${basePath(tenantSlug)}/transfer`,
    payload,
    { headers: headers(tenantSlug) }
  );
  return res.data;
}

export async function adjustStock(
  tenantSlug: string,
  payload: AdjustRequest
): Promise<MovementResult> {
  const res = await api.post<MovementResult>(
    `${basePath(tenantSlug)}/adjust`,
    payload,
    { headers: headers(tenantSlug) }
  );
  return res.data;
}

// ── Error helper ──────────────────────────────────────────────────────────────

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
