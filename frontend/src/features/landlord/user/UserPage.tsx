import RbacUsersPage from "@/features/rbac/components/RbacUsersPage";
import type { RbacUsersPageAdapter } from "@/features/rbac/types";
import {
  createLandlordUser,
  deactivateLandlordUser,
  extractRbacErrorMessage,
  getLandlordRoles,
  getLandlordUsers,
  reactivateLandlordUser,
  resetLandlordUserPassword,
  updateLandlordUser,
} from "@/features/landlord/api/rbacApi";
import { LANDLORD_PERMISSIONS } from "@/features/auth/shared/permissions";

const landlordUsersAdapter: RbacUsersPageAdapter = {
  listUsers: getLandlordUsers,
  createUser: createLandlordUser,
  updateUser: updateLandlordUser,
  resetUserPassword: resetLandlordUserPassword,
  deactivateUser: deactivateLandlordUser,
  reactivateUser: reactivateLandlordUser,
  listRoles: getLandlordRoles,
  extractErrorMessage: extractRbacErrorMessage,
};

const UserPage = () => {
  return <RbacUsersPage adapter={landlordUsersAdapter} permissions={LANDLORD_PERMISSIONS} />;
};

export default UserPage;
