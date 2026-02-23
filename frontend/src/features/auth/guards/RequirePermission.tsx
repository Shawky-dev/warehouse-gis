import type { ReactNode } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { AccessDeniedScreen } from "@/features/auth/components/AccessDeniedScreen";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";

interface RequirePermissionProps {
  permission: string;
  children: ReactNode;
}

export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { status, hasPermission } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.permissionsTitle")}
        description={t("authStatus.permissionsDescription")}
      />
    );
  }

  if (!hasPermission(permission)) {
    return <AccessDeniedScreen />;
  }

  return <>{children}</>;
}
