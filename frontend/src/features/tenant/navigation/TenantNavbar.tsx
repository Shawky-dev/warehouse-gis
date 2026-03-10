import { useMemo, type ComponentType } from "react";
import { NavLink, useParams } from "react-router-dom";
import { Home, Box, Shield, Users, Warehouse, Ruler, Truck, Tag, ClipboardList, MapPin } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { PATHS } from "@/shared/consts/paths";
import { useI18n } from "@/i18n";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

type TenantNavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  end?: boolean;
};

export function TenantNavbar() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  const canViewUsers = hasPermission(TENANT_PERMISSIONS.USERS_VIEW);
  const canViewRoles = hasPermission(TENANT_PERMISSIONS.ROLES_EDIT);
  const canViewProducts = hasPermission(TENANT_PERMISSIONS.PRODUCTS_VIEW);
  const canViewUoms = hasPermission(TENANT_PERMISSIONS.UOMS_VIEW);
  const canViewSuppliers = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_VIEW);
  const canViewCategories = hasPermission(TENANT_PERMISSIONS.CATEGORIES_VIEW);
  const canViewWarehouse = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_VIEW);
  const canViewAudit = hasPermission(TENANT_PERMISSIONS.AUDIT_VIEW);

  const navItems = useMemo<TenantNavItem[]>(
    () =>
      [
        {
          to: PATHS.TENANT.root(normalizedSlug),
          label: t("tenant.nav.dashboard"),
          icon: Home,
          end: true,
        },
        ...(canViewProducts
          ? [
              {
                to: PATHS.TENANT.products(normalizedSlug),
                label: t("tenant.nav.products"),
                icon: Box,
              },
            ]
          : []),
        ...(canViewUoms
          ? [
              {
                to: PATHS.TENANT.uoms(normalizedSlug),
                label: t("tenant.nav.uoms"),
                icon: Ruler,
              },
            ]
          : []),
        ...(canViewSuppliers
          ? [
              {
                to: PATHS.TENANT.suppliers(normalizedSlug),
                label: t("tenant.nav.suppliers"),
                icon: Truck,
              },
            ]
          : []),
        ...(canViewCategories
          ? [
              {
                to: PATHS.TENANT.categories(normalizedSlug),
                label: t("tenant.nav.categories"),
                icon: Tag,
              },
            ]
          : []),
        ...(canViewWarehouse
          ? [
              {
                to: PATHS.TENANT.warehouseLayouts(normalizedSlug),
                label: t("tenant.nav.warehouseLayout"),
                icon: MapPin,
              },
            ]
          : []),
        ...(canViewUsers
          ? [
              {
                to: PATHS.TENANT.users(normalizedSlug),
                label: t("tenant.nav.users"),
                icon: Users,
              },
            ]
          : []),
        ...(canViewRoles
          ? [
              {
                to: PATHS.TENANT.roles(normalizedSlug),
                label: t("tenant.nav.roles"),
                icon: Shield,
              },
            ]
          : []),
        ...(canViewAudit
          ? [
              {
                to: PATHS.TENANT.auditLogs(normalizedSlug),
                label: t("tenant.nav.auditLogs"),
                icon: ClipboardList,
              },
            ]
          : []),
      ],
    [
      canViewAudit,
      canViewCategories,
      canViewProducts,
      canViewRoles,
      canViewSuppliers,
      canViewUoms,
      canViewUsers,
      canViewWarehouse,
      normalizedSlug,
      t,
    ]
  );

  return (
    <aside className="flex min-h-screen w-56 shrink-0 flex-col border-e bg-background">
      <div className="flex h-14 shrink-0 items-center gap-2 px-5 text-sm font-semibold">
        <Warehouse className="h-5 w-5 text-primary" />
        <span>WarehouseGIS</span>
      </div>

      <Separator />

      <nav className="flex flex-1 flex-col gap-1 p-3">
        {navItems.map((item) => {
          const Icon = item.icon;

          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                )
              }
            >
              <Icon className="h-4 w-4 shrink-0" />
              {item.label}
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}
