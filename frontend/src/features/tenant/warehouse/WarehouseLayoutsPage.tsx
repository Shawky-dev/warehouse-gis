import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type FormEvent,
} from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
    ArrowDown,
    ArrowUp,
    Check,
    FolderTree,
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
    createClassicWarehousePreset,
    createWarehouseLayout,
    createWarehouseTemplate,
    deleteWarehouseLayout,
    deleteWarehouseLayoutBlock,
    deleteWarehouseTemplate,
    deactivateWarehouseLayout,
    extractWarehouseErrorMessage,
    listWarehouseLayoutBlocks,
    listWarehouseLayouts,
    listWarehouseTemplates,
    moveWarehouseLayoutBlock,
    reassignWarehouseLayoutBlockTemplate,
    updateWarehouseLayout,
    updateWarehouseTemplate,
} from "@/features/tenant/api/warehouseApi";
import type {
    AddWarehouseBlockRequest,
    UpsertWarehouseLayoutRequest,
    UpsertWarehouseTemplateRequest,
    WarehouseBlockNode,
    WarehouseFlattenedNode,
    WarehouseIdentifierFormat,
    WarehouseLayoutResult,
    WarehouseSideConfig,
    WarehouseTemplateResult,
} from "@/features/tenant/types/warehouse";
import { getWarehouseIcon, WAREHOUSE_ICON_OPTIONS } from "@/features/tenant/warehouse/icon-registry";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
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

type LayoutFilter = "all" | "active" | "inactive";
type LayoutDialogMode = "create" | "edit" | "classic" | null;
type TemplateDialogMode = "create" | "edit" | null;
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
}

type DeleteTarget =
    | { type: "layout"; id: string; label: string }
    | { type: "template"; id: string; label: string }
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

function normalizePosition(value: string): number | null {
    if (!value.trim()) {
        return null;
    }

    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 0) {
        return null;
    }

    return parsed;
}

