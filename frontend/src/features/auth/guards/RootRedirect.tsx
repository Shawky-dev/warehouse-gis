import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";

export function RootRedirect() {
  const { status, isAuthenticated, hasRole } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.workspaceTitle")}
        description={t("authStatus.workspaceDescription")}
      />
    );
  }

  if (isAuthenticated && hasRole("ROLE_ADMIN")) {
    return <Navigate to={PATHS.LANDLORD.ROOT} replace />;
  }

  return <Navigate to={PATHS.LOGIN} replace />;
}
