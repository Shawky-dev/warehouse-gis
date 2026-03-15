import { useNavigate } from "react-router-dom";
import { PATHS } from "@/shared/consts/paths";
import type { ScanResolveResult } from "@/features/tenant/types/scan";

export function useScanNavigation(tenantSlug: string) {
  const navigate = useNavigate();

  function navigateFromResult(result: ScanResolveResult) {
    switch (result.type) {
      case "PRODUCT":
        navigate(
          `${PATHS.TENANT.inventoryStock(tenantSlug)}?productId=${result.productId}`
        );
        break;
      case "LOCATION":
        navigate(
          `${PATHS.TENANT.inventoryStock(tenantSlug)}?locationId=${result.locationId}`
        );
        break;
      case "LOT":
        navigate(
          `${PATHS.TENANT.inventoryStock(tenantSlug)}?productId=${result.productId}&lot=${encodeURIComponent(result.lotNumber ?? "")}`
        );
        break;
      case "STOCK_ROW":
        navigate(
          `${PATHS.TENANT.inventoryStock(tenantSlug)}?productId=${result.productId}&locationId=${result.locationId}`
        );
        break;
      case "RECEIPT":
        navigate(
          `${PATHS.TENANT.receipts(tenantSlug)}?id=${result.receiptId}`
        );
        break;
      case "DISPATCH":
        navigate(
          `${PATHS.TENANT.dispatches(tenantSlug)}?id=${result.dispatchId}`
        );
        break;
      case "COUNT_SESSION":
        navigate(
          `${PATHS.TENANT.countSessions(tenantSlug)}?id=${result.countSessionId}`
        );
        break;
    }
  }

  return { navigateFromResult };
}
