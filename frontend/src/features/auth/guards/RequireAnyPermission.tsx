import type { ReactNode } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { AccessDeniedScreen } from "@/features/auth/components/AccessDeniedScreen";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

interface RequireAnyPermissionProps {
  permissions: string[];
  children: ReactNode;
}

export function RequireAnyPermission({ permissions, children }: RequireAnyPermissionProps) {
  const { status, hasAnyPermission } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen variant="permissions" />;
  }

  if (!hasAnyPermission(permissions)) {
    return <AccessDeniedScreen />;
  }

  return <>{children}</>;
}
