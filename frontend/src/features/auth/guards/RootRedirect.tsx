import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

export function RootRedirect() {
  const { status, isAuthenticated, hasRole } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen title="Loading workspace" description="Preparing your initial route." />;
  }

  if (isAuthenticated && hasRole("ROLE_ADMIN")) {
    return <Navigate to={PATHS.LANDLORD.ROOT} replace />;
  }

  return <Navigate to={PATHS.LOGIN} replace />;
}
