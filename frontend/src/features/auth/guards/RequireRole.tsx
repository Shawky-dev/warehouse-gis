import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";

interface RequireRoleProps {
  role: string;
  children: ReactNode;
}

export function RequireRole({ role, children }: RequireRoleProps) {
  const { status, hasRole } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.permissionsTitle")}
        description={t("authStatus.permissionsDescription")}
      />
    );
  }

  if (!hasRole(role)) {
    return <Navigate to={PATHS.LOGIN} replace />;
  }

  return <>{children}</>;
}
