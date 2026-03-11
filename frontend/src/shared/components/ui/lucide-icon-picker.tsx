import { useMemo, useState } from "react";
import {
    Combobox,
    ComboboxCollection,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
} from "@/shared/components/ui/combobox";
import {
    filterLucideIconOptions,
    getLucideIconOption,
    normalizeLucideIconName,
    type LucideIconName,
} from "@/shared/lib/lucide-icons";

interface LucideIconPickerProps {
    id?: string;
    value: string;
    onChange: (value: LucideIconName) => void;
    placeholder: string;
    emptyMessage: string;
    label: string;
    disabled?: boolean;
}

export function LucideIconPicker({
    id,
    value,
    onChange,
    placeholder,
    emptyMessage,
    label,
    disabled = false,
}: LucideIconPickerProps) {
    const [search, setSearch] = useState("");

    const selectedOption = useMemo(
        () => getLucideIconOption(value) ?? getLucideIconOption(normalizeLucideIconName(value)),
        [value]
    );
    const filteredOptions = useMemo(() => filterLucideIconOptions(search), [search]);

    return (
        <div className="space-y-2">
            <Combobox
                items={filteredOptions}
                value={selectedOption ?? null}
                onInputValueChange={setSearch}
                onValueChange={(nextValue) => {
                    if (!nextValue) {
                        return;
                    }

                    onChange(nextValue.name);
                    setSearch("");
                }}
                itemToStringLabel={(item) => item.name}
                itemToStringValue={(item) => item.name}
                isItemEqualToValue={(item, selectedValue) => item.name === selectedValue.name}
            >
                <ComboboxInput
                    id={id}
                    aria-label={label}
                    className="w-full"
                    disabled={disabled}
                    placeholder={placeholder}
                />
                <ComboboxContent>
                    <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
                    <ComboboxList>
                        <ComboboxCollection>
                            {(option) => {
                                const Icon = option.Icon;
                                return (
                                    <ComboboxItem key={option.name} value={option}>
                                        <span className="flex size-7 items-center justify-center border bg-muted/30">
                                            <Icon className="h-4 w-4 text-primary" />
                                        </span>
                                        <span className="font-medium">{option.name}</span>
                                    </ComboboxItem>
                                );
                            }}
                        </ComboboxCollection>
                    </ComboboxList>
                </ComboboxContent>
            </Combobox>
            {selectedOption ? (
                <div className="flex items-center gap-3 border px-3 py-2 text-sm">
                    <span className="flex size-9 items-center justify-center border bg-muted/30">
                        <selectedOption.Icon className="h-4 w-4 text-primary" />
                    </span>
                    <span className="font-medium">{selectedOption.name}</span>
                </div>
            ) : null}
        </div>
    );
}