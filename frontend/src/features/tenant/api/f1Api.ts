import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
  AislePageResult,
  AisleResult,
  BayPageResult,
  BayResult,
  BulkCreateBaysRequest,
  BulkCreateResult,
  CreateAisleRequest,
  CreateBayRequest,
  CreateLayoutRequest,
  CreateLevelRequest,
  CreateShelfRequest,
  CreateSideRequest,
  F1ErrorResponse,
  LayoutPageResult,
  LayoutResult,
  LevelListResult,
  LevelResult,
  ListAislesParams,
  ListBaysParams,
  ListLayoutsParams,
  ShelfListResult,
  ShelfResult,
  SideListResult,
  SideResult,
  UpdateAisleRequest,
  UpdateBayRequest,
  UpdateLayoutRequest,
} from "@/features/tenant/types/f1";

function tenantBasePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}`;
}

function tenantHeaders(tenantSlug: string): Record<string, string> {
  return {
    "X-TENANT-ID": normalizeTenantSlug(tenantSlug),
  };
}

// ── Layouts ───────────────────────────────────────────────────────────────────

export async function listLayouts(
  tenantSlug: string,
  params: ListLayoutsParams
): Promise<LayoutPageResult> {
  const response = await api.get<LayoutPageResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getLayout(tenantSlug: string, layoutId: string): Promise<LayoutResult> {
  const response = await api.get<LayoutResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createLayout(
  tenantSlug: string,
  payload: CreateLayoutRequest
): Promise<LayoutResult> {
  const response = await api.post<LayoutResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateLayout(
  tenantSlug: string,
  layoutId: string,
  payload: UpdateLayoutRequest
): Promise<LayoutResult> {
  const response = await api.put<LayoutResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteLayout(tenantSlug: string, layoutId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreLayout(tenantSlug: string, layoutId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteLayout(tenantSlug: string, layoutId: string): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Aisles ────────────────────────────────────────────────────────────────────

export async function listAisles(
  tenantSlug: string,
  layoutId: string,
  params: ListAislesParams
): Promise<AislePageResult> {
  const response = await api.get<AislePageResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getAisle(
  tenantSlug: string,
  layoutId: string,
  aisleId: string
): Promise<AisleResult> {
  const response = await api.get<AisleResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles/${aisleId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createAisle(
  tenantSlug: string,
  layoutId: string,
  payload: CreateAisleRequest
): Promise<AisleResult> {
  const response = await api.post<AisleResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateAisle(
  tenantSlug: string,
  layoutId: string,
  aisleId: string,
  payload: UpdateAisleRequest
): Promise<AisleResult> {
  const response = await api.put<AisleResult>(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles/${aisleId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteAisle(
  tenantSlug: string,
  layoutId: string,
  aisleId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles/${aisleId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreAisle(
  tenantSlug: string,
  layoutId: string,
  aisleId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles/${aisleId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteAisle(
  tenantSlug: string,
  layoutId: string,
  aisleId: string
): Promise<void> {
  await api.delete(
    `${tenantBasePath(tenantSlug)}/warehouse-layouts/${layoutId}/aisles/${aisleId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
}

// ── Sides ─────────────────────────────────────────────────────────────────────

