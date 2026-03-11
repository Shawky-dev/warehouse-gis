import type { ComponentType } from "react";
import {
    AlignJustify,
    BookOpen,
    Box,
    ClipboardList,
    GitBranch,
    Home,
    Layers,
    LayoutGrid,
    List,
    MapPin,
    Ruler,
    Shield,
    Tag,
    Truck,
    Users,
    Warehouse,
} from "lucide-react";

export type WarehouseIconName =
    | "AlignJustify"
    | "BookOpen"
    | "Box"
    | "ClipboardList"
    | "GitBranch"
    | "Home"
    | "Layers"
    | "LayoutGrid"
    | "List"
    | "MapPin"
    | "Ruler"
    | "Shield"
    | "Tag"
    | "Truck"
    | "Users"
    | "Warehouse";

export interface WarehouseIconOption {
    name: WarehouseIconName;
    label: string;
    Icon: ComponentType<{ className?: string }>;
}

export const WAREHOUSE_ICON_OPTIONS: WarehouseIconOption[] = [
    { name: "Warehouse", label: "Warehouse", Icon: Warehouse },
    { name: "LayoutGrid", label: "Layout", Icon: LayoutGrid },
    { name: "AlignJustify", label: "Aisle", Icon: AlignJustify },
    { name: "GitBranch", label: "Split / Side", Icon: GitBranch },
    { name: "Layers", label: "Bay / Layer", Icon: Layers },
    { name: "List", label: "Level / Sequence", Icon: List },
    { name: "MapPin", label: "Shelf / Location", Icon: MapPin },
    { name: "Box", label: "Container", Icon: Box },
    { name: "BookOpen", label: "Catalog", Icon: BookOpen },
    { name: "ClipboardList", label: "Checklist", Icon: ClipboardList },
    { name: "Ruler", label: "Measure", Icon: Ruler },
    { name: "Tag", label: "Label", Icon: Tag },
    { name: "Truck", label: "Dock", Icon: Truck },
    { name: "Users", label: "Team", Icon: Users },
    { name: "Shield", label: "Secure", Icon: Shield },
    { name: "Home", label: "Room", Icon: Home },
];

const iconMap = new Map<string, WarehouseIconOption>(
    WAREHOUSE_ICON_OPTIONS.map((option) => [option.name, option])
);

export function getWarehouseIcon(name: string | null | undefined) {
    return iconMap.get(name ?? "")?.Icon ?? LayoutGrid;
}

export function isWarehouseIconName(value: string | null | undefined): value is WarehouseIconName {
    return typeof value === "string" && iconMap.has(value);
}