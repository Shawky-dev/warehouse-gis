export interface UomResult {
  id: string;
  code: string;
  name: string;
  symbol: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface UomPageResult {
  content: UomResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListUomsParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateUomRequest {
  code: string;
  name: string;
  symbol?: string | null;
}

export interface UpdateUomRequest {
  code: string;
  name: string;
  symbol?: string | null;
}

export interface SupplierResult {
  id: string;
  code: string;
  name: string;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface SupplierPageResult {
  content: SupplierResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListSuppliersParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateSupplierRequest {
  code: string;
  name: string;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  notes?: string | null;
}

export interface UpdateSupplierRequest {
  code: string;
  name: string;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  notes?: string | null;
}

export interface ProductSupplierResult {
  supplierId: string;
  supplierCode: string;
  supplierName: string;
  primary: boolean;
}

export interface ProductResult {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  baseUomId: string;
  baseUomCode: string;
  baseUomName: string;
  trackLot: boolean;
  trackExpiry: boolean;
  active: boolean;
  suppliers: ProductSupplierResult[];
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface ProductPageResult {
  content: ProductResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListProductsParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
  baseUomId?: string;
  supplierId?: string;
}

export interface CreateProductRequest {
  sku: string;
  name: string;
  description?: string | null;
  baseUomId: string;
  trackLot?: boolean | null;
  trackExpiry?: boolean | null;
  supplierIds?: string[];
  primarySupplierId?: string | null;
}

export interface UpdateProductRequest {
  sku: string;
  name: string;
  description?: string | null;
  baseUomId: string;
  trackLot?: boolean | null;
  trackExpiry?: boolean | null;
  supplierIds?: string[];
  primarySupplierId?: string | null;
}

export interface AuditLogItem {
  id: string;
  occurredAt: string;
  actorEmail: string;
  actorRoles: string | null;
  action: string;
  entityType: string;
  entityId: string;
  beforeState: string | null;
  afterState: string | null;
  tenantId: string;
  requestPath: string | null;
  requestMethod: string | null;
}

export interface AuditPageResult {
  content: AuditLogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListAuditLogsParams {
  page?: number;
  size?: number;
  actorEmail?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  fromDate?: string;
  toDate?: string;
}

export interface F0ErrorResponse {
  code: string;
  message: string;
}
