import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";
import { parseScopeFromPathname, scopeRootPath } from "@/features/auth/shared/scope";

interface PublicOnlyProps {
  children: ReactNode;
}

export function PublicOnly({ children }: PublicOnlyProps) {
  const { status, isAuthenticated } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.preparingTitle")}
        description={t("authStatus.preparingDescription")}
      />
    );
  }

  if (isAuthenticated) {
    const scope = parseScopeFromPathname(location.pathname);
    if (!scope) {
      return <Navigate to={PATHS.ROOT} replace />;
    }
    return <Navigate to={scopeRootPath(scope)} replace />;
  }

  return <>{children}</>;
}
