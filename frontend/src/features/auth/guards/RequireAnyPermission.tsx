import type { ReactNode } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { AccessDeniedScreen } from "@/features/auth/components/AccessDeniedScreen";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";
import { useI18n } from "@/i18n";

interface RequireAnyPermissionProps {
  permissions: string[];
  children: ReactNode;
}

export function RequireAnyPermission({ permissions, children }: RequireAnyPermissionProps) {
  const { status, hasAnyPermission } = useAuth();
  const { t } = useI18n();

  if (status === "idle" || status === "loading") {
    return (
      <AuthStatusScreen
        title={t("authStatus.permissionsTitle")}
        description={t("authStatus.permissionsDescription")}
      />
    );
  }

  if (!hasAnyPermission(permissions)) {
    return <AccessDeniedScreen />;
  }

  return <>{children}</>;
}
