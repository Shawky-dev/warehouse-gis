import { authRoutes } from "@/features/auth/routes";
import { landlordRoutes } from "@/features/landlord/routes";
import { tenantRoutes } from "@/features/tenant/routes";
import { LandlordLayout } from "@/layouts/LandlordLayout";
import { TenantLayout } from "@/layouts/TenantLayout";
import { RequireAuth } from "@/features/auth/guards/RequireAuth";
import ScopeLandingPage from "@/features/landing/ScopeLandingPage";
import { PATHS } from "@/shared/consts/paths";
import { Navigate } from "react-router-dom";

export const routes = [
  {
    path: PATHS.ROOT,
    element: <ScopeLandingPage />,
  },
  ...authRoutes,
  {
    element: (
      <RequireAuth>
        <LandlordLayout />
      </RequireAuth>
    ),
    children: landlordRoutes,
  },
  {
    element: (
      <RequireAuth>
        <TenantLayout />
      </RequireAuth>
    ),
    children: tenantRoutes,
  },
  {
    path: "*",
    element: <Navigate to={PATHS.ROOT} replace />,
  },
];
