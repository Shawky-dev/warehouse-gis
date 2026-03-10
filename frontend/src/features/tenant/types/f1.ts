export interface LayoutResult {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface LayoutPageResult {
  content: LayoutResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListLayoutsParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateLayoutRequest {
  code: string;
  name: string;
  description?: string | null;
}

export interface UpdateLayoutRequest {
  code: string;
  name: string;
  description?: string | null;
}

export interface AisleResult {
  id: string;
  layoutId: string;
  layoutCode: string;
  code: string;
  name: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface AislePageResult {
  content: AisleResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListAislesParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateAisleRequest {
  code: string;
  name?: string | null;
}

export interface UpdateAisleRequest {
  code: string;
  name?: string | null;
}

export interface SideResult {
  id: string;
  aisleId: string;
  aisleCode: string;
  layoutCode: string;
  side: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface SideListResult {
  content: SideResult[];
}

export interface CreateSideRequest {
  side: string;
}

export interface BayResult {
  id: string;
  sideId: string;
  side: string;
  bayCode: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface BayPageResult {
  content: BayResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListBaysParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateBayRequest {
  code: string;
}

export interface UpdateBayRequest {
  code: string;
}

export interface BulkCreateBaysRequest {
  codes: string[];
  levelsPerBay: number;
  shelvesPerLevel: number;
}

export interface BulkCreateResult {
  locationCodes: string[];
}

export interface LevelResult {
  id: string;
  bayId: string;
  bayCode: string;
  levelNum: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface LevelListResult {
  content: LevelResult[];
}

export interface CreateLevelRequest {
  levelNum: number;
}

export interface ShelfResult {
  id: string;
  levelId: string;
  shelfNum: number;
  locationCode: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface ShelfListResult {
  content: ShelfResult[];
}

export interface CreateShelfRequest {
  shelfNum: number;
}

export interface WarehouseAncestorState {
  layout?: {
    id: string;
    code: string;
  };
  aisle?: {
    id: string;
    code: string;
  };
  side?: {
    id: string;
    side: string;
  };
  bay?: {
    id: string;
    code: string;
  };
  level?: {
    id: string;
    levelNum: number;
  };
}

export interface F1ErrorResponse {
  code: string;
  message: string;
}
