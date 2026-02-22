import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import type { ReactNode } from "react";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";
import { parseScopeFromPathname, scopeLoginPath } from "@/features/auth/shared/scope";

interface RequireAuthProps {
  children: ReactNode;
}

export function RequireAuth({ children }: RequireAuthProps) {
  const { isAuthenticated, status } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.restoringTitle")}
        description={t("authStatus.restoringDescription")}
      />
    );
  }

  if (!isAuthenticated) {
    const scope = parseScopeFromPathname(location.pathname);
    if (!scope) {
      return <Navigate to={PATHS.ROOT} replace />;
    }
    return <Navigate to={scopeLoginPath(scope)} replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
