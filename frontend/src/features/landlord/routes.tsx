import { Navigate } from "react-router-dom";
import { PATHS } from "@/shared/consts/paths";
import LandlordDashboardPage from "./dashboard/LandlordDashboardPage";
import CreateWarehousePage from "./warehouse/create/CreateWarehousePage";
import WarehouseListPage from "./warehouse/list/WarehouseListPage";
import UserPage from "./user/UserPage";
import RolesPage from "./roles/RolesPage";
import { RequirePermission } from "@/features/auth/guards/RequirePermission";
import { LANDLORD_PERMISSIONS } from "@/features/auth/shared/permissions";

export const landlordRoutes = [
  {
    path: PATHS.LANDLORD.ROOT,
    element: <LandlordDashboardPage />,
  },
  {
    path: PATHS.LANDLORD.WAREHOUSES,
    element: <Navigate to={PATHS.LANDLORD.WAREHOUSES_CREATE} replace />,
  },
  {
    path: PATHS.LANDLORD.WAREHOUSES_CREATE,
    element: (
      <RequirePermission permission={LANDLORD_PERMISSIONS.TENANTS_CREATE}>
        <CreateWarehousePage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.LANDLORD.WAREHOUSES_LIST,
    element: (
      <RequirePermission permission={LANDLORD_PERMISSIONS.TENANTS_VIEW}>
        <WarehouseListPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.LANDLORD.USERS,
    element: (
      <RequirePermission permission={LANDLORD_PERMISSIONS.USERS_VIEW}>
        <UserPage />
      </RequirePermission>
    ),
  },
  {
    path: PATHS.LANDLORD.ROLES,
    element: (
      <RequirePermission permission={LANDLORD_PERMISSIONS.ROLES_EDIT}>
        <RolesPage />
      </RequirePermission>
    ),
  },
];
