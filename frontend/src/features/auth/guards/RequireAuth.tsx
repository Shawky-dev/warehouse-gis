import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

interface RequireAuthProps {
  children: ReactNode;
}

export function RequireAuth({ children }: RequireAuthProps) {
  const { isAuthenticated, status } = useAuth();
  const location = useLocation();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen title="Restoring session" description="Checking your authentication state." />;
  }

  if (!isAuthenticated) {
    return <Navigate to={PATHS.LOGIN} replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
