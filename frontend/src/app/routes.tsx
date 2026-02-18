import { authRoutes } from "@/features/auth/routes";
import { landlordRoutes } from "@/features/landlord/routes";
import { LandlordLayout } from "@/layouts/LandlordLayout";
import { RootRedirect } from "@/features/auth/guards/RootRedirect";
import { RequireAuth } from "@/features/auth/guards/RequireAuth";
import { RequireRole } from "@/features/auth/guards/RequireRole";
import { PATHS } from "@/shared/consts/paths";

export const routes = [
  {
    path: PATHS.ROOT,
    element: <RootRedirect />,
  },
  ...authRoutes,
  {
    element: (
      <RequireAuth>
        <RequireRole role="ROLE_ADMIN">
          <LandlordLayout />
        </RequireRole>
      </RequireAuth>
    ),
    children: landlordRoutes,
  },
];
