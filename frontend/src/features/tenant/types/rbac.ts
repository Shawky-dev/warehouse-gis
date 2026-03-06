import type {
  CreateRbacRoleRequest,
  CreateRbacUserRequest,
  ListRbacUsersParams,
  RbacPermissionOption,
  RbacRoleDetails,
  RbacUserSummary,
  RbacUsersPage,
  UpdateRbacRoleRequest,
  UpdateRbacUserRequest,
  ResetRbacUserPasswordRequest,
  RbacErrorResponse,
} from "@/features/rbac/types";

export type TenantUserSummary = RbacUserSummary;
export type TenantUsersPage = RbacUsersPage;
export type ListTenantUsersParams = ListRbacUsersParams;
export type CreateTenantUserRequest = CreateRbacUserRequest;
export type UpdateTenantUserRequest = UpdateRbacUserRequest;
export type ResetTenantUserPasswordRequest = ResetRbacUserPasswordRequest;

export type TenantRoleDetails = RbacRoleDetails;
export type TenantPermissionOption = RbacPermissionOption;
export type CreateTenantRoleRequest = CreateRbacRoleRequest;
export type UpdateTenantRoleRequest = UpdateRbacRoleRequest;

export type TenantRbacErrorResponse = RbacErrorResponse;
