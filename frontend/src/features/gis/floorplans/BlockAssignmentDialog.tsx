import { useState } from "react";
import { useI18n } from "@/i18n";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import type { AvailableBlock } from "./useEditorState";

interface BlockAssignmentDialogProps {
  open: boolean;
  templateName: string;
  availableBlocks: AvailableBlock[];
  loadingBlocks: boolean;
  currentLabel?: string;
  onAssign: (layoutBlockId: string, fullCode: string) => void;
  onCancel: () => void;
}

export function BlockAssignmentDialog({
  open,
  templateName,
  availableBlocks,
  loadingBlocks,
  currentLabel,
  onAssign,
  onCancel,
}: BlockAssignmentDialogProps) {
  const { t } = useI18n();
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (!open) return null;

  const filtered = availableBlocks.filter((b) =>
    b.fullCode.toLowerCase().includes(search.toLowerCase())
  );

  const selectedBlock = availableBlocks.find((b) => b.id === selectedId);

  function handleAssign() {
    if (!selectedBlock) return;
    onAssign(selectedBlock.id, selectedBlock.fullCode);
    setSearch("");
    setSelectedId(null);
  }

  function handleCancel() {
    setSearch("");
    setSelectedId(null);
    onCancel();
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-background ring-foreground/10 flex w-80 flex-col gap-3 rounded-md p-4 shadow-lg ring-1">
        <h2 className="text-sm font-semibold">
          {t("gis.editor.assignDialogTitle").replace("{templateName}", templateName)}
        </h2>

        {currentLabel && (
          <p className="rounded-sm bg-muted px-2 py-1.5 text-xs text-muted-foreground">
            {t("gis.editor.currentAssignment").replace("{label}", currentLabel)}
          </p>
        )}

        <Input
          placeholder={t("gis.editor.assignSearchPlaceholder")}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setSelectedId(null);
          }}
          autoFocus
        />

        <div className="max-h-56 overflow-y-auto rounded-md border">
          {loadingBlocks ? (
            <div className="flex items-center justify-center py-6 text-xs text-muted-foreground">
              Loading...
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-6 text-xs text-muted-foreground">
              {t("gis.editor.assignNoResults")}
            </div>
          ) : (
            filtered.map((block) => (
              <button
                key={block.id}
                type="button"
                className={`w-full px-3 py-2 text-left text-xs transition-colors hover:bg-accent hover:text-accent-foreground ${
                  selectedId === block.id ? "bg-accent text-accent-foreground font-medium" : ""
                }`}
                onClick={() => setSelectedId(block.id)}
                onDoubleClick={() => {
                  setSelectedId(block.id);
                  onAssign(block.id, block.fullCode);
                  setSearch("");
                  setSelectedId(null);
                }}
              >
                {block.fullCode}
              </button>
            ))
          )}
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" size="sm" onClick={handleCancel}>
            {t("gis.editor.assignCancel")}
          </Button>
          <Button
            type="button"
            size="sm"
            disabled={!selectedBlock}
            onClick={handleAssign}
          >
            {t("gis.editor.assignSave")}
          </Button>
        </div>
      </div>
    </div>
  );
}
