import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { PATHS } from "@/shared/consts/paths";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";

const SLUG_REGEXP = /^[A-Za-z0-9_]+$/;

export default function ScopeLandingPage() {
  const navigate = useNavigate();
  const { t } = useI18n();
  const [tenantSlug, setTenantSlug] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleTenantContinue = () => {
    const normalizedSlug = normalizeTenantSlug(tenantSlug);
    if (!normalizedSlug || !SLUG_REGEXP.test(normalizedSlug)) {
      setError(t("landing.tenantSlugValidation"));
      return;
    }

    setError(null);
    navigate(PATHS.TENANT.authLogin(normalizedSlug));
  };

  return (
    <div className="mx-auto flex min-h-screen max-w-4xl flex-col justify-center gap-6 p-6 md:flex-row md:items-stretch">
      <Card className="flex-1">
        <CardHeader>
          <CardTitle>{t("landing.landlordTitle")}</CardTitle>
          <CardDescription>{t("landing.landlordDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex h-full items-end">
          <Button asChild className="w-full">
            <Link to={PATHS.LANDLORD.AUTH_LOGIN}>{t("landing.landlordAction")}</Link>
          </Button>
        </CardContent>
      </Card>

      <Card className="flex-1">
        <CardHeader>
          <CardTitle>{t("landing.tenantTitle")}</CardTitle>
          <CardDescription>{t("landing.tenantDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="tenant-slug">{t("landing.tenantSlugLabel")}</Label>
            <Input
              id="tenant-slug"
              placeholder="acme"
              value={tenantSlug}
              onChange={(event) => setTenantSlug(event.target.value)}
            />
          </div>
          {error ? <p className="text-xs text-destructive">{error}</p> : null}
          <Button onClick={handleTenantContinue} className="w-full">
            {t("landing.tenantAction")}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
