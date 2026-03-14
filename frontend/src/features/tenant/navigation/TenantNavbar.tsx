import { useMemo, useState, type ComponentType } from "react";
import { Link, NavLink, useLocation, useParams } from "react-router-dom";
import {
  Home,
  Box,
  Shield,
  Users,
  Warehouse,
  Ruler,
  Truck,
  Tag,
  ClipboardList,
  LayoutGrid,
  BookOpen,
  PackageOpen,
  PackagePlus,
  PackageMinus,
  SlidersHorizontal,
  List,
} from "lucide-react";
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
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

type LeafNavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  end?: boolean;
  depth?: number;
  exactMatch?: boolean;
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

function normalizeNavigationUrl(value: string) {
  const url = new URL(value, "https://warehouse.local");
  const normalizedParams = new URLSearchParams(
    [...url.searchParams.entries()].sort(([leftKey, leftValue], [rightKey, rightValue]) => {
      if (leftKey === rightKey) {
        return leftValue.localeCompare(rightValue);
      }
      return leftKey.localeCompare(rightKey);
    })
  );
  const search = normalizedParams.toString();
  return `${url.pathname}${search ? `?${search}` : ""}`;
}

export function TenantNavbar() {
  const { pathname, search } = useLocation();
  const { t } = useI18n();
  const { hasPermission, hasAnyPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");
  const currentUrl = `${pathname}${search}`;

  const canViewUsers = hasPermission(TENANT_PERMISSIONS.USERS_VIEW);
  const canViewRoles = hasPermission(TENANT_PERMISSIONS.ROLES_EDIT);
  const canViewProducts = hasPermission(TENANT_PERMISSIONS.PRODUCTS_VIEW);
  const canViewUoms = hasPermission(TENANT_PERMISSIONS.UOMS_VIEW);
  const canViewSuppliers = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_VIEW);
  const canViewCategories = hasPermission(TENANT_PERMISSIONS.CATEGORIES_VIEW);
  const canViewWarehouse = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_VIEW);
  const canViewAudit = hasPermission(TENANT_PERMISSIONS.AUDIT_VIEW);
  const canViewInventory = hasPermission(TENANT_PERMISSIONS.INVENTORY_VIEW);
  const canViewReceipts = hasPermission(TENANT_PERMISSIONS.RECEIPTS_VIEW);
  const canViewDispatches = hasPermission(TENANT_PERMISSIONS.DISPATCHES_VIEW);
  const canViewCounting = hasPermission(TENANT_PERMISSIONS.COUNTING_VIEW);
  const canManageOperations = hasAnyPermission([
    TENANT_PERMISSIONS.INVENTORY_RECEIVE,
    TENANT_PERMISSIONS.INVENTORY_TRANSFER,
    TENANT_PERMISSIONS.INVENTORY_ADJUST,
  ]);
  const canAccessInventory = hasAnyPermission([
    TENANT_PERMISSIONS.INVENTORY_VIEW,
    TENANT_PERMISSIONS.INVENTORY_RECEIVE,
    TENANT_PERMISSIONS.INVENTORY_TRANSFER,
    TENANT_PERMISSIONS.INVENTORY_ADJUST,
  ]);
  const warehouseChildren = useMemo<LeafNavItem[]>(() => {
    if (!canViewWarehouse) {
      return [];
    }

    return [
      {
        to: PATHS.TENANT.warehouseLayouts(normalizedSlug),
        label: t("tenant.nav.layouts"),
        icon: LayoutGrid,
        end: true,
        exactMatch: true,
      },
      {
        to: PATHS.TENANT.warehouseTemplates(normalizedSlug),
        label: t("tenant.nav.templates"),
        icon: LayoutGrid,
      },
    ];
  }, [canViewWarehouse, normalizedSlug, t]);

  const navItems = useMemo<NavItem[]>(
    () =>
      [
        {
          to: PATHS.TENANT.root(normalizedSlug),
          label: t("tenant.nav.dashboard"),
          icon: Home,
          end: true,
        },
        {
          id: "catalog",
          label: t("tenant.nav.catalog"),
          icon: BookOpen,
          children: [
            ...(canViewProducts
              ? [{ to: PATHS.TENANT.products(normalizedSlug), label: t("tenant.nav.products"), icon: Box }]
              : []),
            ...(canViewUoms
              ? [{ to: PATHS.TENANT.uoms(normalizedSlug), label: t("tenant.nav.uoms"), icon: Ruler }]
              : []),
            ...(canViewSuppliers
              ? [{ to: PATHS.TENANT.suppliers(normalizedSlug), label: t("tenant.nav.suppliers"), icon: Truck }]
              : []),
            ...(canViewCategories
              ? [{ to: PATHS.TENANT.categories(normalizedSlug), label: t("tenant.nav.categories"), icon: Tag }]
              : []),
          ],
        },
        {
          id: "warehouse-layouts",
          label: t("tenant.nav.warehouseLayouts"),
          icon: Warehouse,
          children: warehouseChildren,
        },
        {
          id: "accounts",
          label: t("tenant.nav.accounts"),
          icon: Users,
          children: [
            ...(canViewUsers
              ? [{ to: PATHS.TENANT.users(normalizedSlug), label: t("tenant.nav.users"), icon: Users }]
              : []),
            ...(canViewRoles
              ? [{ to: PATHS.TENANT.roles(normalizedSlug), label: t("tenant.nav.roles"), icon: Shield }]
              : []),
          ],
        },
        ...(canAccessInventory
          ? [
            {
              id: "inventory",
              label: t("tenant.nav.inventory"),
              icon: PackageOpen,
              children: [
                ...(canViewInventory
                  ? [
                    {
                      to: PATHS.TENANT.inventoryStock(normalizedSlug),
                      label: t("inventory.tabStock"),
                      icon: PackageOpen,
                    },
                  ]
                  : []),
                ...(canManageOperations
                  ? [
                    {
                      to: PATHS.TENANT.inventoryOperations(normalizedSlug),
                      label: t("inventory.tabOperations"),
                      icon: SlidersHorizontal,
                    },
                  ]
                  : []),
                ...(canViewInventory
                  ? [
                    {
                      to: PATHS.TENANT.inventoryMovements(normalizedSlug),
                      label: t("inventory.tabMovements"),
                      icon: List,
                    },
                  ]
                  : []),
              ],
            },
          ]
          : []),
        ...(canViewReceipts
          ? [
            {
              to: PATHS.TENANT.receipts(normalizedSlug),
              label: t("tenant.nav.receipts"),
              icon: PackagePlus,
            },
          ]
          : []),
        ...(canViewDispatches
          ? [
            {
              to: PATHS.TENANT.dispatches(normalizedSlug),
              label: t("tenant.nav.dispatches"),
              icon: PackageMinus,
            },
          ]
          : []),
        ...(canViewCounting
          ? [
            {
              to: PATHS.TENANT.countSessions(normalizedSlug),
              label: t("tenant.nav.countSessions"),
              icon: ClipboardList,
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
      ].filter((item) => !isGroupItem(item) || item.children.length > 0),
    [
      canViewAudit,
      canViewCategories,
      canViewProducts,
      canViewRoles,
      canViewSuppliers,
      canViewUoms,
      canViewUsers,
      canAccessInventory,
      canViewReceipts,
      canViewDispatches,
      canViewCounting,
      canViewWarehouse,
      normalizedSlug,
      t,
      warehouseChildren,
      canViewInventory,
      canManageOperations,
    ]
  );

  function isLeafActive(item: LeafNavItem) {
    if (item.exactMatch) {
      return normalizeNavigationUrl(currentUrl) === normalizeNavigationUrl(item.to);
    }

    if (item.end) {
      return pathname === item.to;
    }

    return pathname.startsWith(item.to);
  }

  const activeGroupFromRoute = useMemo(() => {
    const activeGroup = navItems.find((item) => {
      if (!isGroupItem(item)) return false;
      return item.children.some((child) => isLeafActive(child));
    });
    return activeGroup && isGroupItem(activeGroup) ? activeGroup.id : "";
  }, [navItems, pathname, currentUrl]);

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
                      const isChildActive = isLeafActive(child);
                      return (
                        <Link
                          key={child.to}
                          to={child.to}
                          className={
                            cn(
                              "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                              isChildActive
                                ? "bg-primary text-primary-foreground"
                                : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                          }
                          style={child.depth ? { paddingInlineStart: `${0.75 + child.depth * 1}rem` } : undefined}
                        >
                          <ChildIcon className="h-4 w-4 shrink-0" />
                          {child.label}
                        </Link>
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
