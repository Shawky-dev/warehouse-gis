import { Navigate } from "react-router-dom";
import { PATHS } from "@/shared/consts/paths";
import LandlordDashboardPage from "./dashboard/LandlordDashboardPage";
import WarehousesPage from "./warehouse/WarehousesPage";
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
    element: <WarehousesPage mode="create" />,
  },
  {
    path: PATHS.LANDLORD.WAREHOUSES_LIST,
    element: <WarehousesPage mode="list" />,
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
