import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";

const TenantProductsPage = () => {
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const { t } = useI18n();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  return (
    <div className="space-y-2">
      <h1 className="text-xl font-semibold">{t("tenant.productsTitle")}</h1>
      <p className="text-sm text-muted-foreground">{t("tenant.productsDescription")}</p>
      <p className="text-sm text-muted-foreground">
        {t("tenant.activeTenant", { tenant: normalizedSlug })}
      </p>
    </div>
  );
};

export default TenantProductsPage;
