import { PATHS } from "@/shared/consts/paths"
import LandlordDashboardPage from "./dashboard/LandlordDashboardPage"

export const landlordRoutes = [
    {
        path: PATHS.LANDLORD,
        element: <LandlordDashboardPage />,
    },
]
