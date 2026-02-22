import type { ComponentType } from "react";
import { NavLink, useParams } from "react-router-dom";
import { Home, Box, Warehouse } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { PATHS } from "@/shared/consts/paths";
import { useI18n } from "@/i18n";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";

type TenantNavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  end?: boolean;
};

export function TenantNavbar() {
  const { t } = useI18n();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");

  const navItems: TenantNavItem[] = [
    {
      to: PATHS.TENANT.root(normalizedSlug),
      label: t("tenant.nav.dashboard"),
      icon: Home,
      end: true,
    },
    {
      to: PATHS.TENANT.products(normalizedSlug),
      label: t("tenant.nav.products"),
      icon: Box,
    },
  ];

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
