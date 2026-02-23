import { Card, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { useI18n } from "@/i18n";

export function AccessDeniedScreen() {
  const { t } = useI18n();

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{t("authStatus.accessDeniedTitle")}</CardTitle>
          <CardDescription>{t("authStatus.accessDeniedDescription")}</CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
