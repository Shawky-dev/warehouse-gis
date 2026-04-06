import type { ReactNode } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { AccessDeniedScreen } from "@/features/auth/components/AccessDeniedScreen";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

interface RequirePermissionProps {
  permission: string;
  children: ReactNode;
}

export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { status, hasPermission } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen variant="permissions" />;
  }

  if (!hasPermission(permission)) {
    return <AccessDeniedScreen />;
  }

  return <>{children}</>;
}
