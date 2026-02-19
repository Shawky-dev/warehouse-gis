import { PATHS } from "@/shared/consts/paths"
import LandlordDashboardPage from "./dashboard/LandlordDashboardPage"
import WarehousesPage from "./warehouse/WarehousesPage"
import UserPage from "./user/UserPage"
import RolesPage from "./roles/RolesPage"

export const landlordRoutes = [
    {
        path: PATHS.LANDLORD.ROOT,
        element: <LandlordDashboardPage />,
    },
    {
        path : PATHS.LANDLORD.WAREHOUSES,
        element: <WarehousesPage/>
    },
    {
        path: PATHS.LANDLORD.USERS,
        element: <UserPage />
    },
    {
        path: PATHS.LANDLORD.ROLES,
        element: <RolesPage />
    }
    
]
