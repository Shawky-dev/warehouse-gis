import { useAuth } from "@/features/auth/context/AuthContext";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { AccessDeniedScreen } from "@/features/auth/components/AccessDeniedScreen";

interface RequireRoleProps {
  role: string;
  children: ReactNode;
}

export function RequireRole({ role, children }: RequireRoleProps) {
  const { status, hasRole } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen variant="permissions" />;
  }

  if (!hasRole(role)) {
    return <AccessDeniedScreen />;
  }

  return <>{children}</>;
}
