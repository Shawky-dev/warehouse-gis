import { PATHS } from "@/shared/consts/paths";
import LoginPage from "./login/LoginPage";
import { PublicOnly } from "@/features/auth/guards/PublicOnly";

export const authRoutes = [
  {
    path: PATHS.LOGIN,
    element: (
      <PublicOnly>
        <LoginPage />
      </PublicOnly>
    ),
  },
];