function splitSideOptions(value: string): string[] | null {
    const options = value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);
    return options.length > 0 ? options : null;
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
    const [searchParams, setSearchParams] = useSearchParams();

    const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
    const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

    const [layouts, setLayouts] = useState<WarehouseLayoutResult[]>([]);
    const [templates, setTemplates] = useState<WarehouseTemplateResult[]>([]);
    const [layoutTree, setLayoutTree] = useState<WarehouseBlockNode[]>([]);
    const [isLoadingLayouts, setIsLoadingLayouts] = useState(false);
    const [isLoadingTemplates, setIsLoadingTemplates] = useState(false);
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
    const [isAddBlockOpen, setIsAddBlockOpen] = useState(false);
    const [blockForm, setBlockForm] = useState<BlockFormState>(DEFAULT_BLOCK_FORM);
    const [selectedBlockTemplateId, setSelectedBlockTemplateId] = useState("");
    const [selectedBlockParentId, setSelectedBlockParentId] = useState("__root__");
    const [selectedBlockPosition, setSelectedBlockPosition] = useState("0");
    const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
    const [layoutSearch, setLayoutSearch] = useState("");
    const [layoutFilter, setLayoutFilter] = useState<LayoutFilter>("all");
    const [templateSearch, setTemplateSearch] = useState("");

    const selectedLayoutId = searchParams.get("layoutId");
    const selectedPath = parsePath(searchParams.get("path"));
    const pageTab = (searchParams.get("tab") as PageTab | null) ?? "builder";
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

    const selectableParents = useMemo(() => {
        if (!selectedFlattenedNode) {
            return flattenedNodes;
        }

        const invalidIds = buildDescendantSet(selectedFlattenedNode.node);
        return flattenedNodes.filter((item) => !invalidIds.has(item.node.block.id));
    }, [flattenedNodes, selectedFlattenedNode]);

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
        void Promise.all([loadLayouts(), loadTemplates()]);
    }, [loadLayouts, loadTemplates]);

    useEffect(() => {
        if (!selectedLayoutId) {
            setLayoutTree([]);
            return;
        }
        void loadTree(selectedLayoutId);
    }, [loadTree, selectedLayoutId]);

    useEffect(() => {
        if (!selectedFlattenedNode) {
            setSelectedBlockTemplateId("");
            setSelectedBlockParentId("__root__");
            setSelectedBlockPosition("0");
            return;
        }

        setSelectedBlockTemplateId(selectedFlattenedNode.node.block.blockTemplateId);
        setSelectedBlockParentId(selectedFlattenedNode.node.block.parentId ?? "__root__");
        setSelectedBlockPosition(String(selectedFlattenedNode.node.block.position));
    }, [selectedFlattenedNode]);

    const handleOpenLayout = (layout: WarehouseLayoutResult) => {
        updateQuery({
            layoutId: layout.id,
            mode: layout.isActive ? "active" : "fork",
            path: null,
            tab: "builder",
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
                iconName: template.iconName ?? "LayoutGrid",
            });
            return;
        }
        setTemplateForm(DEFAULT_TEMPLATE_FORM);
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
                    activate: layoutForm.activate,
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
        setIsSubmitting(true);
        try {
            const payload: AddWarehouseBlockRequest = {
                blockTemplateId: blockForm.blockTemplateId,
                parentId: blockForm.parentId === "__root__" ? null : blockForm.parentId,
                position: normalizePosition(blockForm.position),
            };
            const created = await addWarehouseLayoutBlock(slug, selectedLayout.id, payload);
            await loadTree(selectedLayout.id);
            const parentPath = payload.parentId
                ? flattenedNodes.find((item) => item.node.block.id === payload.parentId)?.path ?? []
                : [];
            updateQuery({ path: joinPath([...parentPath, created.id]) });
            setIsAddBlockOpen(false);
            setBlockForm(DEFAULT_BLOCK_FORM);
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
        const nextPosition = normalizePosition(selectedBlockPosition);
        if (nextPosition === null) {
            setActionError(t("warehouse.builder.positionRequired"));
            return;
        }

        setActionError(null);
        setIsSubmitting(true);
        try {
            if (selectedBlockTemplateId !== selectedFlattenedNode.node.block.blockTemplateId) {
                await reassignWarehouseLayoutBlockTemplate(slug, selectedLayout.id, blockId, {
                    blockTemplateId: selectedBlockTemplateId,
                });
            }

            if (
                nextParentId !== selectedFlattenedNode.node.block.parentId
                || nextPosition !== selectedFlattenedNode.node.block.position
            ) {
                await moveWarehouseLayoutBlock(slug, selectedLayout.id, blockId, {
                    parentId: nextParentId,
                    position: nextPosition,
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
        setSelectedBlockPosition(String(targetPosition));
        setSelectedBlockParentId(selectedFlattenedNode.node.block.parentId ?? "__root__");
        await handleSaveSelectedBlock();
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
                {canEdit ? (
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

            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,1.35fr)]">
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
                                                    {canEdit ? (
                                                        <Button size="sm" variant="outline" onClick={() => handleLayoutDialogOpen("edit", layout)}>
                                                            <Pencil className="h-4 w-4" />
                                                            {t("warehouse.common.edit")}
                                                        </Button>
                                                    ) : null}
                                                    {canEdit && !layout.isActive ? (
                                                        <Button size="sm" variant="outline" onClick={() => handleActivateLayout(layout.id)}>
                                                            <Check className="h-4 w-4" />
                                                            {t("warehouse.layouts.activateAction")}
                                                        </Button>
                                                    ) : null}
                                                    {canEdit && layout.isActive ? (
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

                <Card>
                    <CardHeader>
                        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                            <div>
                                <CardTitle>{selectedLayoutLabel}</CardTitle>
                                <CardDescription>
                                    {selectedLayout
                                        ? isForkMode
                                            ? t("warehouse.builder.forkDescription", { active: activeLayout?.name ?? t("warehouse.common.none") })
                                            : t("warehouse.builder.activeDescription")
                                        : t("warehouse.builder.selectDescription")}
                                </CardDescription>
                            </div>
                            <div className="flex gap-2">
                                <Button
                                    variant={pageTab === "builder" ? "default" : "outline"}
                                    onClick={() => updateQuery({ tab: "builder" })}
                                >
                                    <FolderTree className="h-4 w-4" />
                                    {t("warehouse.builder.builderTab")}
                                </Button>
                                <Button
                                    variant={pageTab === "templates" ? "default" : "outline"}
                                    onClick={() => updateQuery({ tab: "templates" })}
                                >
                                    {t("warehouse.builder.templatesTab")}
                                </Button>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        {pageTab === "templates" ? (
                            <div className="space-y-4">
                                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                                    <Input
                                        value={templateSearch}
                                        onChange={(event) => setTemplateSearch(event.target.value)}
                                        placeholder={t("warehouse.templates.searchPlaceholder")}
                                    />
                                    {canEdit ? (
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
                                                const Icon = getWarehouseIcon(template.iconName);
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
                                                        <TableCell>{template.iconName || t("warehouse.common.none")}</TableCell>
                                                        <TableCell>
                                                            <div className="flex flex-wrap gap-2">
                                                                {canEdit ? (
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
                        ) : !selectedLayout ? (
                            <div className="space-y-3">
                                <p className="text-sm text-muted-foreground">{t("warehouse.builder.noSelectionMessage")}</p>
                                {activeLayout ? (
                                    <Button asChild>
                                        <Link to={PATHS.TENANT.warehouseLayouts(slug, { layoutId: activeLayout.id, mode: "active", tab: "builder" })}>
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
                                        onClick={() => updateQuery({ layoutId: selectedLayout.id, path: null, tab: "builder" })}
                                    >
                                        {selectedLayout.name}
                                    </button>
                                    {blockBreadcrumbs.map((crumb) => (
                                        <div key={crumb.id} className="flex items-center gap-2">
                                            <span>/</span>
                                            <button
                                                className="transition-colors hover:text-foreground"
                                                type="button"
                                                onClick={() => updateQuery({ path: crumb.path, tab: "builder" })}
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
                                                {canEdit ? (
                                                    <Button
                                                        variant="outline"
                                                        onClick={() => {
                                                            setBlockForm({
                                                                blockTemplateId: templates[0]?.id ?? "",
                                                                parentId: selectedFlattenedNode?.node.block.id ?? "__root__",
                                                                position: "",
                                                            });
                                                            setIsAddBlockOpen(true);
                                                        }}
                                                    >
                                                        <Plus className="h-4 w-4" />
                                                        {t("warehouse.builder.addBlockAction")}
                                                    </Button>
                                                ) : null}
                                            </div>
                                        </CardHeader>
                                        <CardContent>
                                            {isLoadingTree ? (
                                                <p className="text-sm text-muted-foreground">{t("warehouse.common.loading")}</p>
                                            ) : layoutTree.length === 0 ? (
                                                <p className="text-sm text-muted-foreground">{t("warehouse.builder.emptyTree")}</p>
                                            ) : (
                                                <div className="space-y-1">
                                                    {flattenedNodes.map((item) => {
                                                        const template = templates.find((entry) => entry.id === item.node.block.blockTemplateId);
                                                        const Icon = getWarehouseIcon(template?.iconName);
                                                        const isSelected = selectedFlattenedNode?.node.block.id === item.node.block.id;
                                                        return (
                                                            <button
                                                                key={item.node.block.id}
                                                                type="button"
                                                                className={[
                                                                    "flex w-full items-center gap-2 rounded-none border px-3 py-2 text-start text-sm transition-colors",
                                                                    isSelected ? "border-primary bg-primary/5 text-foreground" : "border-border hover:bg-accent",
                                                                ].join(" ")}
                                                                style={{ paddingInlineStart: `${0.75 + item.depth * 1}rem` }}
                                                                onClick={() => updateQuery({
                                                                    path: joinPath(item.path),
                                                                    layoutId: selectedLayout.id,
                                                                    mode: selectedLayout.isActive ? "active" : "fork",
                                                                    tab: "builder",
                                                                })}
                                                            >
                                                                <Icon className="h-4 w-4 shrink-0 text-primary" />
                                                                <span className="flex-1">{template?.name ?? t("warehouse.builder.unknownBlock")}</span>
                                                                <span className="text-xs text-muted-foreground">#{item.node.block.position}</span>
                                                            </button>
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
                                                            const Icon = getWarehouseIcon(selectedTemplate.iconName);
                                                            return <Icon className="h-5 w-5 text-primary" />;
                                                        })()}
                                                        <div>
                                                            <p className="font-medium">{selectedTemplate.name}</p>
                                                            <p className="text-xs text-muted-foreground">
                                                                {t("warehouse.builder.blockMeta", {
                                                                    identifier: selectedTemplate.identifierFormat,
                                                                    position: String(selectedFlattenedNode.node.block.position),
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
                                                        <div className="space-y-2">
                                                            <Label htmlFor="selected-position">{t("warehouse.builder.positionLabel")}</Label>
                                                            <Input
                                                                id="selected-position"
                                                                type="number"
                                                                min={0}
                                                                value={selectedBlockPosition}
                                                                onChange={(event) => setSelectedBlockPosition(event.target.value)}
                                                            />
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
                                                                            {`${"- ".repeat(item.depth)}${template?.name ?? t("warehouse.builder.unknownBlock")}`}
                                                                        </SelectItem>
                                                                    );
                                                                })}
                                                            </SelectContent>
                                                        </Select>
                                                    </div>

                                                    <div className="flex flex-wrap gap-2">
                                                        {canEdit ? (
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
                        {layoutDialogMode === "classic" ? (
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

            <Dialog open={templateDialogMode !== null} onOpenChange={(open) => !open && setTemplateDialogMode(null)}>
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
                                <Select value={templateForm.iconName} onValueChange={(value) => setTemplateForm((current) => ({ ...current, iconName: value }))}>
                                    <SelectTrigger id="template-icon" className="w-full">
                                        <SelectValue placeholder={t("warehouse.templates.iconPlaceholder")} />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {WAREHOUSE_ICON_OPTIONS.map((option) => (
                                            <SelectItem key={option.name} value={option.name}>{option.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
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
                        <div className="space-y-2">
                            <Label htmlFor="block-position">{t("warehouse.builder.positionLabelOptional")}</Label>
                            <Input
                                id="block-position"
                                min={0}
                                type="number"
                                value={blockForm.position}
                                onChange={(event) => setBlockForm((current) => ({ ...current, position: event.target.value }))}
                            />
                        </div>
                        <DialogFooter>
                            <Button type="button" variant="outline" onClick={() => setIsAddBlockOpen(false)}>
                                {t("warehouse.common.cancel")}
                            </Button>
                            <Button type="submit" disabled={isSubmitting || !blockForm.blockTemplateId}>
                                {t("warehouse.builder.addBlockAction")}
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
        </div>
    );
}