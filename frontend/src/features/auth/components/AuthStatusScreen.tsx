import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { useI18n } from "@/i18n";
import type { TranslationKey } from "@/i18n";

export type AuthStatusVariant = "restoring" | "permissions" | "preparing" | "workspace";

const VARIANT_KEYS: Record<AuthStatusVariant, { title: TranslationKey; description: TranslationKey }> = {
  restoring: { title: "authStatus.restoringTitle", description: "authStatus.restoringDescription" },
  permissions: { title: "authStatus.permissionsTitle", description: "authStatus.permissionsDescription" },
  preparing: { title: "authStatus.preparingTitle", description: "authStatus.preparingDescription" },
  workspace: { title: "authStatus.workspaceTitle", description: "authStatus.workspaceDescription" },
};

interface AuthStatusScreenProps {
  variant: AuthStatusVariant;
}

export function AuthStatusScreen({ variant }: AuthStatusScreenProps) {
  const { t } = useI18n();
  const keys = VARIANT_KEYS[variant];
  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{t(keys.title)}</CardTitle>
          <CardDescription>{t(keys.description)}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-1.5 w-full overflow-hidden bg-muted">
            <div className="h-full w-1/3 bg-primary animate-pulse" />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
