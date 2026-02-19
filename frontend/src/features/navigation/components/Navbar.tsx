import { useEffect, useMemo, useState, type ComponentType } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { Home, Warehouse, Users, Shield, ChevronRight } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import {
    Accordion,
    AccordionContent,
    AccordionItem,
    AccordionTrigger,
} from "@/components/ui/accordion";
import { cn } from "@/lib/utils";
import { PATHS } from "@/shared/consts/paths";

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

const navItems: NavItem[] = [
    { to: PATHS.LANDLORD.ROOT, label: "Home", icon: Home, end: true },
    { to: PATHS.LANDLORD.WAREHOUSES, label: "Warehouses", icon: Warehouse },
    {
        id: "accounts-management",
        label: "Accounts",
        icon: Users,
        children: [
            { to: PATHS.LANDLORD.USERS, label: "Users", icon: Users },
            { to: PATHS.LANDLORD.ROLES, label: "Roles", icon: Shield },
        ],
    },
];

export function Navbar() {
    const { pathname } = useLocation();

    const activeGroupFromRoute = useMemo(() => {
        const activeGroup = navItems.find((item) => {
            if (!isGroupItem(item)) return false;
            return item.children.some((child) => pathname.startsWith(child.to));
        });

        return activeGroup && isGroupItem(activeGroup) ? activeGroup.id : "";
    }, [pathname]);

    const [openGroup, setOpenGroup] = useState<string>(activeGroupFromRoute);

    useEffect(() => {
        if (activeGroupFromRoute) {
            setOpenGroup(activeGroupFromRoute);
        }
    }, [activeGroupFromRoute]);

    return (
        <aside className="flex min-h-screen w-56 shrink-0 flex-col border-r bg-background">
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
                            value={openGroup === item.id ? item.id : ""}
                            onValueChange={(value) => setOpenGroup(value)}
                        >
                            <AccordionItem value={item.id} className="border-none">
                                <AccordionTrigger
                                    className={cn(
                                        "rounded-md px-3 py-2 text-left",
                                        isGroupActive
                                            ? "bg-primary text-primary-foreground hover:bg-primary/90"
                                            : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                                    )}
                                >
                                    <span className="flex flex-1 items-start gap-3 text-left">
                                        <Icon className="h-4 w-4 shrink-0" />
                                        <span className="leading-tight">{item.label}</span>
                                    </span>
                                </AccordionTrigger>

                                <AccordionContent>
                                    <div className="ml-2 flex flex-col gap-1 border-l border-border/60 pl-2">
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
                                                    {/* <ChevronRight className="h-3.5 w-3.5 shrink-0" /> */}
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
