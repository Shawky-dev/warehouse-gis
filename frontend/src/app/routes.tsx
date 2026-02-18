import { Navigate } from "react-router-dom"
import { PATHS } from "@/shared/consts/paths"
import { authRoutes } from "@/features/auth/routes"
import { landlordRoutes } from "@/features/landlord/routes"
import { LandlordLayout } from "@/layouts/LandlordLayout"

export const routes = [
    {
        path: PATHS.ROOT,
        element: <Navigate to={PATHS.LANDLORD} replace />,
    },
    ...authRoutes,
    {
        element: <LandlordLayout />,
        children: landlordRoutes,
    },
]
