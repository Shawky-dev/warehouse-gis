export interface RbacUserSummary {
  id: string;
  email: string;
  role: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface RbacUsersPage {
  content: RbacUserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListRbacUsersParams {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}

export interface CreateRbacUserRequest {
  email: string;
  password: string;
  role: string;
}

export interface UpdateRbacUserRequest {
  email: string;
  role: string;
}

export interface ResetRbacUserPasswordRequest {
  newPassword: string;
}

export interface RbacRoleDetails {
  code: string;
  name: string;
  description: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface RbacPermissionOption {
  code: string;
  description: string | null;
}

export interface CreateRbacRoleRequest {
  code: string;
  name: string;
  description?: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface UpdateRbacRoleRequest {
  name: string;
  description?: string | null;
  permissionCodes: string[];
  locked: boolean;
}

export interface RbacErrorResponse {
  code: string;
  message: string;
}

export interface RbacUsersPageAdapter {
  listUsers: (params: ListRbacUsersParams) => Promise<RbacUsersPage>;
  createUser: (payload: CreateRbacUserRequest) => Promise<RbacUserSummary>;
  updateUser: (userId: string, payload: UpdateRbacUserRequest) => Promise<RbacUserSummary>;
  resetUserPassword: (userId: string, payload: ResetRbacUserPasswordRequest) => Promise<void>;
  deactivateUser: (userId: string) => Promise<void>;
  reactivateUser: (userId: string) => Promise<void>;
  listRoles: () => Promise<RbacRoleDetails[]>;
  extractErrorMessage: (error: unknown) => string | null;
}

export interface RbacRolesPageAdapter {
  listRoles: () => Promise<RbacRoleDetails[]>;
  listPermissions: () => Promise<RbacPermissionOption[]>;
  createRole: (payload: CreateRbacRoleRequest) => Promise<RbacRoleDetails>;
  updateRole: (roleCode: string, payload: UpdateRbacRoleRequest) => Promise<RbacRoleDetails>;
  extractErrorMessage: (error: unknown) => string | null;
}

export interface RbacPermissionSet {
  USERS_CREATE: string;
  USERS_EDIT: string;
  USERS_RESET_PASSWORD: string;
  USERS_DEACTIVATE: string;
  USERS_REACTIVATE: string;
}
