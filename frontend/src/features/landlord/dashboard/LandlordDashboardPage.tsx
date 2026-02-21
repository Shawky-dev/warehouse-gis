import { useI18n } from "@/i18n";

const LandlordDashboardPage = () => {
  const { t } = useI18n();

  return (
    <div className="space-y-2">
      <h1 className="text-xl font-semibold">{t("dashboard.title")}</h1>
    </div>
  );
};

export default LandlordDashboardPage;
