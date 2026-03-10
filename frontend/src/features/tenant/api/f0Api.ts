import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";
import type {
  AuditPageResult,
  CategoryPageResult,
  CategoryResult,
  CreateCategoryRequest,
  CreateProductRequest,
  CreateSupplierRequest,
  CreateUomRequest,
  F0ErrorResponse,
  ListAuditLogsParams,
  ListCategoriesParams,
  ListProductsParams,
  ListSuppliersParams,
  ListUomsParams,
  ProductPageResult,
  ProductResult,
  SupplierPageResult,
  SupplierResult,
  UomPageResult,
  UomResult,
  UpdateCategoryRequest,
  UpdateProductRequest,
  UpdateSupplierRequest,
  UpdateUomRequest,
} from "@/features/tenant/types/f0";

function tenantBasePath(tenantSlug: string): string {
  return `/${normalizeTenantSlug(tenantSlug)}`;
}

function tenantHeaders(tenantSlug: string): Record<string, string> {
  return {
    "X-TENANT-ID": normalizeTenantSlug(tenantSlug),
  };
}

// ── UOM ───────────────────────────────────────────────────────────────────────

export async function listUoms(tenantSlug: string, params: ListUomsParams): Promise<UomPageResult> {
  const response = await api.get<UomPageResult>(`${tenantBasePath(tenantSlug)}/uoms`, {
    params,
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function createUom(tenantSlug: string, payload: CreateUomRequest): Promise<UomResult> {
  const response = await api.post<UomResult>(`${tenantBasePath(tenantSlug)}/uoms`, payload, {
    headers: tenantHeaders(tenantSlug),
  });
  return response.data;
}

export async function updateUom(
  tenantSlug: string,
  uomId: string,
  payload: UpdateUomRequest
): Promise<UomResult> {
  const response = await api.put<UomResult>(
    `${tenantBasePath(tenantSlug)}/uoms/${uomId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteUom(tenantSlug: string, uomId: string): Promise<void> {
  await api.post(`${tenantBasePath(tenantSlug)}/uoms/${uomId}/soft-delete`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function restoreUom(tenantSlug: string, uomId: string): Promise<void> {
  await api.post(`${tenantBasePath(tenantSlug)}/uoms/${uomId}/restore`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function hardDeleteUom(tenantSlug: string, uomId: string): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/uoms/${uomId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Supplier ──────────────────────────────────────────────────────────────────

export async function listSuppliers(
  tenantSlug: string,
  params: ListSuppliersParams
): Promise<SupplierPageResult> {
  const response = await api.get<SupplierPageResult>(
    `${tenantBasePath(tenantSlug)}/suppliers`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createSupplier(
  tenantSlug: string,
  payload: CreateSupplierRequest
): Promise<SupplierResult> {
  const response = await api.post<SupplierResult>(
    `${tenantBasePath(tenantSlug)}/suppliers`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateSupplier(
  tenantSlug: string,
  supplierId: string,
  payload: UpdateSupplierRequest
): Promise<SupplierResult> {
  const response = await api.put<SupplierResult>(
    `${tenantBasePath(tenantSlug)}/suppliers/${supplierId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteSupplier(tenantSlug: string, supplierId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/suppliers/${supplierId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreSupplier(tenantSlug: string, supplierId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/suppliers/${supplierId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteSupplier(tenantSlug: string, supplierId: string): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/suppliers/${supplierId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Product ───────────────────────────────────────────────────────────────────

export async function listProducts(
  tenantSlug: string,
  params: ListProductsParams
): Promise<ProductPageResult> {
  const response = await api.get<ProductPageResult>(
    `${tenantBasePath(tenantSlug)}/products`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createProduct(
  tenantSlug: string,
  payload: CreateProductRequest
): Promise<ProductResult> {
  const response = await api.post<ProductResult>(
    `${tenantBasePath(tenantSlug)}/products`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateProduct(
  tenantSlug: string,
  productId: string,
  payload: UpdateProductRequest
): Promise<ProductResult> {
  const response = await api.put<ProductResult>(
    `${tenantBasePath(tenantSlug)}/products/${productId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteProduct(tenantSlug: string, productId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/products/${productId}/soft-delete`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function restoreProduct(tenantSlug: string, productId: string): Promise<void> {
  await api.post(
    `${tenantBasePath(tenantSlug)}/products/${productId}/restore`,
    undefined,
    { headers: tenantHeaders(tenantSlug) }
  );
}

export async function hardDeleteProduct(tenantSlug: string, productId: string): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/products/${productId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Category ──────────────────────────────────────────────────────────────────

export async function listCategories(
  tenantSlug: string,
  params: ListCategoriesParams
): Promise<CategoryPageResult> {
  const response = await api.get<CategoryPageResult>(
    `${tenantBasePath(tenantSlug)}/categories`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function createCategory(
  tenantSlug: string,
  payload: CreateCategoryRequest
): Promise<CategoryResult> {
  const response = await api.post<CategoryResult>(
    `${tenantBasePath(tenantSlug)}/categories`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function updateCategory(
  tenantSlug: string,
  categoryId: string,
  payload: UpdateCategoryRequest
): Promise<CategoryResult> {
  const response = await api.put<CategoryResult>(
    `${tenantBasePath(tenantSlug)}/categories/${categoryId}`,
    payload,
    { headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

export async function softDeleteCategory(tenantSlug: string, categoryId: string): Promise<void> {
  await api.post(`${tenantBasePath(tenantSlug)}/categories/${categoryId}/soft-delete`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function restoreCategory(tenantSlug: string, categoryId: string): Promise<void> {
  await api.post(`${tenantBasePath(tenantSlug)}/categories/${categoryId}/restore`, undefined, {
    headers: tenantHeaders(tenantSlug),
  });
}

export async function hardDeleteCategory(tenantSlug: string, categoryId: string): Promise<void> {
  await api.delete(`${tenantBasePath(tenantSlug)}/categories/${categoryId}`, {
    headers: tenantHeaders(tenantSlug),
  });
}

// ── Audit Logs ────────────────────────────────────────────────────────────────

export async function listAuditLogs(
  tenantSlug: string,
  params: ListAuditLogsParams
): Promise<AuditPageResult> {
  const response = await api.get<AuditPageResult>(
    `${tenantBasePath(tenantSlug)}/audit-logs`,
    { params, headers: tenantHeaders(tenantSlug) }
  );
  return response.data;
}

// ── Error extraction ──────────────────────────────────────────────────────────

export function extractF0ErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as F0ErrorResponse | string | undefined;
  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (data && typeof data === "object" && "message" in data) {
    const message = (data as F0ErrorResponse).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return null;
}
