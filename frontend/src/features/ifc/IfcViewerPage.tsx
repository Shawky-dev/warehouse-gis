import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import * as OBC from "@thatopen/components";
import { Box, Trash2, Upload } from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  deleteIfcModel,
  fetchIfcModelBuffer,
  listIfcModels,
  uploadIfcModel,
  type IfcModelSummary,
} from "@/features/ifc/ifcApi";
import { Button } from "@/shared/components/ui/button";
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

const FRAGMENT_WORKER_URL = "/fragments-worker.mjs";

export default function IfcViewerPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canManage = hasPermission(TENANT_PERMISSIONS.IFC_MANAGE);

  const [models, setModels] = useState<IfcModelSummary[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [selectedModelId, setSelectedModelId] = useState<string | null>(null);
  const [viewerReady, setViewerReady] = useState(false);
  const [modelLoading, setModelLoading] = useState(false);
  const [modelError, setModelError] = useState<string | null>(null);

  const [isUploading, setIsUploading] = useState(false);
  const uploadInputRef = useRef<HTMLInputElement>(null);

  const [deleteTarget, setDeleteTarget] = useState<IfcModelSummary | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // @thatopen/components refs
  const containerRef = useRef<HTMLDivElement>(null);
  const componentsRef = useRef<OBC.Components | null>(null);
  const worldRef = useRef<OBC.World | null>(null);
  const ifcLoaderRef = useRef<OBC.IfcLoader | null>(null);
  const fragmentsRef = useRef<OBC.FragmentsManager | null>(null);
  const fragmentsReadyRef = useRef(false);

  const loadModels = useCallback(async () => {
    setListLoading(true);
    setListError(null);
    try {
      setModels(await listIfcModels(slug));
    } catch {
      setListError(t("ifc.loadModelsError"));
    } finally {
      setListLoading(false);
    }
  }, [slug, t]);

  useEffect(() => {
    void loadModels();
  }, [loadModels]);

  // Initialise the @thatopen/components 3D world
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const components = new OBC.Components();
    componentsRef.current = components;

    const worlds = components.get(OBC.Worlds);
    const world = worlds.create<
      OBC.SimpleScene,
      OBC.OrthoPerspectiveCamera,
      OBC.SimpleRenderer
    >();

    world.scene = new OBC.SimpleScene(components);
    world.scene.setup();
    world.renderer = new OBC.SimpleRenderer(components, container);
    world.camera = new OBC.OrthoPerspectiveCamera(components);
    components.init();

    worldRef.current = world;

    // Set up FragmentsManager with local worker (version-matched to @thatopen/fragments)
    const fragments = components.get(OBC.FragmentsManager);
    fragmentsRef.current = fragments;

    try {
      fragments.init(FRAGMENT_WORKER_URL);
      fragmentsReadyRef.current = true;

      fragments.list.onItemSet.add(({ value: model }) => {
        model.useCamera(world.camera.three);
        world.scene.three.add(model.object);
        fragments.core?.update(true);
      });

      fragments.core?.models.materials.list.onItemSet.add(({ value: material }) => {
        if (!("isLodMaterial" in material && material.isLodMaterial)) {
          const mat = material as Record<string, unknown>;
          mat.polygonOffset = true;
          mat.polygonOffsetUnits = 1;
          mat.polygonOffsetFactor = Math.random();
        }
      });
    } catch {
      // Init failed — model loading will fail gracefully
    }

    world.camera.controls.addEventListener("update", () => {
      fragments.core?.update();
    });

    // Set up IFC loader with local WASM
    const ifcLoader = components.get(OBC.IfcLoader);
    ifcLoaderRef.current = ifcLoader;

    void ifcLoader
      .setup({ autoSetWasm: false, wasm: { path: "/web-ifc/", absolute: false } })
      .then(() => setViewerReady(true));

    return () => {
      components.dispose();
      componentsRef.current = null;
      worldRef.current = null;
      ifcLoaderRef.current = null;
      fragmentsRef.current = null;
      fragmentsReadyRef.current = false;
      setViewerReady(false);
    };
  }, []);

  const loadModel = useCallback(
    async (modelId: string) => {
      const ifcLoader = ifcLoaderRef.current;
      const fragments = fragmentsRef.current;
      if (!ifcLoader || !fragments || !fragmentsReadyRef.current) return;

      setModelLoading(true);
      setModelError(null);
      setSelectedModelId(modelId);

      // Dispose previously loaded models
      for (const model of fragments.list.values()) {
        await model.dispose();
      }

      try {
        const buffer = await fetchIfcModelBuffer(slug, modelId);
        await ifcLoader.load(new Uint8Array(buffer), true, modelId);
      } catch {
        setModelError(t("ifc.loadModelError"));
      } finally {
        setModelLoading(false);
      }
    },
    [slug, t]
  );

  const handleUpload = useCallback(
    async (file: File) => {
      setIsUploading(true);
      try {
        await uploadIfcModel(slug, file);
        await loadModels();
      } catch {
        // Error shown via toast or inline — keep it simple
      } finally {
        setIsUploading(false);
        if (uploadInputRef.current) uploadInputRef.current.value = "";
      }
    },
    [slug, loadModels]
  );

  const handleDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await deleteIfcModel(slug, deleteTarget.id);
      if (selectedModelId === deleteTarget.id) setSelectedModelId(null);
      setDeleteTarget(null);
      await loadModels();
    } catch {
      // silent — list will not refresh on failure
    } finally {
      setIsDeleting(false);
    }
  }, [slug, deleteTarget, selectedModelId, loadModels]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      {/* Header */}
      <div className="shrink-0 border-b px-6 py-4">
        <h1 className="text-xl font-semibold">{t("ifc.pageTitle")}</h1>
        <p className="text-muted-foreground text-sm">{t("ifc.pageDescription")}</p>
      </div>

      {/* Body */}
      <div className="flex min-h-0 flex-1">
        {/* Left panel — model list */}
        <aside className="flex w-72 shrink-0 flex-col gap-2 border-r p-4">
          {canManage && (
            <>
              <Button
                size="sm"
                className="w-full gap-2"
                disabled={isUploading}
                onClick={() => uploadInputRef.current?.click()}
              >
                <Upload className="size-4" />
                {isUploading ? t("ifc.uploading") : t("ifc.uploadButton")}
              </Button>
              <input
                ref={uploadInputRef}
                type="file"
                accept=".ifc"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) void handleUpload(file);
                }}
              />
            </>
          )}

          {listLoading && (
            <p className="text-muted-foreground py-4 text-center text-sm">{t("ifc.loadingModels")}</p>
          )}
          {listError && (
            <p className="text-destructive py-4 text-center text-sm">{listError}</p>
          )}
          {!listLoading && !listError && models.length === 0 && (
            <p className="text-muted-foreground py-4 text-center text-sm">{t("ifc.noModels")}</p>
          )}

          <ul className="flex flex-col gap-1 overflow-y-auto">
            {models.map((m) => (
              <li
                key={m.id}
                className={`group flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                  selectedModelId === m.id
                    ? "bg-primary text-primary-foreground"
                    : "hover:bg-accent cursor-pointer"
                }`}
                onClick={() => void loadModel(m.id)}
              >
                <Box className="size-4 shrink-0" />
                <span className="min-w-0 flex-1 truncate">{m.originalName}</span>
                {canManage && (
                  <button
                    className={`ml-auto shrink-0 rounded p-0.5 opacity-0 transition-opacity group-hover:opacity-100 ${
                      selectedModelId === m.id ? "hover:bg-primary-foreground/20" : "hover:bg-muted"
                    }`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeleteTarget(m);
                    }}
                    title="Delete"
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        </aside>

        {/* Right panel — 3D canvas */}
        <div className="relative flex-1">
          {/* Canvas container */}
          <div ref={containerRef} className="absolute inset-0" />

          {/* Overlays */}
          {!viewerReady && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/80">
              <p className="text-muted-foreground text-sm">{t("ifc.loadingViewer")}</p>
            </div>
          )}
          {viewerReady && !selectedModelId && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <p className="text-muted-foreground rounded-md bg-background/70 px-4 py-2 text-sm">
                {t("ifc.selectPrompt")}
              </p>
            </div>
          )}
          {modelLoading && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/60">
              <p className="text-muted-foreground text-sm">{t("ifc.loadingModel")}</p>
            </div>
          )}
          {modelError && (
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
              <p className="text-destructive rounded-md bg-background px-4 py-2 text-sm shadow">
                {modelError}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Delete confirm dialog */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("ifc.deleteConfirmTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("ifc.deleteConfirmDescription").replace("{name}", deleteTarget?.originalName ?? "")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>{t("ifc.deleteCancel")}</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleDelete()} disabled={isDeleting}>
              {t("ifc.deleteConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