export async function listSides(
  tenantSlug: string,
  aisleId: string,
  active?: boolean
): Promise<SideListResult> {
  const response = await api.get<SideListResult>(
    `${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides`,
    { params: active !== undefined ? { active } : undefined, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createSide(
  tenantSlug: string,
  aisleId: string,
  payload: CreateSideRequest
): Promise<SideResult> {
  const response = await api.post<SideResult>(
    `${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getSide(
  tenantSlug: string,
  aisleId: string,
  sideId: string
): Promise<SideResult> {
  const response = await api.get<SideResult>(
    `${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides/${sideId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteSide(
  tenantSlug: string,
  aisleId: string,
  sideId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides/${sideId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreSide(
  tenantSlug: string,
  aisleId: string,
  sideId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides/${sideId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteSide(
  tenantSlug: string,
  aisleId: string,
  sideId: string
): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/aisles/${aisleId}/sides/${sideId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Bays ──────────────────────────────────────────────────────────────────────

export async function listBays(
  tenantSlug: string,
  sideId: string,
  params: ListBaysParams
): Promise<BayPageResult> {
  const response = await api.get<BayPageResult>(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createBay(
  tenantSlug: string,
  sideId: string,
  payload: CreateBayRequest
): Promise<BayResult> {
  const response = await api.post<BayResult>(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getBay(
  tenantSlug: string,
  sideId: string,
  bayId: string
): Promise<BayResult> {
  const response = await api.get<BayResult>(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/${bayId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateBay(
  tenantSlug: string,
  sideId: string,
  bayId: string,
  payload: UpdateBayRequest
): Promise<BayResult> {
  const response = await api.put<BayResult>(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/${bayId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteBay(
  tenantSlug: string,
  sideId: string,
  bayId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/${bayId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreBay(
  tenantSlug: string,
  sideId: string,
  bayId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/${bayId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteBay(
  tenantSlug: string,
  sideId: string,
  bayId: string
): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/${bayId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function createBaysBulk(
  tenantSlug: string,
  sideId: string,
  payload: BulkCreateBaysRequest
): Promise<BulkCreateResult> {
  const response = await api.post<BulkCreateResult>(
    `${tenantBasePath(tenantSlug)}/sides/${sideId}/bays/bulk`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

// ── Levels ────────────────────────────────────────────────────────────────────

export async function listLevels(
  tenantSlug: string,
  bayId: string,
  active?: boolean
): Promise<LevelListResult> {
  const response = await api.get<LevelListResult>(
    `${tenantBasePath(tenantSlug)}/bays/${bayId}/levels`,
    { params: active !== undefined ? { active } : undefined, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createLevel(
  tenantSlug: string,
  bayId: string,
  payload: CreateLevelRequest
): Promise<LevelResult> {
  const response = await api.post<LevelResult>(
    `${tenantBasePath(tenantSlug)}/bays/${bayId}/levels`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getLevel(
  tenantSlug: string,
  bayId: string,
  levelId: string
): Promise<LevelResult> {
  const response = await api.get<LevelResult>(
    `${tenantBasePath(tenantSlug)}/bays/${bayId}/levels/${levelId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteLevel(
  tenantSlug: string,
  bayId: string,
  levelId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/bays/${bayId}/levels/${levelId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreLevel(
  tenantSlug: string,
  bayId: string,
  levelId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/bays/${bayId}/levels/${levelId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteLevel(
  tenantSlug: string,
  bayId: string,
  levelId: string
): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/bays/${bayId}/levels/${levelId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Shelves ───────────────────────────────────────────────────────────────────

export async function listShelves(
  tenantSlug: string,
  levelId: string,
  active?: boolean
): Promise<ShelfListResult> {
  const response = await api.get<ShelfListResult>(
    `${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves`,
    { params: active !== undefined ? { active } : undefined, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createShelf(
  tenantSlug: string,
  levelId: string,
  payload: CreateShelfRequest
): Promise<ShelfResult> {
  const response = await api.post<ShelfResult>(
    `${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function getShelf(
  tenantSlug: string,
  levelId: string,
  shelfId: string
): Promise<ShelfResult> {
  const response = await api.get<ShelfResult>(
    `${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves/${shelfId}`,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteShelf(
  tenantSlug: string,
  levelId: string,
  shelfId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves/${shelfId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreShelf(
  tenantSlug: string,
  levelId: string,
  shelfId: string
): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves/${shelfId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteShelf(
  tenantSlug: string,
  levelId: string,
  shelfId: string
): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/bay-levels/${levelId}/shelves/${shelfId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Error extraction ──────────────────────────────────────────────────────────

export function extractF1ErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as F1ErrorResponse | string | undefined;
  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (data && typeof data === "object" && "message" in data) {
    const message = (data as F1ErrorResponse).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return null;
}
