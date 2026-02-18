import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { AuthStatusScreen } from "@/features/auth/components/AuthStatusScreen";

interface PublicOnlyProps {
  children: ReactNode;
}

export function PublicOnly({ children }: PublicOnlyProps) {
  const { status, isAuthenticated } = useAuth();

  if (status === "idle" || status === "loading") {
    return <AuthStatusScreen title="Preparing sign-in" description="Checking if you already have an active session." />;
  }

  if (isAuthenticated) {
    return <Navigate to={PATHS.LANDLORD.ROOT} replace />;
  }

  return <>{children}</>;
}
