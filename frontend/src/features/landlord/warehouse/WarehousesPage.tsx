import { useEffect, useMemo, useState } from "react";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Badge } from "@/shared/components/ui/badge";
import { Separator } from "@/shared/components/ui/separator";
import {
  createTenant,
  extractTenantErrorMessage,
  getTenants,
} from "@/features/landlord/api/tenantApi";
import type { TenantSummary } from "@/features/landlord/types/tenant";
import { useI18n } from "@/i18n";

const SCHEMA_REGEXP = /^[A-Za-z0-9_]*$/;

const WarehousesPage = () => {
  const { t } = useI18n();
  const [tenantId, setTenantId] = useState("");
  const [schema, setSchema] = useState("");
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [isLoadingTenants, setIsLoadingTenants] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const schemaError = useMemo(() => {
    if (!schema.trim()) {
      return null;
    }
    if (!SCHEMA_REGEXP.test(schema)) {
      return t("warehouses.schemaValidation");
    }
    return null;
  }, [schema, t]);

  async function loadTenants() {
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
  }

  useEffect(() => {
    void loadTenants();
  }, [t]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError(null);
    setSuccessMessage(null);

    const normalizedTenantId = tenantId.trim();
    const normalizedSchema = schema.trim();

    if (!normalizedTenantId) {
      setFormError(t("warehouses.requiredWarehouseId"));
      return;
    }
    if (!normalizedSchema) {
      setFormError(t("warehouses.requiredSchema"));
      return;
    }
    if (!SCHEMA_REGEXP.test(normalizedSchema)) {
      setFormError(null);
      return;
    }

    setIsSubmitting(true);
    try {
      await createTenant({
        tenantId: normalizedTenantId,
        schema: normalizedSchema,
      });
      setTenantId("");
      setSchema("");
      setSuccessMessage(t("warehouses.createdSuccess", { tenantId: normalizedTenantId }));
      await loadTenants();
    } catch (error) {
      setFormError(extractTenantErrorMessage(error) ?? t("warehouses.createFailedFallback"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("warehouses.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("warehouses.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("warehouses.createTitle")}</CardTitle>
          <CardDescription>{t("warehouses.createDescription")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="warehouse-id">{t("warehouses.warehouseId")}</Label>
              <Input
                id="warehouse-id"
                value={tenantId}
                onChange={(event) => setTenantId(event.target.value)}
                placeholder="acme"
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="warehouse-schema">{t("warehouses.schema")}</Label>
              <Input
                id="warehouse-schema"
                value={schema}
                onChange={(event) => setSchema(event.target.value)}
                placeholder="acme"
                required
              />
              {schemaError ? <p className="text-xs text-destructive">{schemaError}</p> : null}
            </div>

            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
            {successMessage ? <Badge variant="outline">{successMessage}</Badge> : null}

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? t("warehouses.createSubmitting") : t("warehouses.createSubmit")}
            </Button>
          </form>
        </CardContent>
      </Card>

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
              <div className="grid grid-cols-2 gap-4 pb-2 text-xs font-medium text-muted-foreground">
                <span>{t("warehouses.warehouseId")}</span>
                <span>{t("warehouses.schema")}</span>
              </div>
              <Separator />
              {tenants.map((tenant) => (
                <div key={tenant.tenantId}>
                  <div className="grid grid-cols-2 gap-4 py-2 text-sm">
                    <span>{tenant.tenantId}</span>
                    <span>{tenant.schema}</span>
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

export default WarehousesPage;
