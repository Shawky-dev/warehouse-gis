import { useEffect, useMemo, useState, type ComponentType } from "react";
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
} from "lucide-react";
import {
  listWarehouseLayoutBlocks,
  listWarehouseLayouts,
  listWarehouseTemplates,
} from "@/features/tenant/api/warehouseApi";
import type {
  WarehouseBlockNode,
  WarehouseFlattenedNode,
  WarehouseLayoutResult,
  WarehouseTemplateResult,
} from "@/features/tenant/types/warehouse";
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
import { getLucideIcon } from "@/shared/lib/lucide-icons";

type LeafNavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  end?: boolean;
  depth?: number;
  exactMatch?: boolean;
  iconName?: string;
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
  const { hasPermission } = useAuth();
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
  const [activeLayout, setActiveLayout] = useState<WarehouseLayoutResult | null>(null);
  const [activeTree, setActiveTree] = useState<WarehouseBlockNode[]>([]);
  const [templates, setTemplates] = useState<WarehouseTemplateResult[]>([]);

  const flattenedActiveTree = useMemo<WarehouseFlattenedNode[]>(() => {
    function flatten(nodes: WarehouseBlockNode[], depth = 0, parentPath: string[] = []): WarehouseFlattenedNode[] {
      return nodes.flatMap((node) => {
        const path = [...parentPath, node.block.id];
        return [{ node, depth, path }, ...flatten(node.children, depth + 1, path)];
      });
    }

    return flatten(activeTree);
  }, [activeTree]);

  useEffect(() => {
    if (!canViewWarehouse || !normalizedSlug) {
      setActiveLayout(null);
      setActiveTree([]);
      setTemplates([]);
      return;
    }

    let cancelled = false;

    async function loadWarehouseNav() {
      try {
        const [layoutResponse, templateResponse] = await Promise.all([
          listWarehouseLayouts(normalizedSlug, { active: true, page: 0, size: 1 }),
          listWarehouseTemplates(normalizedSlug, { page: 0, size: 100 }),
        ]);

        if (cancelled) {
          return;
        }

        const currentActiveLayout = layoutResponse.content[0] ?? null;
        setActiveLayout(currentActiveLayout);
        setTemplates(templateResponse.content);

        if (!currentActiveLayout) {
          setActiveTree([]);
          return;
        }

        const tree = await listWarehouseLayoutBlocks(normalizedSlug, currentActiveLayout.id);
        if (!cancelled) {
          setActiveTree(tree);
        }
      } catch {
        if (!cancelled) {
          setActiveLayout(null);
          setActiveTree([]);
        }
      }
    }

    void loadWarehouseNav();

    return () => {
      cancelled = true;
    };
  }, [canViewWarehouse, normalizedSlug]);

  const warehouseChildren = useMemo<LeafNavItem[]>(() => {
    if (!canViewWarehouse) {
      return [];
    }

    const baseItems: LeafNavItem[] = [
      {
        to: PATHS.TENANT.warehouseLayouts(normalizedSlug),
        label: t("tenant.nav.layouts"),
        icon: LayoutGrid,
        end: true,
        exactMatch: true,
      },
    ];

    if (!activeLayout) {
      return baseItems;
    }

    const treeItems = flattenedActiveTree.map((entry) => {
      const template = templates.find((item) => item.id === entry.node.block.blockTemplateId);
      return {
        to: PATHS.TENANT.warehouseLayouts(normalizedSlug, {
          layoutId: activeLayout.id,
          mode: "active",
          path: entry.path.join(","),
          tab: "builder",
        }),
        label: template?.name ?? t("tenant.nav.layoutBlock"),
        icon: getLucideIcon(template?.iconName),
        depth: entry.depth,
        exactMatch: true,
        iconName: template?.iconName ?? "LayoutGrid",
      } satisfies LeafNavItem;
    });

    return [...baseItems, ...treeItems];
  }, [activeLayout, canViewWarehouse, flattenedActiveTree, normalizedSlug, t, templates]);

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
      canViewWarehouse,
      normalizedSlug,
      t,
      warehouseChildren,
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
                          data-warehouse-icon={child.iconName}
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
