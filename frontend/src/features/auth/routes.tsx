import { PATHS } from "@/shared/consts/paths";
import LandlordLoginPage from "@/features/auth/landlord/login/LandlordLoginPage";
import TenantLoginPage from "@/features/auth/tenant/login/TenantLoginPage";
import { PublicOnly } from "@/features/auth/guards/PublicOnly";

export const authRoutes = [
  {
    path: PATHS.LANDLORD.AUTH_LOGIN,
    element: (
      <PublicOnly>
        <LandlordLoginPage />
      </PublicOnly>
    ),
  },
  {
    path: PATHS.TENANT.AUTH_LOGIN_PATTERN,
    element: (
      <PublicOnly>
        <TenantLoginPage />
      </PublicOnly>
    ),
  },
];
