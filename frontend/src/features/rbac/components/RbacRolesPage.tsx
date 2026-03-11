import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import type {
  RbacPermissionOption,
  RbacRoleDetails,
  RbacRolesPageAdapter,
} from "@/features/rbac/types";
import { useI18n } from "@/i18n";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Checkbox } from "@/components/ui/checkbox";
import { ScrollArea } from "@/components/ui/scroll-area";

interface PermissionGroup {
  key: string;
  title: string;
  permissions: RbacPermissionOption[];
}

const TOKEN_LABELS: Record<string, string> = {
  audit: "Audit",
  block: "Block",
  categories: "Categories",
  layout: "Layout",
  products: "Products",
  roles: "Roles",
  suppliers: "Suppliers",
  template: "Template",
  tenants: "Tenants",
  uoms: "UOMs",
  users: "Users",
  warehouse: "Warehouse",
};

function toTitleCase(value: string): string {
  return value
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatPermissionSegment(segment: string): string {
  return TOKEN_LABELS[segment] ?? toTitleCase(segment);
}

function getPermissionGroupKey(permissionCode: string): string {
  const segments = permissionCode.split(".").filter(Boolean);
  if (segments.length <= 2) {
    return segments.slice(0, -1).join(".") || permissionCode;
  }
  return segments.slice(1, -1).join(".");
}

function getPermissionGroupTitle(groupKey: string): string {
  return groupKey
    .split(".")
    .filter(Boolean)
    .map(formatPermissionSegment)
    .join(" / ");
}

function groupPermissions(permissions: RbacPermissionOption[], query: string): PermissionGroup[] {
  const normalizedQuery = query.trim().toLowerCase();
  const grouped = new Map<string, PermissionGroup>();

  permissions
    .slice()
    .sort((left, right) => left.code.localeCompare(right.code))
    .forEach((permission) => {
      const key = getPermissionGroupKey(permission.code);
      const title = getPermissionGroupTitle(key);
      const searchText = `${title} ${permission.code} ${permission.description ?? ""}`.toLowerCase();

      if (normalizedQuery && !searchText.includes(normalizedQuery)) {
        return;
      }

      if (!grouped.has(key)) {
        grouped.set(key, { key, title, permissions: [] });
      }
      grouped.get(key)?.permissions.push(permission);
    });

  return Array.from(grouped.values()).sort((left, right) => left.title.localeCompare(right.title));
}

interface PermissionSelectorProps {
  groups: PermissionGroup[];
  query: string;
  selectedPermissions: string[];
  searchId: string;
  searchLabel: string;
  searchPlaceholder: string;
  emptyMessage: string;
  disabled?: boolean;
  onQueryChange: (value: string) => void;
  onToggle: (permissionCode: string) => void;
}

function PermissionSelector({
  groups,
  query,
  selectedPermissions,
  searchId,
  searchLabel,
  searchPlaceholder,
  emptyMessage,
  disabled = false,
  onQueryChange,
  onToggle,
}: PermissionSelectorProps) {
  return (
    <div className="space-y-3">
      <div className="space-y-1">
        <Label htmlFor={searchId}>{searchLabel}</Label>
        <Input
          id={searchId}
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder={searchPlaceholder}
        />
      </div>
      <ScrollArea className="h-72 rounded-md border p-3">
        {groups.length === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyMessage}</p>
        ) : (
          <div className="space-y-3">
            {groups.map((group) => (
              <section key={group.key} className="rounded-md border bg-muted/20 p-3">
                <div className="mb-3 flex items-center justify-between gap-3 border-b pb-2">
                  <div>
                    <h3 className="text-sm font-semibold">{group.title}</h3>
                    <p className="text-xs text-muted-foreground">{group.permissions.length} permissions</p>
                  </div>
                </div>
                <div className="space-y-2">
                  {group.permissions.map((permission) => (
                    <label key={permission.code} className="flex items-start gap-2 text-sm">
                      <Checkbox
                        checked={selectedPermissions.includes(permission.code)}
                        onCheckedChange={() => onToggle(permission.code)}
                        disabled={disabled}
                      />
                      <span>
                        <span className="font-medium">{permission.code}</span>
                        {permission.description ? (
                          <span className="ms-2 text-xs text-muted-foreground">{permission.description}</span>
                        ) : null}
                      </span>
                    </label>
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}

interface RbacRolesPageProps {
  adapter: RbacRolesPageAdapter;
}

export default function RbacRolesPage({ adapter }: RbacRolesPageProps) {
  const { t } = useI18n();
  const [roles, setRoles] = useState<RbacRoleDetails[]>([]);
  const [permissions, setPermissions] = useState<RbacPermissionOption[]>([]);
  const [selectedRoleCode, setSelectedRoleCode] = useState<string>("");
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [locked, setLocked] = useState(false);
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createCode, setCreateCode] = useState("");
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createLocked, setCreateLocked] = useState(false);
  const [createPermissions, setCreatePermissions] = useState<string[]>([]);
  const [permissionSearch, setPermissionSearch] = useState("");
  const [createPermissionSearch, setCreatePermissionSearch] = useState("");

  const deferredPermissionSearch = useDeferredValue(permissionSearch);
  const deferredCreatePermissionSearch = useDeferredValue(createPermissionSearch);

  const selectedRole = useMemo(
    () => roles.find((role) => role.code === selectedRoleCode) ?? null,
    [roles, selectedRoleCode]
  );
  const isAdminRole = selectedRole?.code === "ADMIN";
  const permissionGroups = useMemo(
    () => groupPermissions(permissions, deferredPermissionSearch),
    [permissions, deferredPermissionSearch]
  );
  const createPermissionGroups = useMemo(
    () => groupPermissions(permissions, deferredCreatePermissionSearch),
    [permissions, deferredCreatePermissionSearch]
  );

  const syncSelectedRoleForm = useCallback((role: RbacRoleDetails | null) => {
    if (!role) {
      setName("");
      setDescription("");
      setLocked(false);
      setSelectedPermissions([]);
      return;
    }
    setName(role.name);
    setDescription(role.description ?? "");
    setLocked(role.locked);
    setSelectedPermissions(role.permissionCodes);
  }, []);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setPageError(null);

    try {
      const [rolesResult, permissionsResult] = await Promise.all([
        adapter.listRoles(),
        adapter.listPermissions(),
      ]);
      setRoles(rolesResult);
      setPermissions(permissionsResult);

      const nextSelectedCode = selectedRoleCode || rolesResult[0]?.code || "";
      setSelectedRoleCode(nextSelectedCode);
      const nextSelectedRole = rolesResult.find((role) => role.code === nextSelectedCode) ?? null;
      syncSelectedRoleForm(nextSelectedRole);
    } catch (error) {
      setPageError(adapter.extractErrorMessage(error) ?? t("roles.loadFailed"));
    } finally {
      setIsLoading(false);
    }
  }, [adapter, selectedRoleCode, syncSelectedRoleForm, t]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    syncSelectedRoleForm(selectedRole);
  }, [selectedRole, syncSelectedRoleForm]);

  useEffect(() => {
    if (!isCreateOpen) {
      return;
    }
    setCreateCode("");
    setCreateName("");
    setCreateDescription("");
    setCreateLocked(false);
    setCreatePermissions([]);
    setCreatePermissionSearch("");
    setFormError(null);
  }, [isCreateOpen]);

  useEffect(() => {
    setPermissionSearch("");
  }, [selectedRoleCode]);

  const togglePermission = (permissionCode: string) => {
    setSelectedPermissions((current) =>
      current.includes(permissionCode)
        ? current.filter((item) => item !== permissionCode)
        : [...current, permissionCode]
    );
  };

  const toggleCreatePermission = (permissionCode: string) => {
    setCreatePermissions((current) =>
      current.includes(permissionCode)
        ? current.filter((item) => item !== permissionCode)
        : [...current, permissionCode]
    );
  };

  const saveRole = async () => {
    if (!selectedRole) {
      return;
    }
    if (isAdminRole) {
      setFormError(t("roles.adminLockedWarning"));
      return;
    }
    if (!name.trim()) {
      setFormError(t("roles.validationNameRequired"));
      return;
    }

    setIsSaving(true);
    setFormError(null);
    try {
      await adapter.updateRole(selectedRole.code, {
        name: name.trim(),
        description: description.trim() || null,
        permissionCodes: selectedPermissions,
        locked,
      });
      setSuccessMessage(t("roles.updateSuccess"));
      await loadData();
    } catch (error) {
      setFormError(adapter.extractErrorMessage(error) ?? t("roles.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const createRole = async () => {
    if (!createCode.trim() || !createName.trim()) {
      setFormError(t("roles.validationNameRequired"));
      return;
    }

    setIsSaving(true);
    setFormError(null);
    try {
      const created = await adapter.createRole({
        code: createCode.trim().toUpperCase(),
        name: createName.trim(),
        description: createDescription.trim() || null,
        permissionCodes: createPermissions,
        locked: createLocked,
      });
      setIsCreateOpen(false);
      setSuccessMessage(t("roles.createSuccess"));
      await loadData();
      setSelectedRoleCode(created.code);
    } catch (error) {
      setFormError(adapter.extractErrorMessage(error) ?? t("roles.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("pages.rolesTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("pages.rolesDescription")}</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>{t("roles.listTitle")}</CardTitle>
            <CardDescription>{t("roles.listDescription")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Button variant="outline" className="w-full" onClick={() => setIsCreateOpen(true)}>
              {t("roles.createAction")}
            </Button>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">{t("roles.loading")}</p>
            ) : roles.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("roles.empty")}</p>
            ) : (
              <div className="space-y-2">
                {roles.map((role) => (
                  <button
                    key={role.code}
                    type="button"
                    className={`w-full border px-3 py-2 text-start text-sm ${selectedRoleCode === role.code ? "border-primary bg-primary/10" : "border-border"
                      }`}
                    onClick={() => setSelectedRoleCode(role.code)}
                  >
                    <p className="font-medium">{role.code}</p>
                    <p className="text-xs text-muted-foreground">
                      {role.locked ? t("roles.lockedLabel") : t("roles.unlockedLabel")}
                    </p>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("roles.detailsTitle")}</CardTitle>
            <CardDescription>{t("roles.detailsDescription")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {pageError ? <p className="text-xs text-destructive">{pageError}</p> : null}
            {successMessage ? <p className="text-xs text-emerald-600">{successMessage}</p> : null}
            {!selectedRole ? (
              <p className="text-sm text-muted-foreground">{t("roles.selectRoleHint")}</p>
            ) : (
              <>
                <div className="space-y-1">
                  <Label htmlFor="role-code">{t("roles.codeLabel")}</Label>
                  <Input id="role-code" value={selectedRole.code} disabled />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="role-name">{t("roles.nameLabel")}</Label>
                  <Input
                    id="role-name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    disabled={isAdminRole}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="role-description">{t("roles.descriptionLabel")}</Label>
                  <Input
                    id="role-description"
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    disabled={isAdminRole}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <Checkbox checked={locked} onCheckedChange={(checked) => setLocked(checked === true)} disabled={isAdminRole} />
                  {t("roles.lockedFieldLabel")}
                </label>
                {isAdminRole ? <p className="text-xs text-amber-600">{t("roles.adminLockedWarning")}</p> : null}

                <div className="space-y-2">
                  <Label>{t("roles.permissionsLabel")}</Label>
                  <PermissionSelector
                    groups={permissionGroups}
                    query={permissionSearch}
                    selectedPermissions={selectedPermissions}
                    searchId="edit-role-permission-search"
                    searchLabel={t("roles.permissionsSearchLabel")}
                    searchPlaceholder={t("roles.permissionsSearchPlaceholder")}
                    emptyMessage={t("roles.permissionsEmpty")}
                    disabled={isAdminRole}
                    onQueryChange={setPermissionSearch}
                    onToggle={togglePermission}
                  />
                </div>

                {formError ? <p className="text-xs text-destructive">{formError}</p> : null}

                <Button onClick={saveRole} disabled={isSaving || isAdminRole}>
                  {isSaving ? t("roles.saving") : t("roles.saveAction")}
                </Button>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("roles.createDialogTitle")}</DialogTitle>
            <DialogDescription>{t("roles.createDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="create-role-code">{t("roles.codeLabel")}</Label>
              <Input
                id="create-role-code"
                value={createCode}
                onChange={(event) => setCreateCode(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-role-name">{t("roles.nameLabel")}</Label>
              <Input
                id="create-role-name"
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-role-description">{t("roles.descriptionLabel")}</Label>
              <Input
                id="create-role-description"
                value={createDescription}
                onChange={(event) => setCreateDescription(event.target.value)}
              />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={createLocked} onCheckedChange={(checked) => setCreateLocked(checked === true)} />
              {t("roles.lockedFieldLabel")}
            </label>
            <div className="space-y-2">
              <Label>{t("roles.permissionsLabel")}</Label>
              <PermissionSelector
                groups={createPermissionGroups}
                query={createPermissionSearch}
                selectedPermissions={createPermissions}
                searchId="create-role-permission-search"
                searchLabel={t("roles.permissionsSearchLabel")}
                searchPlaceholder={t("roles.permissionsSearchPlaceholder")}
                emptyMessage={t("roles.permissionsEmpty")}
                onQueryChange={setCreatePermissionSearch}
                onToggle={toggleCreatePermission}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateOpen(false)} disabled={isSaving}>
              {t("roles.cancelAction")}
            </Button>
            <Button onClick={createRole} disabled={isSaving}>
              {isSaving ? t("roles.saving") : t("roles.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
