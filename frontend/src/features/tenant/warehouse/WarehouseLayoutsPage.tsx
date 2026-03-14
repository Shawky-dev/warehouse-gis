import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type FormEvent,
} from "react";
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom";
import {
    ArrowDown,
    ArrowUp,
    Check,
    ChevronDown,
    ChevronRight,
    ClipboardPaste,
    Copy,
    Pencil,
    Plus,
    Sparkles,
    Trash2,
} from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import {
    activateWarehouseLayout,
    addWarehouseLayoutBlock,
    addWarehouseLayoutBlocks,
    copyWarehouseLayoutBlockSubtree,
    createWarehouseLocationKind,
    createClassicWarehousePreset,
    createWarehouseLayout,
    createWarehouseTemplate,
    deleteWarehouseLocationKind,
    deleteWarehouseLayout,
    deleteWarehouseLayoutBlock,
    deleteWarehouseTemplate,
    deactivateWarehouseLayout,
    extractWarehouseErrorMessage,
    listWarehouseLayoutBlocks,
    listWarehouseLayouts,
    listWarehouseLocationKinds,
    listWarehouseTemplates,
    moveWarehouseLayoutBlock,
    reassignWarehouseLayoutBlockTemplate,
    updateWarehouseLayoutBlockMetadata,
    updateWarehouseLocationKind,
    updateWarehouseLayout,
    updateWarehouseTemplate,
} from "@/features/tenant/api/warehouseApi";
import type {
    AddWarehouseBlockRequest,
    UpsertWarehouseLocationKindRequest,
    UpsertWarehouseLayoutRequest,
    UpsertWarehouseTemplateRequest,
    WarehouseBlockResult,
    WarehouseBlockNode,
    WarehouseFlattenedNode,
    WarehouseIdentifierFormat,
    WarehouseLayoutResult,
    WarehouseLocationKindResult,
    WarehouseSideConfig,
    WarehouseTemplateResult,
} from "@/features/tenant/types/warehouse";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { LucideIconPicker } from "@/shared/components/ui/lucide-icon-picker";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/shared/components/ui/select";
import { Textarea } from "@/shared/components/ui/textarea";
import { Badge } from "@/shared/components/ui/badge";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/shared/components/ui/alert-dialog";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import {
    getLucideIcon,
    getLucideIconLabel,
    normalizeLucideIconName,
} from "@/shared/lib/lucide-icons";
import { LocationLabel } from "@/features/tenant/labels/LocationLabel";

type LayoutFilter = "all" | "active" | "inactive";
type LayoutDialogMode = "create" | "edit" | "classic" | null;
type TemplateDialogMode = "create" | "edit" | null;
type LocationKindDialogMode = "create" | "edit" | null;
type PageTab = "builder" | "templates";

interface LayoutFormState {
    name: string;
    description: string;
    activate: boolean;
}

interface TemplateFormState {
    name: string;
    identifierFormat: WarehouseIdentifierFormat;
    sideConfig: WarehouseSideConfig;
    sideOptions: string;
    required: boolean;
    description: string;
    iconName: string;
}

interface BlockFormState {
    blockTemplateId: string;
    parentId: string;
    position: string;
    quantity: string;
    side: string;
}

interface PasteBlockFormState {
    parentId: string;
    position: string;
    copies: string;
}

interface LocationKindFormState {
    name: string;
}

interface BlockClipboardState {
    sourceBlockId: string;
    label: string;
    totalNodes: number;
}

type DeleteTarget =
    | { type: "layout"; id: string; label: string }
    | { type: "template"; id: string; label: string }
    | { type: "locationKind"; id: string; label: string }
    | { type: "block"; id: string; label: string };

const DEFAULT_LAYOUT_FORM: LayoutFormState = {
    name: "",
    description: "",
    activate: false,
};

const DEFAULT_TEMPLATE_FORM: TemplateFormState = {
    name: "",
    identifierFormat: "NUMERIC",
    sideConfig: "NONE",
    sideOptions: "",
    required: true,
    description: "",
    iconName: "LayoutGrid",
};

const DEFAULT_BLOCK_FORM: BlockFormState = {
    blockTemplateId: "",
    parentId: "__root__",
    position: "",
    quantity: "1",
    side: "__none__",
};

const DEFAULT_PASTE_FORM: PasteBlockFormState = {
    parentId: "__root__",
    position: "",
    copies: "1",
};

const DEFAULT_LOCATION_KIND_FORM: LocationKindFormState = {
    name: "",
};

function parsePath(value: string | null): string[] {
    return (value ?? "")
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);
}

function joinPath(path: string[]) {
    return path.join(",");
}

function flattenTree(nodes: WarehouseBlockNode[], depth = 0, parentPath: string[] = []): WarehouseFlattenedNode[] {
    return nodes.flatMap((node) => {
        const path = [...parentPath, node.block.id];
        return [{ node, depth, path }, ...flattenTree(node.children, depth + 1, path)];
    });
}

function flattenVisibleTree(
    nodes: WarehouseBlockNode[],
    collapsedIds: Set<string>,
    depth = 0,
    parentPath: string[] = []
): WarehouseFlattenedNode[] {
    return nodes.flatMap((node) => {
        const path = [...parentPath, node.block.id];
        if (collapsedIds.has(node.block.id)) {
            return [{ node, depth, path }];
        }
        return [{ node, depth, path }, ...flattenVisibleTree(node.children, collapsedIds, depth + 1, path)];
    });
}

function findFlattenedNodeByPath(flattenedNodes: WarehouseFlattenedNode[], path: string[]) {
    if (path.length === 0) {
        return null;
    }

    const joined = joinPath(path);
    return flattenedNodes.find((item) => joinPath(item.path) === joined) ?? null;
}

function buildDescendantSet(node: WarehouseBlockNode): Set<string> {
    const ids = new Set<string>();

    function visit(current: WarehouseBlockNode) {
        ids.add(current.block.id);
        current.children.forEach(visit);
    }

    visit(node);
    return ids;
}

function countSubtreeNodes(node: WarehouseBlockNode): number {
    return 1 + node.children.reduce((total, child) => total + countSubtreeNodes(child), 0);
}

function splitSideOptions(value: string): string[] | null {
    const options = value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);
    return options.length > 0 ? options : null;
}

function parseOptionalPosition(value: string): number | null {
    const normalized = value.trim();
    if (!normalized) {
        return null;
    }
    const parsed = Number.parseInt(normalized, 10);
    if (!Number.isFinite(parsed) || parsed < 0) {
        throw new Error("invalid-position");
    }
    return parsed;
}

function parseRequiredPositiveInteger(value: string): number {
    const parsed = Number.parseInt(value.trim(), 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
        throw new Error("invalid-positive-integer");
    }
    return parsed;
}

function getTemplateSideOptions(template: WarehouseTemplateResult | null): string[] {
    if (!template) {
        return [];
    }
    switch (template.sideConfig) {
        case "LR":
            return ["L", "R"];
        case "AB":
            return ["A", "B"];
        case "CUSTOM":
            return template.sideOptions ?? [];
        default:
            return [];
    }
}

function normalizeSideSelection(value: string, template: WarehouseTemplateResult | null): string | null {
    if (getTemplateSideOptions(template).length === 0) {
        return null;
    }
    if (!value || value === "__none__") {
        return null;
    }
    return value;
}

function getBlockDisplayLabel(block: WarehouseBlockResult, template: WarehouseTemplateResult | null, unknownLabel: string) {
    const parts = [template?.name ?? unknownLabel];
    if (block.identifier) {
        parts.push(block.identifier);
    }
    if (block.side) {
        parts.push(block.side);
    }
    return parts.join(" · ");
}

function useWarehouseFilters(layouts: WarehouseLayoutResult[], search: string, filter: LayoutFilter) {
    return useMemo(() => {
        const normalizedSearch = search.trim().toLowerCase();
        return layouts.filter((layout) => {
            const matchesSearch = !normalizedSearch
                || layout.name.toLowerCase().includes(normalizedSearch)
                || (layout.description ?? "").toLowerCase().includes(normalizedSearch);
            const matchesFilter = filter === "all"
                || (filter === "active" && layout.isActive)
                || (filter === "inactive" && !layout.isActive);
            return matchesSearch && matchesFilter;
        });
    }, [filter, layouts, search]);
}

