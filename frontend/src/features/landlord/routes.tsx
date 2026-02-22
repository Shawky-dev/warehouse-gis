import { Navigate } from "react-router-dom";
import { PATHS } from "@/shared/consts/paths";
import LandlordDashboardPage from "./dashboard/LandlordDashboardPage";
import CreateWarehousePage from "./warehouse/create/CreateWarehousePage";
import WarehouseListPage from "./warehouse/list/WarehouseListPage";
import UserPage from "./user/UserPage";
import RolesPage from "./roles/RolesPage";

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
    element: <CreateWarehousePage />,
  },
  {
    path: PATHS.LANDLORD.WAREHOUSES_LIST,
    element: <WarehouseListPage />,
  },
  {
    path: PATHS.LANDLORD.USERS,
    element: <UserPage />,
  },
  {
    path: PATHS.LANDLORD.ROLES,
    element: <RolesPage />,
  },
];
