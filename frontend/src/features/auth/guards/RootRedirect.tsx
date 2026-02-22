import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";
import { scopeRootPath } from "@/features/auth/shared/scope";

export function RootRedirect() {
  const { status, isAuthenticated, hasRole, scope } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.workspaceTitle")}
        description={t("authStatus.workspaceDescription")}
      />
    );
  }

  if (isAuthenticated && scope) {
    return <Navigate to={scopeRootPath(scope)} replace />;
  }

  if (hasRole("ROLE_ADMIN")) {
    return <Navigate to={PATHS.LANDLORD.ROOT} replace />;
  }

  return <Navigate to={PATHS.ROOT} replace />;
}
