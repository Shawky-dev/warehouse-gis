import { useI18n } from "@/i18n";

const WarehousesPageHeader = () => {
  const { t } = useI18n();

  return (
    <div className="space-y-1">
      <h1 className="text-xl font-semibold">{t("warehouses.pageTitle")}</h1>
      <p className="text-sm text-muted-foreground">{t("warehouses.pageDescription")}</p>
    </div>
  );
};

export default WarehousesPageHeader;
