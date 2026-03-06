import RbacRolesPage from "@/features/rbac/components/RbacRolesPage";
import type { RbacRolesPageAdapter } from "@/features/rbac/types";
import {
  createLandlordRole,
  extractRbacErrorMessage,
  getLandlordPermissions,
  getLandlordRoles,
  updateLandlordRole,
} from "@/features/landlord/api/rbacApi";

const landlordRolesAdapter: RbacRolesPageAdapter = {
  listRoles: getLandlordRoles,
  listPermissions: getLandlordPermissions,
  createRole: createLandlordRole,
  updateRole: updateLandlordRole,
  extractErrorMessage: extractRbacErrorMessage,
};

const RolesPage = () => {
  return <RbacRolesPage adapter={landlordRolesAdapter} />;
};

export default RolesPage;
