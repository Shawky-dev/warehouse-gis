import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";
import { parseScopeFromPathname, scopeLoginPath } from "@/features/auth/shared/scope";

interface RequireRoleProps {
  role: string;
  children: ReactNode;
}

export function RequireRole({ role, children }: RequireRoleProps) {
  const { status, hasRole } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.permissionsTitle")}
        description={t("authStatus.permissionsDescription")}
      />
    );
  }

  if (!hasRole(role)) {
    const scope = parseScopeFromPathname(location.pathname);
    if (!scope) {
      return <Navigate to={PATHS.ROOT} replace />;
    }
    return <Navigate to={scopeLoginPath(scope)} replace />;
  }

  return <>{children}</>;
}
