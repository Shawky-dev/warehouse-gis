import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Separator } from "@/shared/components/ui/separator";
import { PATHS } from "@/shared/consts/paths";
import { getTenants } from "@/features/landlord/api/tenantApi";
import type { TenantSummary } from "@/features/landlord/types/tenant";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";
import WarehousesPageHeader from "@/features/landlord/warehouse/components/WarehousesPageHeader";

const WarehouseListPage = () => {
  const { t } = useI18n();
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [isLoadingTenants, setIsLoadingTenants] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  const loadTenants = useCallback(async () => {
    setIsLoadingTenants(true);
    setListError(null);
    try {
      const result = await getTenants();
      setTenants(result);
    } catch {
      setListError(t("warehouses.listLoadFailed"));
    } finally {
      setIsLoadingTenants(false);
    }
  }, [t]);

  useEffect(() => {
    void loadTenants();
  }, [loadTenants]);

  return (
    <div className="space-y-6">
      <WarehousesPageHeader />

      <Card>
        <CardHeader>
          <CardTitle>{t("warehouses.listTitle")}</CardTitle>
          <CardDescription>{t("warehouses.listDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {listError ? <p className="text-xs text-destructive">{listError}</p> : null}

          {isLoadingTenants ? (
            <p className="text-sm text-muted-foreground">{t("warehouses.listLoading")}</p>
          ) : tenants.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("warehouses.listEmpty")}</p>
          ) : (
            <div>
              <div className="grid grid-cols-[1fr_1fr_auto] gap-4 pb-2 text-xs font-medium text-muted-foreground">
                <span>{t("warehouses.warehouseId")}</span>
                <span>{t("warehouses.schema")}</span>
                <span className="text-end">{t("warehouses.listTenantPage")}</span>
              </div>
              <Separator />
              {tenants.map((tenant) => (
                <div key={tenant.tenantId}>
                  <div className="grid grid-cols-[1fr_1fr_auto] items-center gap-4 py-2 text-sm">
                    <span>{tenant.tenantId}</span>
                    <span>{tenant.schema}</span>
                    <Button asChild size="sm" variant="outline" className="justify-self-end">
                      <Link to={PATHS.TENANT.root(normalizeTenantSlug(tenant.tenantId))}>
                        {t("warehouses.listGoToTenant", { tenant: tenant.tenantId })}
                      </Link>
                    </Button>
                  </div>
                  <Separator />
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default WarehouseListPage;
