export interface LandlordUserSummary {
  id: string;
  email: string;
  role: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface LandlordUsersPage {
  content: LandlordUserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListLandlordUsersParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateLandlordUserRequest {
  email: string;
  password: string;
  role: string;
}

export interface UpdateLandlordUserRequest {
  email: string;
  role: string;
}

export interface ResetLandlordUserPasswordRequest {
  newPassword: string;
}

export interface LandlordRoleDetails {
  code: string;
  name: string;
  description: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface LandlordPermissionOption {
  code: string;
  description: string | null;
}

export interface CreateLandlordRoleRequest {
  code: string;
  name: string;
  description?: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface UpdateLandlordRoleRequest {
  name: string;
  description?: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface LandlordRbacErrorResponse {
  code: string;
  message: string;
}
