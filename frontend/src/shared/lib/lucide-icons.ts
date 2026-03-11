import { icons, LayoutGrid, type LucideIcon } from "lucide-react";

const LEGACY_LUCIDE_ICON_NAME_MAP = {
    AlignJustify: "TextAlignJustify",
    Home: "House",
} as const;

export const DEFAULT_LUCIDE_ICON_NAME = "LayoutGrid" as const;

export type LucideIconName = Exclude<keyof typeof icons, "Icon">;

export interface LucideIconOption {
    name: LucideIconName;
    Icon: LucideIcon;
    searchText: string;
}

function splitLucideIconName(name: string) {
    return name
        .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2");
}

export const LUCIDE_ICON_OPTIONS: LucideIconOption[] = Object.entries(icons)
    .filter(([name]) => name !== "Icon")
    .sort(([leftName], [rightName]) => leftName.localeCompare(rightName))
    .map(([name, Icon]) => ({
        name: name as LucideIconName,
        Icon: Icon as LucideIcon,
        searchText: `${name} ${splitLucideIconName(name)}`.toLowerCase(),
    }));

const lucideIconMap = new Map<LucideIconName, LucideIconOption>(
    LUCIDE_ICON_OPTIONS.map((option) => [option.name, option])
);

export function resolveLucideIconName(value: string | null | undefined): LucideIconName | null {
    if (typeof value !== "string") {
        return null;
    }

    const trimmedValue = value.trim();
    if (!trimmedValue) {
        return null;
    }

    const normalizedValue = LEGACY_LUCIDE_ICON_NAME_MAP[trimmedValue as keyof typeof LEGACY_LUCIDE_ICON_NAME_MAP] ?? trimmedValue;
    return lucideIconMap.has(normalizedValue as LucideIconName)
        ? normalizedValue as LucideIconName
        : null;
}

export function normalizeLucideIconName(value: string | null | undefined) {
    return resolveLucideIconName(value) ?? DEFAULT_LUCIDE_ICON_NAME;
}

export function isLucideIconName(value: string | null | undefined): value is LucideIconName {
    return resolveLucideIconName(value) !== null;
}

export function getLucideIconOption(value: string | null | undefined) {
    const iconName = resolveLucideIconName(value);
    return iconName ? lucideIconMap.get(iconName) : undefined;
}

export function getLucideIcon(value: string | null | undefined): LucideIcon {
    return getLucideIconOption(value)?.Icon ?? LayoutGrid;
}

export function getLucideIconLabel(value: string | null | undefined) {
    return resolveLucideIconName(value) ?? value ?? null;
}

export function filterLucideIconOptions(query: string) {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
        return LUCIDE_ICON_OPTIONS;
    }

    return LUCIDE_ICON_OPTIONS.filter((option) => option.searchText.includes(normalizedQuery));
}