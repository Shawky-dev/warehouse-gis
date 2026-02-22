import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";

const TenantDashboardPage = () => {
  const { t } = useI18n();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  return (
    <div className="space-y-2">
      <h1 className="text-xl font-semibold">{t("tenant.dashboardTitle")}</h1>
      <p className="text-sm text-muted-foreground">
        {t("tenant.activeTenant", { tenant: normalizedSlug })}
      </p>
    </div>
  );
};

export default TenantDashboardPage;
