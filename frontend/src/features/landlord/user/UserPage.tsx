import { useI18n } from "@/i18n";

const UserPage = () => {
  const { t } = useI18n();

  return (
    <div className="space-y-1">
      <h1 className="text-xl font-semibold">{t("pages.usersTitle")}</h1>
      <p className="text-sm text-muted-foreground">{t("pages.usersDescription")}</p>
    </div>
  );
};

export default UserPage;
