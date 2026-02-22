import { useMemo, useState, type ComponentType } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { Home, List, Plus, Shield, Users, Warehouse } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { cn } from "@/lib/utils";
import { PATHS } from "@/shared/consts/paths";
import { useI18n } from "@/i18n";

type LeafNavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  end?: boolean;
};

type GroupNavItem = {
  id: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  children: LeafNavItem[];
};

type NavItem = LeafNavItem | GroupNavItem;

function isGroupItem(item: NavItem): item is GroupNavItem {
  return "children" in item;
}

export function LandlordNavbar() {
  const { pathname } = useLocation();
  const { t } = useI18n();

  const navItems = useMemo<NavItem[]>(
    () => [
      { to: PATHS.LANDLORD.ROOT, label: t("nav.home"), icon: Home, end: true },
      {
        id: "warehouses-management",
        label: t("nav.warehouses"),
        icon: Warehouse,
        children: [
          { to: PATHS.LANDLORD.WAREHOUSES_CREATE, label: t("nav.warehousesCreate"), icon: Plus },
          { to: PATHS.LANDLORD.WAREHOUSES_LIST, label: t("nav.warehousesList"), icon: List },
        ],
      },
      {
        id: "accounts-management",
        label: t("nav.accounts"),
        icon: Users,
        children: [
          { to: PATHS.LANDLORD.USERS, label: t("nav.users"), icon: Users },
          { to: PATHS.LANDLORD.ROLES, label: t("nav.roles"), icon: Shield },
        ],
      },
    ],
    [t]
  );

  const activeGroupFromRoute = useMemo(() => {
    const activeGroup = navItems.find((item) => {
      if (!isGroupItem(item)) return false;
      return item.children.some((child) => pathname.startsWith(child.to));
    });

    return activeGroup && isGroupItem(activeGroup) ? activeGroup.id : "";
  }, [navItems, pathname]);

  const [openGroups, setOpenGroups] = useState<string[]>(
    activeGroupFromRoute ? [activeGroupFromRoute] : []
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
          if (!isGroupItem(item)) {
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
          }

          const Icon = item.icon;
          const isGroupActive = item.children.some((child) => pathname.startsWith(child.to));

          return (
            <Accordion
              key={item.id}
              type="single"
              collapsible
              value={openGroups.includes(item.id) || activeGroupFromRoute === item.id ? item.id : ""}
              onValueChange={(value) => {
                setOpenGroups((previous) => {
                  const next = new Set(previous);
                  if (value === item.id) {
                    next.add(item.id);
                  } else {
                    next.delete(item.id);
                  }
                  return Array.from(next);
                });
              }}
            >
              <AccordionItem value={item.id} className="border-none">
                <AccordionTrigger
                  className={cn(
                    "rounded-md px-3 py-2 text-start",
                    isGroupActive
                      ? "bg-primary text-primary-foreground hover:bg-primary/90"
                      : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  )}
                >
                  <span className="flex flex-1 items-start gap-3 text-start">
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="leading-tight">{item.label}</span>
                  </span>
                </AccordionTrigger>

                <AccordionContent>
                  <div className="ms-2 flex flex-col gap-1 border-s border-border/60 ps-2">
                    {item.children.map((child) => {
                      const ChildIcon = child.icon;

                      return (
                        <NavLink
                          key={child.to}
                          to={child.to}
                          end={child.end}
                          className={({ isActive }) =>
                            cn(
                              "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                              isActive
                                ? "bg-primary text-primary-foreground"
                                : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                          }
                        >
                          <ChildIcon className="h-4 w-4 shrink-0" />
                          {child.label}
                        </NavLink>
                      );
                    })}
                  </div>
                </AccordionContent>
              </AccordionItem>
            </Accordion>
          );
        })}
      </nav>
    </aside>
  );
}
