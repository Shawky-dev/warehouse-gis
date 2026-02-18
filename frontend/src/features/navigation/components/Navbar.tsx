import { Separator } from "@/components/ui/separator";
import { AvatarMenu } from "./AvatarMenu";

export function Navbar() {
    return (
        <header className="sticky top-0 z-50 w-full bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
            <div className="flex h-14 items-center px-6">
                {/* Brand */}
                <div className="flex items-center gap-2 font-semibold text-sm">
                    <span>WarehouseGIS</span>
                </div>

                {/* Nav links — grows to push avatar right */}
                <nav className="flex flex-1 items-center gap-4 px-6">
                    {/* Add <NavLink> items here as features grow */}
                </nav>

                {/* Right side */}
                <div className="flex items-center gap-3">
                    <AvatarMenu />
                </div>
            </div>
            <Separator />
        </header>
    );
}
