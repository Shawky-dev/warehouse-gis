import { useI18n } from "@/i18n";

const RolesPage = () => {
  const { t } = useI18n();

  return (
    <div className="space-y-1">
      <h1 className="text-xl font-semibold">{t("pages.rolesTitle")}</h1>
      <p className="text-sm text-muted-foreground">{t("pages.rolesDescription")}</p>
    </div>
  );
};

export default RolesPage;
