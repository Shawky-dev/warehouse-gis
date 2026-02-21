import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";

interface PublicOnlyProps {
  children: ReactNode;
}

export function PublicOnly({ children }: PublicOnlyProps) {
  const { status, isAuthenticated } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.preparingTitle")}
        description={t("authStatus.preparingDescription")}
      />
    );
  }

  if (isAuthenticated) {
    return <Navigate to={PATHS.LANDLORD.ROOT} replace />;
  }

  return <>{children}</>;
}
