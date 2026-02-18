import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

interface RequireRoleProps {
  role: string;
  children: ReactNode;
}

export function RequireRole({ role, children }: RequireRoleProps) {
  const { status, hasRole } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen title="Loading permissions" description="Verifying your access level." />;
  }

  if (!hasRole(role)) {
    return <Navigate to={PATHS.LOGIN} replace />;
  }

  return <>{children}</>;
}