export default function WarehouseLayoutsPage() {
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { pathname } = useLocation();
    const [searchParams, setSearchParams] = useSearchParams();

    const canManageLayouts = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_LAYOUT_MANAGE);
    const canActivateLayouts = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_LAYOUT_ACTIVATE);
    const canManageTemplates = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_TEMPLATE_MANAGE);
    const canEditBlocks = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_BLOCK_EDIT);
    const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

    const [layouts, setLayouts] = useState<WarehouseLayoutResult[]>([]);
    const [templates, setTemplates] = useState<WarehouseTemplateResult[]>([]);
    const [locationKinds, setLocationKinds] = useState<WarehouseLocationKindResult[]>([]);
    const [layoutTree, setLayoutTree] = useState<WarehouseBlockNode[]>([]);
    const [isLoadingLayouts, setIsLoadingLayouts] = useState(false);
    const [isLoadingTemplates, setIsLoadingTemplates] = useState(false);
    const [isLoadingLocationKinds, setIsLoadingLocationKinds] = useState(false);
    const [isLoadingTree, setIsLoadingTree] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [layoutDialogMode, setLayoutDialogMode] = useState<LayoutDialogMode>(null);
    const [layoutForm, setLayoutForm] = useState<LayoutFormState>(DEFAULT_LAYOUT_FORM);
    const [editingLayoutId, setEditingLayoutId] = useState<string | null>(null);
    const [templateDialogMode, setTemplateDialogMode] = useState<TemplateDialogMode>(null);
    const [templateForm, setTemplateForm] = useState<TemplateFormState>(DEFAULT_TEMPLATE_FORM);
    const [editingTemplateId, setEditingTemplateId] = useState<string | null>(null);
    const [locationKindDialogMode, setLocationKindDialogMode] = useState<LocationKindDialogMode>(null);
    const [locationKindForm, setLocationKindForm] = useState<LocationKindFormState>(DEFAULT_LOCATION_KIND_FORM);
    const [editingLocationKindId, setEditingLocationKindId] = useState<string | null>(null);
    const [isAddBlockOpen, setIsAddBlockOpen] = useState(false);
    const [blockForm, setBlockForm] = useState<BlockFormState>(DEFAULT_BLOCK_FORM);
    const [isPasteBlockOpen, setIsPasteBlockOpen] = useState(false);
    const [pasteForm, setPasteForm] = useState<PasteBlockFormState>(DEFAULT_PASTE_FORM);
    const [blockClipboard, setBlockClipboard] = useState<BlockClipboardState | null>(null);
    const [collapsedBlockIds, setCollapsedBlockIds] = useState<string[]>([]);
    const [selectedBlockTemplateId, setSelectedBlockTemplateId] = useState("");
    const [selectedBlockParentId, setSelectedBlockParentId] = useState("__root__");
    const [selectedBlockSide, setSelectedBlockSide] = useState("__none__");
    const [selectedBlockLocationKindId, setSelectedBlockLocationKindId] = useState("");
    const [isScanCodeCopied, setIsScanCodeCopied] = useState(false);
    const [isLocationLabelOpen, setIsLocationLabelOpen] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
    const [layoutSearch, setLayoutSearch] = useState("");
    const [layoutFilter, setLayoutFilter] = useState<LayoutFilter>("all");
    const [templateSearch, setTemplateSearch] = useState("");

    const selectedLayoutId = searchParams.get("layoutId");
    const selectedPath = parsePath(searchParams.get("path"));
    const pageTab: PageTab = pathname === PATHS.TENANT.warehouseTemplates(slug) ? "templates" : "builder";
    const activeLayout = layouts.find((layout) => layout.isActive) ?? null;
    const selectedLayout = layouts.find((layout) => layout.id === selectedLayoutId) ?? null;
    const displayedLayouts = useWarehouseFilters(layouts, layoutSearch, layoutFilter);
    const filteredTemplates = useMemo(() => {
        const normalizedSearch = templateSearch.trim().toLowerCase();
        return templates.filter((template) => {
            if (!normalizedSearch) {
                return true;
            }
            return template.name.toLowerCase().includes(normalizedSearch)
                || (template.description ?? "").toLowerCase().includes(normalizedSearch)
                || (template.iconName ?? "").toLowerCase().includes(normalizedSearch);
        });
    }, [templateSearch, templates]);

    const flattenedNodes = useMemo(() => flattenTree(layoutTree), [layoutTree]);
    const visibleFlattenedNodes = useMemo(
        () => flattenVisibleTree(layoutTree, new Set(collapsedBlockIds)),
        [collapsedBlockIds, layoutTree]
    );
    const selectedFlattenedNode = useMemo(
        () => findFlattenedNodeByPath(flattenedNodes, selectedPath),
        [flattenedNodes, selectedPath]
    );

    const selectedTemplate = useMemo(() => {
        if (!selectedFlattenedNode) {
            return null;
        }
        return templates.find((template) => template.id === selectedFlattenedNode.node.block.blockTemplateId) ?? null;
    }, [selectedFlattenedNode, templates]);
    const selectedEditorTemplate = useMemo(() => {
        if (!selectedBlockTemplateId) {
            return selectedTemplate;
        }
        return templates.find((template) => template.id === selectedBlockTemplateId) ?? selectedTemplate;
    }, [selectedBlockTemplateId, selectedTemplate, templates]);
    const selectedEditorSideOptions = useMemo(
        () => getTemplateSideOptions(selectedEditorTemplate),
        [selectedEditorTemplate]
    );
    const addBlockTemplate = useMemo(
        () => templates.find((template) => template.id === blockForm.blockTemplateId) ?? null,
        [blockForm.blockTemplateId, templates]
    );
    const addBlockSideOptions = useMemo(
        () => getTemplateSideOptions(addBlockTemplate),
        [addBlockTemplate]
    );
    const selectableParents = useMemo(() => {
        if (!selectedFlattenedNode) {
            return flattenedNodes;
        }

        const invalidIds = buildDescendantSet(selectedFlattenedNode.node);
        return flattenedNodes.filter((item) => !invalidIds.has(item.node.block.id));
    }, [flattenedNodes, selectedFlattenedNode]);
    const clipboardSourceNode = useMemo(
        () => blockClipboard
            ? flattenedNodes.find((item) => item.node.block.id === blockClipboard.sourceBlockId) ?? null
            : null,
        [blockClipboard, flattenedNodes]
    );
    const pasteSelectableParents = useMemo(() => {
        if (!clipboardSourceNode) {
            return flattenedNodes;
        }

        const invalidIds = buildDescendantSet(clipboardSourceNode.node);
        return flattenedNodes.filter((item) => !invalidIds.has(item.node.block.id));
    }, [clipboardSourceNode, flattenedNodes]);

    const blockBreadcrumbs = useMemo(() => {
        return selectedPath
            .map((id, index) => {
                const flattened = flattenedNodes.find((item) => item.node.block.id === id);
                if (!flattened) {
                    return null;
                }
                const template = templates.find((entry) => entry.id === flattened.node.block.blockTemplateId);
                return {
                    id,
                    label: template?.name ?? t("warehouse.builder.unknownBlock"),
                    path: joinPath(selectedPath.slice(0, index + 1)),
                };
            })
            .filter(Boolean) as Array<{ id: string; label: string; path: string }>;
    }, [flattenedNodes, selectedPath, t, templates]);

    const updateQuery = useCallback((updates: Record<string, string | null>) => {
        const next = new URLSearchParams(searchParams);
        Object.entries(updates).forEach(([key, value]) => {
            if (value === null || value === "") {
                next.delete(key);
            } else {
                next.set(key, value);
            }
        });
        setSearchParams(next, { replace: true });
    }, [searchParams, setSearchParams]);

    const loadLayouts = useCallback(async () => {
        setIsLoadingLayouts(true);
        try {
            const result = await listWarehouseLayouts(slug, { page: 0, size: 100 });
            setLayouts(result.content);
        } catch (error) {
            setPageError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.loadFailed"));
        } finally {
            setIsLoadingLayouts(false);
        }
    }, [slug, t]);

    const loadTemplates = useCallback(async () => {
        setIsLoadingTemplates(true);
        try {
            const result = await listWarehouseTemplates(slug, { page: 0, size: 100 });
            setTemplates(result.content);
        } catch (error) {
            setPageError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.loadFailed"));
        } finally {
            setIsLoadingTemplates(false);
        }
    }, [slug, t]);

    const loadLocationKinds = useCallback(async () => {
        setIsLoadingLocationKinds(true);
        try {
            const result = await listWarehouseLocationKinds(slug);
            setLocationKinds(result);
        } catch (error) {
            setPageError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.loadFailed"));
        } finally {
            setIsLoadingLocationKinds(false);
        }
    }, [slug, t]);

    const loadTree = useCallback(async (layoutId: string) => {
        setIsLoadingTree(true);
        try {
            const result = await listWarehouseLayoutBlocks(slug, layoutId);
            setLayoutTree(result);
        } catch (error) {
            setPageError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.loadFailed"));
            setLayoutTree([]);
        } finally {
            setIsLoadingTree(false);
        }
    }, [slug, t]);

    useEffect(() => {
        void Promise.all([loadLayouts(), loadTemplates(), loadLocationKinds()]);
    }, [loadLayouts, loadTemplates, loadLocationKinds]);

    useEffect(() => {
        if (!selectedLayoutId) {
            setLayoutTree([]);
            return;
        }
        void loadTree(selectedLayoutId);
    }, [loadTree, selectedLayoutId]);

    useEffect(() => {
        const validIds = new Set(flattenedNodes.map((item) => item.node.block.id));
        setCollapsedBlockIds((current) => {
            const next = current.filter((id) => validIds.has(id));
            if (next.length === current.length && next.every((id, index) => id === current[index])) {
                return current;
            }
            return next;
        });
    }, [flattenedNodes]);

    useEffect(() => {
        if (selectedPath.length <= 1) {
            return;
        }

        const ancestorIds = new Set(selectedPath.slice(0, -1));
        setCollapsedBlockIds((current) => {
            const next = current.filter((id) => !ancestorIds.has(id));
            if (next.length === current.length && next.every((id, index) => id === current[index])) {
                return current;
            }
            return next;
        });
    }, [selectedPath]);

    useEffect(() => {
        if (!selectedFlattenedNode) {
            setSelectedBlockTemplateId("");
            setSelectedBlockParentId("__root__");
            setSelectedBlockSide("__none__");
            setSelectedBlockLocationKindId(locationKinds[0]?.id ?? "");
            setIsScanCodeCopied(false);
            return;
        }

        setSelectedBlockTemplateId(selectedFlattenedNode.node.block.blockTemplateId);
        setSelectedBlockParentId(selectedFlattenedNode.node.block.parentId ?? "__root__");
        setSelectedBlockSide(selectedFlattenedNode.node.block.side ?? "__none__");
        setSelectedBlockLocationKindId(selectedFlattenedNode.node.block.locationKindId ?? locationKinds[0]?.id ?? "");
        setIsScanCodeCopied(false);
    }, [locationKinds, selectedFlattenedNode]);

    useEffect(() => {
        if (selectedFlattenedNode) {
            return;
        }
        if (!selectedBlockLocationKindId && locationKinds[0]?.id) {
            setSelectedBlockLocationKindId(locationKinds[0].id);
        }
    }, [locationKinds, selectedBlockLocationKindId, selectedFlattenedNode]);

    useEffect(() => {
        if (!selectedBlockLocationKindId) {
            return;
        }
        if (!locationKinds.some((item) => item.id === selectedBlockLocationKindId)) {
            setSelectedBlockLocationKindId(locationKinds[0]?.id ?? "");
        }
    }, [locationKinds, selectedBlockLocationKindId]);

    useEffect(() => {
        if (selectedEditorSideOptions.length === 0) {
            setSelectedBlockSide("__none__");
            return;
        }
        if (selectedBlockSide !== "__none__" && !selectedEditorSideOptions.includes(selectedBlockSide)) {
            setSelectedBlockSide("__none__");
        }
    }, [selectedBlockSide, selectedEditorSideOptions]);

    useEffect(() => {
        if (addBlockSideOptions.length === 0) {
            setBlockForm((current) => current.side === "__none__" ? current : { ...current, side: "__none__" });
            return;
        }
        if (blockForm.side !== "__none__" && !addBlockSideOptions.includes(blockForm.side)) {
            setBlockForm((current) => ({ ...current, side: "__none__" }));
        }
    }, [addBlockSideOptions, blockForm.side]);

    const handleOpenLayout = (layout: WarehouseLayoutResult) => {
        updateQuery({
            layoutId: layout.id,
            mode: layout.isActive ? "active" : "fork",
            path: null,
        });
    };

    const handleLayoutDialogOpen = (mode: Exclude<LayoutDialogMode, null>, layout?: WarehouseLayoutResult) => {
        setActionError(null);
        setLayoutDialogMode(mode);
        setEditingLayoutId(layout?.id ?? null);
        if (mode === "edit" && layout) {
            setLayoutForm({
                name: layout.name,
                description: layout.description ?? "",
                activate: layout.isActive,
            });
            return;
        }
        setLayoutForm(DEFAULT_LAYOUT_FORM);
    };

    const handleTemplateDialogOpen = (mode: Exclude<TemplateDialogMode, null>, template?: WarehouseTemplateResult) => {
        setActionError(null);
        setTemplateDialogMode(mode);
        setEditingTemplateId(template?.id ?? null);
        if (mode === "edit" && template) {
            setTemplateForm({
                name: template.name,
                identifierFormat: template.identifierFormat,
                sideConfig: template.sideConfig,
                sideOptions: template.sideOptions?.join(", ") ?? "",
                required: template.required,
                description: template.description ?? "",
                iconName: normalizeLucideIconName(template.iconName),
            });
            return;
        }
        setTemplateForm(DEFAULT_TEMPLATE_FORM);
    };

    const handleLocationKindDialogOpen = (mode: Exclude<LocationKindDialogMode, null>, locationKind?: WarehouseLocationKindResult) => {
        setActionError(null);
        setLocationKindDialogMode(mode);
        setEditingLocationKindId(locationKind?.id ?? null);
        setLocationKindForm({
            name: locationKind?.name ?? "",
        });
    };

    const handleCreateOrUpdateLayout = async (event: FormEvent) => {
        event.preventDefault();
        setActionError(null);
        setIsSubmitting(true);

        try {
            if (layoutDialogMode === "classic") {
                const created = await createClassicWarehousePreset(slug, {
                    name: layoutForm.name.trim(),
                    description: layoutForm.description.trim() || null,
                    activate: canActivateLayouts ? layoutForm.activate : false,
                });
                await loadLayouts();
                handleOpenLayout(created);
            } else {
                const payload: UpsertWarehouseLayoutRequest = {
                    name: layoutForm.name.trim(),
                    description: layoutForm.description.trim() || null,
                };
                const saved = layoutDialogMode === "edit" && editingLayoutId
                    ? await updateWarehouseLayout(slug, editingLayoutId, payload)
                    : await createWarehouseLayout(slug, payload);
                await loadLayouts();
                handleOpenLayout(saved);
            }
            setLayoutDialogMode(null);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCreateOrUpdateTemplate = async (event: FormEvent) => {
        event.preventDefault();
        setActionError(null);
        setIsSubmitting(true);

        try {
            const payload: UpsertWarehouseTemplateRequest = {
                name: templateForm.name.trim(),
                identifierFormat: templateForm.identifierFormat,
                sideConfig: templateForm.sideConfig,
                sideOptions: templateForm.sideConfig === "CUSTOM" ? splitSideOptions(templateForm.sideOptions) : null,
                required: templateForm.required,
                description: templateForm.description.trim() || null,
                iconName: templateForm.iconName || null,
            };
            if (templateDialogMode === "edit" && editingTemplateId) {
                await updateWarehouseTemplate(slug, editingTemplateId, payload);
            } else {
                await createWarehouseTemplate(slug, payload);
            }
            await loadTemplates();
            setTemplateDialogMode(null);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCreateOrUpdateLocationKind = async (event: FormEvent) => {
        event.preventDefault();
        setActionError(null);
        setIsSubmitting(true);

        try {
            const payload: UpsertWarehouseLocationKindRequest = {
                name: locationKindForm.name.trim(),
            };
            if (locationKindDialogMode === "edit" && editingLocationKindId) {
                await updateWarehouseLocationKind(slug, editingLocationKindId, payload);
            } else {
                await createWarehouseLocationKind(slug, payload);
            }
            await loadLocationKinds();
            setLocationKindDialogMode(null);
            setLocationKindForm(DEFAULT_LOCATION_KIND_FORM);
            setEditingLocationKindId(null);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleActivateLayout = async (layoutId: string) => {
        setActionError(null);
        try {
            await activateWarehouseLayout(slug, layoutId);
            await loadLayouts();
            if (selectedLayoutId === layoutId) {
                updateQuery({ mode: "active" });
            }
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        }
    };

    const handleDeactivateLayout = async (layoutId: string) => {
        setActionError(null);
        try {
            await deactivateWarehouseLayout(slug, layoutId);
            await loadLayouts();
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        }
    };

    const handleAddBlock = async (event: FormEvent) => {
        event.preventDefault();
        if (!selectedLayout) {
            return;
        }

        setActionError(null);
        let quantity: number;
        let position: number | null;
        try {
            quantity = parseRequiredPositiveInteger(blockForm.quantity);
        } catch {
            setActionError(t("warehouse.builder.quantityInvalid"));
            return;
        }

        try {
            position = parseOptionalPosition(blockForm.position);
        } catch {
            setActionError(t("warehouse.builder.positionInvalid"));
            return;
        }

        setIsSubmitting(true);
        try {
            const payload: AddWarehouseBlockRequest = {
                blockTemplateId: blockForm.blockTemplateId,
                parentId: blockForm.parentId === "__root__" ? null : blockForm.parentId,
                position,
                side: normalizeSideSelection(blockForm.side, addBlockTemplate),
            };
            const createdBlocks = quantity === 1
                ? [await addWarehouseLayoutBlock(slug, selectedLayout.id, payload)]
                : (await addWarehouseLayoutBlocks(slug, selectedLayout.id, {
                    ...payload,
                    count: quantity,
                })).createdBlocks;
            await loadTree(selectedLayout.id);
            const parentPath = payload.parentId
                ? flattenedNodes.find((item) => item.node.block.id === payload.parentId)?.path ?? []
                : [];
            const firstCreatedBlock = createdBlocks[0];
            updateQuery({ path: firstCreatedBlock ? joinPath([...parentPath, firstCreatedBlock.id]) : joinPath(parentPath) });
            setIsAddBlockOpen(false);
            setBlockForm(DEFAULT_BLOCK_FORM);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCopySelectedBlock = () => {
        if (!selectedFlattenedNode) {
            return;
        }
        const template = templates.find((entry) => entry.id === selectedFlattenedNode.node.block.blockTemplateId) ?? null;
        setBlockClipboard({
            sourceBlockId: selectedFlattenedNode.node.block.id,
            label: getBlockDisplayLabel(selectedFlattenedNode.node.block, template, t("warehouse.builder.unknownBlock")),
            totalNodes: countSubtreeNodes(selectedFlattenedNode.node),
        });
        setActionError(null);
    };

    const handleOpenPasteDialog = () => {
        if (!blockClipboard) {
            return;
        }

        const invalidTargetIds = clipboardSourceNode ? buildDescendantSet(clipboardSourceNode.node) : new Set<string>();
        const suggestedParentId = selectedFlattenedNode && !invalidTargetIds.has(selectedFlattenedNode.node.block.id)
            ? selectedFlattenedNode.node.block.id
            : clipboardSourceNode?.node.block.parentId ?? "__root__";

        setPasteForm({
            parentId: suggestedParentId,
            position: "",
            copies: "1",
        });
        setIsPasteBlockOpen(true);
    };

    const handlePasteBlock = async (event: FormEvent) => {
        event.preventDefault();
        if (!selectedLayout || !blockClipboard) {
            return;
        }

        setActionError(null);

        let copies: number;
        let position: number | null;
        try {
            copies = parseRequiredPositiveInteger(pasteForm.copies);
        } catch {
            setActionError(t("warehouse.builder.copiesInvalid"));
            return;
        }

        try {
            position = parseOptionalPosition(pasteForm.position);
        } catch {
            setActionError(t("warehouse.builder.positionInvalid"));
            return;
        }

        const targetParentId = pasteForm.parentId === "__root__" ? null : pasteForm.parentId;
        setIsSubmitting(true);
        try {
            const result = await copyWarehouseLayoutBlockSubtree(slug, selectedLayout.id, {
                sourceBlockId: blockClipboard.sourceBlockId,
                targetParentId,
                position,
                copies,
            });
            await loadTree(selectedLayout.id);
            const parentPath = targetParentId
                ? flattenedNodes.find((item) => item.node.block.id === targetParentId)?.path ?? []
                : [];
            const firstCreatedBlock = result.createdBlocks[0];
            updateQuery({ path: firstCreatedBlock ? joinPath([...parentPath, firstCreatedBlock.id]) : joinPath(parentPath) });
            setIsPasteBlockOpen(false);
            setPasteForm(DEFAULT_PASTE_FORM);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleSaveSelectedBlock = async () => {
        if (!selectedLayout || !selectedFlattenedNode) {
            return;
        }

        const blockId = selectedFlattenedNode.node.block.id;
        const nextParentId = selectedBlockParentId === "__root__" ? null : selectedBlockParentId;
        const nextSide = normalizeSideSelection(selectedBlockSide, selectedEditorTemplate);

        setActionError(null);
        setIsSubmitting(true);
        try {
            if (selectedBlockTemplateId !== selectedFlattenedNode.node.block.blockTemplateId) {
                await reassignWarehouseLayoutBlockTemplate(slug, selectedLayout.id, blockId, {
                    blockTemplateId: selectedBlockTemplateId,
                });
            }

            if (
                selectedBlockTemplateId !== selectedFlattenedNode.node.block.blockTemplateId
                || nextSide !== (selectedFlattenedNode.node.block.side ?? null)
                || selectedBlockLocationKindId !== (selectedFlattenedNode.node.block.locationKindId ?? "")
            ) {
                await updateWarehouseLayoutBlockMetadata(slug, selectedLayout.id, blockId, {
                    side: nextSide,
                    locationKindId: selectedBlockLocationKindId || null,
                });
            }

            if (
                nextParentId !== selectedFlattenedNode.node.block.parentId
            ) {
                const siblingCount = flattenedNodes.filter((item) => {
                    if (item.node.block.id === blockId) {
                        return false;
                    }
                    return item.node.block.parentId === nextParentId;
                }).length;
                await moveWarehouseLayoutBlock(slug, selectedLayout.id, blockId, {
                    parentId: nextParentId,
                    position: siblingCount,
                });
                const parentPath = nextParentId
                    ? flattenedNodes.find((item) => item.node.block.id === nextParentId)?.path ?? []
                    : [];
                updateQuery({ path: joinPath([...parentPath, blockId]) });
            }

            await loadTree(selectedLayout.id);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleQuickMove = async (direction: "up" | "down") => {
        if (!selectedLayout || !selectedFlattenedNode) {
            return;
        }

        const siblingNodes = flattenedNodes.filter((item) => item.node.block.parentId === selectedFlattenedNode.node.block.parentId);
        const currentIndex = siblingNodes.findIndex((item) => item.node.block.id === selectedFlattenedNode.node.block.id);
        const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
        if (targetIndex < 0 || targetIndex >= siblingNodes.length) {
            return;
        }

        const targetPosition = siblingNodes[targetIndex].node.block.position;
        setActionError(null);
        setIsSubmitting(true);
        try {
            await moveWarehouseLayoutBlock(slug, selectedLayout.id, selectedFlattenedNode.node.block.id, {
                parentId: selectedFlattenedNode.node.block.parentId,
                position: targetPosition,
            });
            await loadTree(selectedLayout.id);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const toggleBlockCollapsed = (blockId: string) => {
        setCollapsedBlockIds((current) => {
            if (current.includes(blockId)) {
                return current.filter((id) => id !== blockId);
            }
            return [...current, blockId];
        });
    };

    const confirmDelete = async () => {
        if (!deleteTarget) {
            return;
        }

        setActionError(null);
        setIsSubmitting(true);
        try {
            if (deleteTarget.type === "layout") {
                await deleteWarehouseLayout(slug, deleteTarget.id);
                await loadLayouts();
                if (selectedLayoutId === deleteTarget.id) {
                    updateQuery({ layoutId: null, path: null, mode: null });
                }
            }

            if (deleteTarget.type === "template") {
                await deleteWarehouseTemplate(slug, deleteTarget.id);
                await loadTemplates();
            }

            if (deleteTarget.type === "locationKind") {
                await deleteWarehouseLocationKind(slug, deleteTarget.id);
                await loadLocationKinds();
            }

            if (deleteTarget.type === "block" && selectedLayout) {
                await deleteWarehouseLayoutBlock(slug, selectedLayout.id, deleteTarget.id);
                await loadTree(selectedLayout.id);
                const parentPath = selectedPath.slice(0, -1);
                updateQuery({ path: parentPath.length > 0 ? joinPath(parentPath) : null });
            }

            setDeleteTarget(null);
        } catch (error) {
            setActionError(extractWarehouseErrorMessage(error) ?? t("warehouse.common.actionFailed"));
        } finally {
            setIsSubmitting(false);
        }
    };

    const selectedLayoutLabel = selectedLayout?.name ?? activeLayout?.name ?? t("warehouse.builder.noLayoutSelected");
    const isForkMode = Boolean(selectedLayout && activeLayout && selectedLayout.id !== activeLayout.id);

    return (
        <div className="space-y-6">
            <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div className="space-y-1">
                    <h1 className="text-xl font-semibold">{t("warehouse.layouts.pageTitle")}</h1>
                    <p className="text-sm text-muted-foreground">{t("warehouse.layouts.pageDescription")}</p>
                </div>
                {pageTab === "builder" && canManageLayouts ? (
                    <div className="flex flex-wrap gap-2">
                        <Button variant="outline" onClick={() => handleLayoutDialogOpen("classic")}>
                            <Sparkles className="h-4 w-4" />
                            {t("warehouse.layouts.createClassicAction")}
                        </Button>
                        <Button onClick={() => handleLayoutDialogOpen("create")}>
                            <Plus className="h-4 w-4" />
                            {t("warehouse.layouts.createAction")}
                        </Button>
                    </div>
                ) : null}
            </div>

            {pageError ? <p className="text-sm text-destructive">{pageError}</p> : null}
            {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}

            <div className="space-y-6">
                {pageTab === "builder" ? (
                    <Card>
                        <CardHeader>
                            <CardTitle>{t("warehouse.layouts.listTitle")}</CardTitle>
                            <CardDescription>{t("warehouse.layouts.listCount", { count: String(displayedLayouts.length) })}</CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="flex flex-col gap-3 md:flex-row">
                                <Input
                                    value={layoutSearch}
                                    onChange={(event) => setLayoutSearch(event.target.value)}
                                    placeholder={t("warehouse.layouts.searchPlaceholder")}
                                />
                                <Select value={layoutFilter} onValueChange={(value) => setLayoutFilter(value as LayoutFilter)}>
                                    <SelectTrigger className="w-full md:w-52">
                                        <SelectValue placeholder={t("warehouse.layouts.filterLabel")} />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="all">{t("warehouse.layouts.filterAll")}</SelectItem>
                                        <SelectItem value="active">{t("warehouse.layouts.filterActive")}</SelectItem>
                                        <SelectItem value="inactive">{t("warehouse.layouts.filterInactive")}</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>

                            {isLoadingLayouts ? (
                                <p className="text-sm text-muted-foreground">{t("warehouse.common.loading")}</p>
                            ) : displayedLayouts.length === 0 ? (
                                <p className="text-sm text-muted-foreground">{t("warehouse.layouts.empty")}</p>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>{t("warehouse.layouts.tableName")}</TableHead>
                                            <TableHead>{t("warehouse.layouts.tableDescription")}</TableHead>
                                            <TableHead>{t("warehouse.layouts.tableStatus")}</TableHead>
                                            <TableHead>{t("warehouse.common.actions")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {displayedLayouts.map((layout) => (
                                            <TableRow key={layout.id}>
                                                <TableCell className="font-medium">{layout.name}</TableCell>
                                                <TableCell>{layout.description || t("warehouse.common.emptyValue")}</TableCell>
                                                <TableCell>
                                                    <Badge variant="outline" className="rounded-none">
                                                        {layout.isActive ? t("warehouse.layouts.statusActive") : t("warehouse.layouts.statusInactive")}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell>
                                                    <div className="flex flex-wrap gap-2">
                                                        <Button size="sm" variant="outline" onClick={() => handleOpenLayout(layout)}>
                                                            {layout.isActive ? t("warehouse.layouts.openActiveAction") : t("warehouse.layouts.openForkAction")}
                                                        </Button>
                                                        {canManageLayouts ? (
                                                            <Button size="sm" variant="outline" onClick={() => handleLayoutDialogOpen("edit", layout)}>
                                                                <Pencil className="h-4 w-4" />
                                                                {t("warehouse.common.edit")}
                                                            </Button>
                                                        ) : null}
                                                        {canActivateLayouts && !layout.isActive ? (
                                                            <Button size="sm" variant="outline" onClick={() => handleActivateLayout(layout.id)}>
                                                                <Check className="h-4 w-4" />
                                                                {t("warehouse.layouts.activateAction")}
                                                            </Button>
                                                        ) : null}
                                                        {canActivateLayouts && layout.isActive ? (
                                                            <Button size="sm" variant="outline" onClick={() => handleDeactivateLayout(layout.id)}>
                                                                {t("warehouse.layouts.deactivateAction")}
                                                            </Button>
                                                        ) : null}
                                                        {canHardDelete ? (
                                                            <Button
                                                                size="sm"
                                                                variant="outline"
                                                                onClick={() => setDeleteTarget({ type: "layout", id: layout.id, label: layout.name })}
                                                            >
                                                                <Trash2 className="h-4 w-4" />
                                                                {t("warehouse.common.delete")}
                                                            </Button>
                                                        ) : null}
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                ) : null}

                <Card>
                    <CardHeader>
                        <CardTitle>{selectedLayoutLabel}</CardTitle>
                        <CardDescription>
                            {selectedLayout
                                ? isForkMode
                                    ? t("warehouse.builder.forkDescription", { active: activeLayout?.name ?? t("warehouse.common.none") })
                                    : t("warehouse.builder.activeDescription")
                                : t("warehouse.builder.selectDescription")}
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                                        {pageTab === "templates" ? (
                            <div className="space-y-6">
                                <div className="space-y-4">
                                    <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                                        <Input
                                            value={templateSearch}
                                            onChange={(event) => setTemplateSearch(event.target.value)}
                                            placeholder={t("warehouse.templates.searchPlaceholder")}
                                        />
                                        {canManageTemplates ? (
                                            <Button onClick={() => handleTemplateDialogOpen("create")}>
                                                <Plus className="h-4 w-4" />
                                                {t("warehouse.templates.createAction")}
                                            </Button>
                                        ) : null}
                                    </div>

                                    {isLoadingTemplates ? (
                                        <p className="text-sm text-muted-foreground">{t("warehouse.common.loading")}</p>
                                    ) : filteredTemplates.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("warehouse.templates.empty")}</p>
                                    ) : (
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <TableHead>{t("warehouse.templates.tableType")}</TableHead>
                                                    <TableHead>{t("warehouse.templates.tableIdentifier")}</TableHead>
                                                    <TableHead>{t("warehouse.templates.tableSideConfig")}</TableHead>
                                                    <TableHead>{t("warehouse.templates.tableIcon")}</TableHead>
                                                    <TableHead>{t("warehouse.common.actions")}</TableHead>
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {filteredTemplates.map((template) => {
                                                    const Icon = getLucideIcon(template.iconName);
                                                    const iconLabel = getLucideIconLabel(template.iconName);
                                                    return (
                                                        <TableRow key={template.id}>
                                                            <TableCell>
                                                                <div className="flex items-center gap-2 font-medium">
                                                                    <Icon className="h-4 w-4 text-primary" />
                                                                    <span>{template.name}</span>
                                                                </div>
                                                            </TableCell>
                                                            <TableCell>{template.identifierFormat}</TableCell>
                                                            <TableCell>{template.sideConfig}</TableCell>
                                                            <TableCell>
                                                                {iconLabel ? (
                                                                    <div className="flex items-center gap-2">
                                                                        <span className="flex size-7 items-center justify-center border bg-muted/30">
                                                                            <Icon className="h-4 w-4 text-primary" />
                                                                        </span>
                                                                        <span>{iconLabel}</span>
                                                                    </div>
                                                                ) : t("warehouse.common.none")}
                                                            </TableCell>
                                                            <TableCell>
                                                                <div className="flex flex-wrap gap-2">
                                                                    {canManageTemplates ? (
                                                                        <Button size="sm" variant="outline" onClick={() => handleTemplateDialogOpen("edit", template)}>
                                                                            <Pencil className="h-4 w-4" />
                                                                            {t("warehouse.common.edit")}
                                                                        </Button>
                                                                    ) : null}
                                                                    {canHardDelete ? (
                                                                        <Button
                                                                            size="sm"
                                                                            variant="outline"
                                                                            onClick={() => setDeleteTarget({ type: "template", id: template.id, label: template.name })}
                                                                        >
                                                                            <Trash2 className="h-4 w-4" />
                                                                            {t("warehouse.common.delete")}
                                                                        </Button>
                                                                    ) : null}
                                                                </div>
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                            </TableBody>
                                        </Table>
                                    )}
                                </div>

                                <div className="space-y-4 border-t pt-4">
                                    <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                                        <div>
                                            <p className="font-medium">{t("warehouse.locationKinds.sectionTitle")}</p>
                                            <p className="text-sm text-muted-foreground">{t("warehouse.locationKinds.sectionDescription")}</p>
                                        </div>
                                        {canManageTemplates ? (
                                            <Button onClick={() => handleLocationKindDialogOpen("create")}>
                                                <Plus className="h-4 w-4" />
                                                {t("warehouse.locationKinds.createAction")}
                                            </Button>
                                        ) : null}
                                    </div>

                                    {isLoadingLocationKinds ? (
                                        <p className="text-sm text-muted-foreground">{t("warehouse.common.loading")}</p>
                                    ) : locationKinds.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("warehouse.locationKinds.empty")}</p>
                                    ) : (
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <TableHead>{t("warehouse.locationKinds.tableName")}</TableHead>
                                                    <TableHead>{t("warehouse.locationKinds.tableOrder")}</TableHead>
                                                    <TableHead>{t("warehouse.common.actions")}</TableHead>
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {locationKinds.map((locationKind) => {
                                                    return (
                                                        <TableRow key={locationKind.id}>
                                                            <TableCell className="font-medium">{locationKind.name}</TableCell>
                                                            <TableCell>{locationKind.sortOrder + 1}</TableCell>
                                                            <TableCell>
                                                                <div className="flex flex-wrap gap-2">
                                                                    {canManageTemplates ? (
                                                                        <Button size="sm" variant="outline" onClick={() => handleLocationKindDialogOpen("edit", locationKind)}>
                                                                            <Pencil className="h-4 w-4" />
                                                                            {t("warehouse.common.edit")}
                                                                        </Button>
                                                                    ) : null}
                                                                    {canHardDelete ? (
                                                                        <Button
                                                                            size="sm"
                                                                            variant="outline"
                                                                            onClick={() => setDeleteTarget({ type: "locationKind", id: locationKind.id, label: locationKind.name })}
                                                                        >
                                                                            <Trash2 className="h-4 w-4" />
                                                                            {t("warehouse.common.delete")}
                                                                        </Button>
                                                                    ) : null}
                                                                </div>
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                            </TableBody>
                                        </Table>
                                    )}
                                </div>
                            </div>
                        ) : !selectedLayout ? (
                            <div className="space-y-3">
                                <p className="text-sm text-muted-foreground">{t("warehouse.builder.noSelectionMessage")}</p>
                                {activeLayout ? (
                                    <Button asChild>
                                        <Link to={PATHS.TENANT.warehouseLayouts(slug, { layoutId: activeLayout.id, mode: "active" })}>
                                            {t("warehouse.builder.openActiveLayoutAction")}
                                        </Link>
                                    </Button>
                                ) : null}
                            </div>
                        ) : (
                            <div className="space-y-6">
                                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                                    <Link className="transition-colors hover:text-foreground" to={PATHS.TENANT.warehouseLayouts(slug)}>
                                        {t("warehouse.breadcrumb.layouts")}
                                    </Link>
                                    <span>/</span>
                                    <button
                                        className="transition-colors hover:text-foreground"
                                        type="button"
                                        onClick={() => updateQuery({ layoutId: selectedLayout.id, path: null })}
                                    >
                                        {selectedLayout.name}
                                    </button>
                                    {blockBreadcrumbs.map((crumb) => (
                                        <div key={crumb.id} className="flex items-center gap-2">
                                            <span>/</span>
                                            <button
                                                className="transition-colors hover:text-foreground"
                                                type="button"
                                                onClick={() => updateQuery({ path: crumb.path })}
                                            >
                                                {crumb.label}
                                            </button>
                                        </div>
                                    ))}
                                </div>

                                <div className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                                    <Card>
                                        <CardHeader>
                                            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                                                <div>
                                                    <CardTitle>{t("warehouse.builder.treeTitle")}</CardTitle>
                                                    <CardDescription>{t("warehouse.builder.treeDescription")}</CardDescription>
                                                </div>
                                                {canEditBlocks ? (
                                                    <div className="flex flex-wrap gap-2">
                                                        {blockClipboard ? (
                                                            <Button variant="outline" onClick={handleOpenPasteDialog}>
                                                                <ClipboardPaste className="h-4 w-4" />
                                                                {t("warehouse.builder.pasteSubtreeAction")}
                                                            </Button>
                                                        ) : null}
                                                        <Button
                                                            variant="outline"
                                                            onClick={() => {
                                                                setBlockForm({
                                                                    blockTemplateId: templates[0]?.id ?? "",
                                                                    parentId: selectedFlattenedNode?.node.block.id ?? "__root__",
                                                                    position: "",
                                                                    quantity: "1",
                                                                    side: "__none__",
                                                                });
                                                                setIsAddBlockOpen(true);
                                                            }}
                                                        >
                                                            <Plus className="h-4 w-4" />
                                                            {t("warehouse.builder.addBlockAction")}
                                                        </Button>
                                                    </div>
                                                ) : null}
                                            </div>
                                        </CardHeader>
                                        <CardContent>
                                            {blockClipboard ? (
                                                <div className="mb-4 flex items-center justify-between gap-3 border bg-muted/20 px-3 py-2 text-sm">
                                                    <div>
                                                        <p className="font-medium">{t("warehouse.builder.clipboardReady")}</p>
                                                        <p className="text-xs text-muted-foreground">
                                                            {t("warehouse.builder.clipboardSummary", {
                                                                label: blockClipboard.label,
                                                                count: String(blockClipboard.totalNodes),
                                                            })}
                                                        </p>
                                                    </div>
                                                    <Button variant="ghost" size="sm" onClick={() => setBlockClipboard(null)}>
                                                        {t("warehouse.builder.clearClipboardAction")}
                                                    </Button>
                                                </div>
                                            ) : null}
                                            {isLoadingTree ? (
                                                <p className="text-sm text-muted-foreground">{t("warehouse.common.loading")}</p>
                                            ) : layoutTree.length === 0 ? (
                                                <p className="text-sm text-muted-foreground">{t("warehouse.builder.emptyTree")}</p>
                                            ) : (
                                                <div className="space-y-1">
                                                    {visibleFlattenedNodes.map((item) => {
                                                        const template = templates.find((entry) => entry.id === item.node.block.blockTemplateId);
                                                        const Icon = getLucideIcon(template?.iconName);
                                                        const isSelected = selectedFlattenedNode?.node.block.id === item.node.block.id;
                                                        const hasChildren = item.node.children.length > 0;
                                                        const isCollapsed = collapsedBlockIds.includes(item.node.block.id);
                                                        return (
                                                            <div
                                                                key={item.node.block.id}
                                                                className={[
                                                                    "flex w-full items-center gap-2 rounded-none border px-3 py-2 text-start text-sm transition-colors",
                                                                    isSelected ? "border-primary bg-primary/5 text-foreground" : "border-border hover:bg-accent",
                                                                ].join(" ")}
                                                                style={{ paddingInlineStart: `${0.75 + item.depth * 1}rem` }}
                                                            >
                                                                {hasChildren ? (
                                                                    <Button
                                                                        type="button"
                                                                        variant="ghost"
                                                                        size="sm"
                                                                        className="h-7 w-7 shrink-0 px-0"
                                                                        aria-label={isCollapsed ? t("warehouse.builder.expandBlockAction") : t("warehouse.builder.collapseBlockAction")}
                                                                        onClick={(event) => {
                                                                            event.stopPropagation();
                                                                            toggleBlockCollapsed(item.node.block.id);
                                                                        }}
                                                                    >
                                                                        {isCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                                                                    </Button>
                                                                ) : (
                                                                    <span className="w-7 shrink-0" />
                                                                )}
                                                                <button
                                                                    type="button"
                                                                    className="flex min-w-0 flex-1 items-center gap-2 text-start"
                                                                    onClick={() => updateQuery({
                                                                        path: joinPath(item.path),
                                                                        layoutId: selectedLayout.id,
                                                                        mode: selectedLayout.isActive ? "active" : "fork",
                                                                    })}
                                                                >
                                                                    <Icon className="h-4 w-4 shrink-0 text-primary" />
                                                                    <span className="flex-1 truncate">{getBlockDisplayLabel(item.node.block, template ?? null, t("warehouse.builder.unknownBlock"))}</span>
                                                                    {!hasChildren ? (
                                                                        <Badge variant="outline" className="shrink-0 rounded-none px-1 py-0 text-[10px]">
                                                                            {item.node.block.locationKindName ?? t("warehouse.common.none")}
                                                                        </Badge>
                                                                    ) : null}
                                                                </button>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            )}
                                        </CardContent>
                                    </Card>

                                    <Card>
                                        <CardHeader>
                                            <CardTitle>{t("warehouse.builder.inspectorTitle")}</CardTitle>
                                            <CardDescription>
                                                {selectedFlattenedNode
                                                    ? t("warehouse.builder.inspectorDescription")
                                                    : t("warehouse.builder.inspectorEmpty")}
                                            </CardDescription>
                                        </CardHeader>
                                        <CardContent className="space-y-4">
                                            {selectedFlattenedNode && selectedTemplate ? (
                                                <>
                                                    <div className="flex items-center gap-3">
                                                        {(() => {
                                                            const Icon = getLucideIcon(selectedTemplate.iconName);
                                                            return <Icon className="h-5 w-5 text-primary" />;
                                                        })()}
                                                        <div>
                                                            <p className="font-medium">{selectedTemplate.name}</p>
                                                            <p className="text-xs text-muted-foreground">
                                                                {selectedFlattenedNode.node.block.side
                                                                    ? t("warehouse.builder.blockMetaWithSide", {
                                                                        identifier: selectedFlattenedNode.node.block.identifier ?? t("warehouse.builder.identifierUnavailable"),
                                                                        side: selectedFlattenedNode.node.block.side,
                                                                    })
                                                                    : t("warehouse.builder.blockMeta", {
                                                                        identifier: selectedFlattenedNode.node.block.identifier ?? t("warehouse.builder.identifierUnavailable"),
                                                                    })}
                                                            </p>
                                                        </div>
                                                    </div>

                                                    <div className="grid gap-4 md:grid-cols-2">
                                                        <div className="space-y-2">
                                                            <Label htmlFor="selected-template">{t("warehouse.builder.templateLabel")}</Label>
                                                            <Select value={selectedBlockTemplateId} onValueChange={setSelectedBlockTemplateId}>
                                                                <SelectTrigger id="selected-template" className="w-full">
                                                                    <SelectValue placeholder={t("warehouse.builder.templatePlaceholder")} />
                                                                </SelectTrigger>
                                                                <SelectContent>
                                                                    {templates.map((template) => (
                                                                        <SelectItem key={template.id} value={template.id}>{template.name}</SelectItem>
                                                                    ))}
                                                                </SelectContent>
                                                            </Select>
                                                        </div>
                                                    </div>

                                                    <div className="space-y-2">
                                                        <Label htmlFor="selected-parent">{t("warehouse.builder.parentLabel")}</Label>
                                                        <Select value={selectedBlockParentId} onValueChange={setSelectedBlockParentId}>
                                                            <SelectTrigger id="selected-parent" className="w-full">
                                                                <SelectValue placeholder={t("warehouse.builder.parentPlaceholder")} />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="__root__">{t("warehouse.builder.rootParent")}</SelectItem>
                                                                {selectableParents.map((item) => {
                                                                    const template = templates.find((entry) => entry.id === item.node.block.blockTemplateId);
                                                                    return (
                                                                        <SelectItem key={item.node.block.id} value={item.node.block.id}>
                                                                            {`${"- ".repeat(item.depth)}${getBlockDisplayLabel(item.node.block, template ?? null, t("warehouse.builder.unknownBlock"))}`}
                                                                        </SelectItem>
                                                                    );
                                                                })}
                                                            </SelectContent>
                                                        </Select>
                                                    </div>

                                                    {selectedEditorSideOptions.length > 0 ? (
                                                        <div className="space-y-2">
                                                            <Label htmlFor="selected-side">{t("warehouse.builder.sideLabel")}</Label>
                                                            <Select value={selectedBlockSide} onValueChange={setSelectedBlockSide}>
                                                                <SelectTrigger id="selected-side" className="w-full">
                                                                    <SelectValue placeholder={t("warehouse.builder.sidePlaceholder")} />
                                                                </SelectTrigger>
                                                                <SelectContent>
                                                                    <SelectItem value="__none__">{t("warehouse.builder.sideEmpty")}</SelectItem>
                                                                    {selectedEditorSideOptions.map((sideOption) => (
                                                                        <SelectItem key={sideOption} value={sideOption}>{sideOption}</SelectItem>
                                                                    ))}
                                                                </SelectContent>
                                                            </Select>
                                                        </div>
                                                    ) : null}

                                                    {selectedFlattenedNode.node.children.length === 0 ? (
                                                        <div className="space-y-2">
                                                            <Label htmlFor="selected-kind">{t("warehouse.locationKind.label")}</Label>
                                                            <Select value={selectedBlockLocationKindId} onValueChange={setSelectedBlockLocationKindId}>
                                                                <SelectTrigger id="selected-kind" className="w-full">
                                                                    <SelectValue placeholder={t("warehouse.locationKind.placeholder")} />
                                                                </SelectTrigger>
                                                                <SelectContent>
                                                                    {locationKinds.map((kind) => (
                                                                        <SelectItem key={kind.id} value={kind.id}>{kind.name}</SelectItem>
                                                                    ))}
                                                                </SelectContent>
                                                            </Select>
                                                        </div>
                                                    ) : null}

                                                    {selectedFlattenedNode.node.block.scanCode ? (
                                                        <div className="space-y-2">
                                                            <Label>{t("warehouse.scanCode.label")}</Label>
                                                            <div className="flex items-center gap-2">
                                                                <Input
                                                                    readOnly
                                                                    value={selectedFlattenedNode.node.block.scanCode}
                                                                    className="font-mono text-sm"
                                                                />
                                                                <Button
                                                                    type="button"
                                                                    variant="outline"
                                                                    size="sm"
                                                                    className="shrink-0"
                                                                    aria-label={t("warehouse.scanCode.copy")}
                                                                    onClick={() => {
                                                                        void navigator.clipboard.writeText(selectedFlattenedNode.node.block.scanCode!);
                                                                        setIsScanCodeCopied(true);
                                                                        setTimeout(() => setIsScanCodeCopied(false), 2000);
                                                                    }}
                                                                >
                                                                    {isScanCodeCopied ? t("warehouse.scanCode.copied") : <Copy className="h-4 w-4" />}
                                                                </Button>
                                                                <Button
                                                                    type="button"
                                                                    variant="outline"
                                                                    size="sm"
                                                                    className="shrink-0"
                                                                    onClick={() => setIsLocationLabelOpen(true)}
                                                                >
                                                                    {t("labels.printLabel")}
                                                                </Button>
                                                            </div>
                                                        </div>
                                                    ) : null}

                                                    <div className="flex flex-wrap gap-2">
                                                        {canEditBlocks ? (
                                                            <>
                                                                <Button onClick={() => void handleSaveSelectedBlock()} disabled={isSubmitting}>
                                                                    {t("warehouse.common.save")}
                                                                </Button>
                                                                <Button variant="outline" onClick={() => void handleQuickMove("up")} disabled={isSubmitting}>
                                                                    <ArrowUp className="h-4 w-4" />
                                                                    {t("warehouse.builder.moveUpAction")}
                                                                </Button>
                                                                <Button variant="outline" onClick={() => void handleQuickMove("down")} disabled={isSubmitting}>
                                                                    <ArrowDown className="h-4 w-4" />
                                                                    {t("warehouse.builder.moveDownAction")}
                                                                </Button>
                                                                <Button variant="outline" onClick={handleCopySelectedBlock} disabled={isSubmitting}>
                                                                    <Copy className="h-4 w-4" />
                                                                    {t("warehouse.builder.copySubtreeAction")}
                                                                </Button>
                                                                {blockClipboard ? (
                                                                    <Button variant="outline" onClick={handleOpenPasteDialog} disabled={isSubmitting}>
                                                                        <ClipboardPaste className="h-4 w-4" />
                                                                        {t("warehouse.builder.pasteSubtreeAction")}
                                                                    </Button>
                                                                ) : null}
                                                            </>
                                                        ) : null}
                                                        {canHardDelete ? (
                                                            <Button
                                                                variant="outline"
                                                                onClick={() => setDeleteTarget({ type: "block", id: selectedFlattenedNode.node.block.id, label: selectedTemplate.name })}
                                                            >
                                                                <Trash2 className="h-4 w-4" />
                                                                {t("warehouse.common.delete")}
                                                            </Button>
                                                        ) : null}
                                                    </div>
                                                </>
                                            ) : (
                                                <p className="text-sm text-muted-foreground">{t("warehouse.builder.inspectorEmptyMessage")}</p>
                                            )}
                                        </CardContent>
                                    </Card>
                                </div>
                            </div>
                        )}
                    </CardContent>
                </Card>
            </div>

            <Dialog open={layoutDialogMode !== null} onOpenChange={(open) => !open && setLayoutDialogMode(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {layoutDialogMode === "edit"
                                ? t("warehouse.layouts.editDialogTitle")
                                : layoutDialogMode === "classic"
                                    ? t("warehouse.layouts.classicDialogTitle")
                                    : t("warehouse.layouts.createDialogTitle")}
                        </DialogTitle>
                        <DialogDescription>
                            {layoutDialogMode === "classic"
                                ? t("warehouse.layouts.classicDialogDescription")
                                : t("warehouse.layouts.dialogDescription")}
                        </DialogDescription>
                    </DialogHeader>
                    <form className="space-y-4" onSubmit={handleCreateOrUpdateLayout}>
                        <div className="space-y-2">
                            <Label htmlFor="layout-name">{t("warehouse.layouts.nameLabel")}</Label>
                            <Input
                                id="layout-name"
                                value={layoutForm.name}
                                onChange={(event) => setLayoutForm((current) => ({ ...current, name: event.target.value }))}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="layout-description">{t("warehouse.layouts.descriptionLabel")}</Label>
                            <Textarea
                                id="layout-description"
                                value={layoutForm.description}
                                onChange={(event) => setLayoutForm((current) => ({ ...current, description: event.target.value }))}
                            />
                        </div>
                        {layoutDialogMode === "classic" && canActivateLayouts ? (
                            <label className="flex items-center gap-2 text-sm">
                                <input
                                    checked={layoutForm.activate}
                                    type="checkbox"
                                    onChange={(event) => setLayoutForm((current) => ({ ...current, activate: event.target.checked }))}
                                />
                                <span>{t("warehouse.layouts.activateNowLabel")}</span>
                            </label>
                        ) : null}
                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setLayoutDialogMode(null)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting}>
                                {layoutDialogMode === "edit" ? t("warehouse.common.save") : t("warehouse.common.create")}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog
                open={templateDialogMode !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setTemplateDialogMode(null);
                    }
                }}
            >
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {templateDialogMode === "edit" ? t("warehouse.templates.editDialogTitle") : t("warehouse.templates.createDialogTitle")}
                        </DialogTitle>
                        <DialogDescription>{t("warehouse.templates.dialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <form className="space-y-4" onSubmit={handleCreateOrUpdateTemplate}>
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <Label htmlFor="template-name">{t("warehouse.templates.nameLabel")}</Label>
                                <Input
                                    id="template-name"
                                    value={templateForm.name}
                                    onChange={(event) => setTemplateForm((current) => ({ ...current, name: event.target.value }))}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="template-icon">{t("warehouse.templates.iconLabel")}</Label>
                                <LucideIconPicker
                                    id="template-icon"
                                    label={t("warehouse.templates.iconLabel")}
                                    value={templateForm.iconName}
                                    onChange={(iconName) => setTemplateForm((current) => ({ ...current, iconName }))}
                                    placeholder={t("warehouse.templates.iconSearchPlaceholder")}
                                    emptyMessage={t("warehouse.templates.iconEmpty")}
                                />
                            </div>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <Label htmlFor="template-format">{t("warehouse.templates.identifierLabel")}</Label>
                                <Select
                                    value={templateForm.identifierFormat}
                                    onValueChange={(value) => setTemplateForm((current) => ({ ...current, identifierFormat: value as WarehouseIdentifierFormat }))}
                                >
                                    <SelectTrigger id="template-format" className="w-full">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="NUMERIC">NUMERIC</SelectItem>
                                        <SelectItem value="ALPHA">ALPHA</SelectItem>
                                        <SelectItem value="CUSTOM">CUSTOM</SelectItem>
                                        <SelectItem value="FREE_TEXT">FREE_TEXT</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="template-side-config">{t("warehouse.templates.sideConfigLabel")}</Label>
                                <Select
                                    value={templateForm.sideConfig}
                                    onValueChange={(value) => setTemplateForm((current) => ({ ...current, sideConfig: value as WarehouseSideConfig }))}
                                >
                                    <SelectTrigger id="template-side-config" className="w-full">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="NONE">NONE</SelectItem>
                                        <SelectItem value="LR">LR</SelectItem>
                                        <SelectItem value="AB">AB</SelectItem>
                                        <SelectItem value="CUSTOM">CUSTOM</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                        </div>

                        {templateForm.sideConfig === "CUSTOM" ? (
                            <div className="space-y-2">
                                <Label htmlFor="template-side-options">{t("warehouse.templates.sideOptionsLabel")}</Label>
                                <Input
                                    id="template-side-options"
                                    value={templateForm.sideOptions}
                                    onChange={(event) => setTemplateForm((current) => ({ ...current, sideOptions: event.target.value }))}
                                    placeholder={t("warehouse.templates.sideOptionsPlaceholder")}
                                />
                            </div>
                        ) : null}

                        <div className="space-y-2">
                            <Label htmlFor="template-description">{t("warehouse.templates.descriptionLabel")}</Label>
                            <Textarea
                                id="template-description"
                                value={templateForm.description}
                                onChange={(event) => setTemplateForm((current) => ({ ...current, description: event.target.value }))}
                            />
                        </div>

                        <label className="flex items-center gap-2 text-sm">
                            <input
                                checked={templateForm.required}
                                type="checkbox"
                                onChange={(event) => setTemplateForm((current) => ({ ...current, required: event.target.checked }))}
                            />
                            <span>{t("warehouse.templates.requiredLabel")}</span>
                        </label>

                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setTemplateDialogMode(null)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting}>
                                {templateDialogMode === "edit" ? t("warehouse.common.save") : t("warehouse.common.create")}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog
                open={locationKindDialogMode !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setLocationKindDialogMode(null);
                    }
                }}
            >
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {locationKindDialogMode === "edit"
                                ? t("warehouse.locationKinds.editDialogTitle")
                                : t("warehouse.locationKinds.createDialogTitle")}
                        </DialogTitle>
                        <DialogDescription>{t("warehouse.locationKinds.dialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <form className="space-y-4" onSubmit={handleCreateOrUpdateLocationKind}>
                        <div className="space-y-2">
                            <Label htmlFor="location-kind-name">{t("warehouse.locationKinds.nameLabel")}</Label>
                            <Input
                                id="location-kind-name"
                                value={locationKindForm.name}
                                onChange={(event) => setLocationKindForm({ name: event.target.value })}
                            />
                        </div>
                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setLocationKindDialogMode(null)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting}>
                                {locationKindDialogMode === "edit" ? t("warehouse.common.save") : t("warehouse.common.create")}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog open={isAddBlockOpen} onOpenChange={setIsAddBlockOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("warehouse.builder.addBlockDialogTitle")}</DialogTitle>
                        <DialogDescription>{t("warehouse.builder.addBlockDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <form className="space-y-4" onSubmit={handleAddBlock}>
                        <div className="space-y-2">
                            <Label htmlFor="block-template">{t("warehouse.builder.templateLabel")}</Label>
                            <Select
                                value={blockForm.blockTemplateId}
                                onValueChange={(value) => setBlockForm((current) => ({ ...current, blockTemplateId: value }))}
                            >
                                <SelectTrigger id="block-template" className="w-full">
                                    <SelectValue placeholder={t("warehouse.builder.templatePlaceholder")} />
                                </SelectTrigger>
                                <SelectContent>
                                    {templates.map((template) => (
                                        <SelectItem key={template.id} value={template.id}>{template.name}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="block-parent">{t("warehouse.builder.parentLabel")}</Label>
                            <Select
                                value={blockForm.parentId}
                                onValueChange={(value) => setBlockForm((current) => ({ ...current, parentId: value }))}
                            >
                                <SelectTrigger id="block-parent" className="w-full">
                                    <SelectValue placeholder={t("warehouse.builder.parentPlaceholder")} />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="__root__">{t("warehouse.builder.rootParent")}</SelectItem>
                                    {flattenedNodes.map((item) => {
                                        const template = templates.find((entry) => entry.id === item.node.block.blockTemplateId);
                                        return (
                                            <SelectItem key={item.node.block.id} value={item.node.block.id}>
                                                {`${"- ".repeat(item.depth)}${template?.name ?? t("warehouse.builder.unknownBlock")}`}
                                            </SelectItem>
                                        );
                                    })}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <Label htmlFor="block-position">{t("warehouse.builder.positionLabel")}</Label>
                                <Input
                                    id="block-position"
                                    min={0}
                                    type="number"
                                    value={blockForm.position}
                                    onChange={(event) => setBlockForm((current) => ({ ...current, position: event.target.value }))}
                                    placeholder={t("warehouse.builder.positionPlaceholder")}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="block-quantity">{t("warehouse.builder.quantityLabel")}</Label>
                                <Input
                                    id="block-quantity"
                                    min={1}
                                    type="number"
                                    value={blockForm.quantity}
                                    onChange={(event) => setBlockForm((current) => ({ ...current, quantity: event.target.value }))}
                                    placeholder={t("warehouse.builder.quantityPlaceholder")}
                                />
                            </div>
                        </div>
                        {addBlockSideOptions.length > 0 ? (
                            <div className="space-y-2">
                                <Label htmlFor="block-side">{t("warehouse.builder.sideLabel")}</Label>
                                <Select
                                    value={blockForm.side}
                                    onValueChange={(value) => setBlockForm((current) => ({ ...current, side: value }))}
                                >
                                    <SelectTrigger id="block-side" className="w-full">
                                        <SelectValue placeholder={t("warehouse.builder.sidePlaceholder")} />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="__none__">{t("warehouse.builder.sideEmpty")}</SelectItem>
                                        {addBlockSideOptions.map((sideOption) => (
                                            <SelectItem key={sideOption} value={sideOption}>{sideOption}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                        ) : null}
                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setIsAddBlockOpen(false)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting || !blockForm.blockTemplateId}>
                                {t("warehouse.builder.addBlockSubmitAction")}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog open={isPasteBlockOpen} onOpenChange={setIsPasteBlockOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("warehouse.builder.pasteSubtreeDialogTitle")}</DialogTitle>
                        <DialogDescription>{t("warehouse.builder.pasteSubtreeDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <form className="space-y-4" onSubmit={handlePasteBlock}>
                        {blockClipboard ? (
                            <div className="border bg-muted/20 px-3 py-2 text-sm">
                                <p className="font-medium">{blockClipboard.label}</p>
                                <p className="text-xs text-muted-foreground">
                                    {t("warehouse.builder.clipboardSummary", {
                                        label: blockClipboard.label,
                                        count: String(blockClipboard.totalNodes),
                                    })}
                                </p>
                            </div>
                        ) : null}
                        <div className="space-y-2">
                            <Label htmlFor="paste-parent">{t("warehouse.builder.pasteTargetLabel")}</Label>
                            <Select
                                value={pasteForm.parentId}
                                onValueChange={(value) => setPasteForm((current) => ({ ...current, parentId: value }))}
                            >
                                <SelectTrigger id="paste-parent" className="w-full">
                                    <SelectValue placeholder={t("warehouse.builder.parentPlaceholder")} />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="__root__">{t("warehouse.builder.rootParent")}</SelectItem>
                                    {pasteSelectableParents.map((item) => {
                                        const template = templates.find((entry) => entry.id === item.node.block.blockTemplateId);
                                        return (
                                            <SelectItem key={item.node.block.id} value={item.node.block.id}>
                                                {`${"- ".repeat(item.depth)}${getBlockDisplayLabel(item.node.block, template ?? null, t("warehouse.builder.unknownBlock"))}`}
                                            </SelectItem>
                                        );
                                    })}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <Label htmlFor="paste-position">{t("warehouse.builder.positionLabel")}</Label>
                                <Input
                                    id="paste-position"
                                    min={0}
                                    type="number"
                                    value={pasteForm.position}
                                    onChange={(event) => setPasteForm((current) => ({ ...current, position: event.target.value }))}
                                    placeholder={t("warehouse.builder.positionPlaceholder")}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="paste-copies">{t("warehouse.builder.copiesLabel")}</Label>
                                <Input
                                    id="paste-copies"
                                    min={1}
                                    type="number"
                                    value={pasteForm.copies}
                                    onChange={(event) => setPasteForm((current) => ({ ...current, copies: event.target.value }))}
                                    placeholder={t("warehouse.builder.copiesPlaceholder")}
                                />
                            </div>
                        </div>
                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setIsPasteBlockOpen(false)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting || !blockClipboard}>
                                {t("warehouse.builder.pasteSubtreeSubmitAction")}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("warehouse.common.deleteDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("warehouse.common.deleteDialogDescription", { label: deleteTarget?.label ?? "" })}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isSubmitting}>{t("warehouse.common.cancel")}</AlertDialogCancel>
                        <AlertDialogAction onClick={() => void confirmDelete()} disabled={isSubmitting}>
                            {t("warehouse.common.delete")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {selectedFlattenedNode?.node.block.scanCode ? (
                <Dialog open={isLocationLabelOpen} onOpenChange={setIsLocationLabelOpen}>
                    <DialogContent>
                        <DialogHeader>
                            <DialogTitle>{t("labels.printLabel")}</DialogTitle>
                        </DialogHeader>
                        <LocationLabel
                            scanCode={selectedFlattenedNode.node.block.scanCode}
                            fullCode={selectedFlattenedNode.node.block.fullCode}
                            locationKindName={selectedFlattenedNode.node.block.locationKindName}
                            pathLabel={selectedFlattenedNode.node.block.identifier}
                        />
                    </DialogContent>
                </Dialog>
            ) : null}
        </div>
    );
}
