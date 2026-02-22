import { PATHS } from "@/shared/consts/paths";
import TenantDashboardPage from "@/features/tenant/dashboard/TenantDashboardPage";
import TenantProductsPage from "@/features/tenant/products/TenantProductsPage";

export const tenantRoutes = [
  {
    path: PATHS.TENANT.ROOT_PATTERN,
    element: <TenantDashboardPage />,
  },
  {
    path: PATHS.TENANT.PRODUCTS_PATTERN,
    element: <TenantProductsPage />,
  },
];
