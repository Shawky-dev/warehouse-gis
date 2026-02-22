import { Navigate, useNavigate, useParams } from "react-router-dom";
import { AuthLoginForm } from "@/features/auth/shared/components/AuthLoginForm";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";

const TenantLoginPage = () => {
  const navigate = useNavigate();
  const { t } = useI18n();
  const { login } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();

  if (!tenantSlug) {
    return <Navigate to={PATHS.ROOT} replace />;
  }

  const normalizedSlug = normalizeTenantSlug(tenantSlug);

  const handleSubmit = async (payload: { email: string; password: string }) => {
    await login(payload);
    navigate(PATHS.TENANT.root(normalizedSlug), { replace: true });
  };

  return (
    <AuthLoginForm
      title={t("login.tenantTitle", { tenant: normalizedSlug })}
      description={t("login.tenantDescription")}
      placeholderEmail={`admin@${normalizedSlug}.local`}
      onSubmit={handleSubmit}
    />
  );
};

export default TenantLoginPage;
