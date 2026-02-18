import { NavLink } from "react-router-dom";
import { Home, Warehouse, Users } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { PATHS } from "@/shared/consts/paths"


const navItems = [
    { to: PATHS.LANDLORD.ROOT, label: "Home", icon: Home, end: true },
    { to: PATHS.LANDLORD.WAREHOUSES, label: "Warehouses", icon: Warehouse },
    { to: PATHS.LANDLORD.USERS, label: "Users", icon: Users },
];

export function Navbar() {
    return (
        <aside className="flex flex-col w-56 min-h-screen bg-background border-r shrink-0">
            {/* Brand */}
            <div className="flex items-center gap-2 px-5 h-14 font-semibold text-sm shrink-0">
                <Warehouse className="h-5 w-5 text-primary" />
                <span>WarehouseGIS</span>
            </div>

            <Separator />

            {/* Nav links */}
            <nav className="flex flex-col gap-1 p-3 flex-1">
                {navItems.map(({ to, label, icon: Icon, end }) => (
                    <NavLink
                        key={to}
                        to={to}
                        end={end}
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
                        {label}
                    </NavLink>
                ))}
            </nav>
        </aside>
    );
}
