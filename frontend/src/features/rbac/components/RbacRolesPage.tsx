import { useCallback, useEffect, useMemo, useState } from "react";
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

  const selectedRole = useMemo(
    () => roles.find((role) => role.code === selectedRoleCode) ?? null,
    [roles, selectedRoleCode]
  );
  const isAdminRole = selectedRole?.code === "ADMIN";

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
    setFormError(null);
  }, [isCreateOpen]);

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
                    className={`w-full border px-3 py-2 text-start text-sm ${
                      selectedRoleCode === role.code ? "border-primary bg-primary/10" : "border-border"
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
                  <ScrollArea className="h-72 border p-3">
                    <div className="space-y-2">
                      {permissions.map((permission) => (
                        <label key={permission.code} className="flex items-start gap-2 text-sm">
                          <Checkbox
                            checked={selectedPermissions.includes(permission.code)}
                            onCheckedChange={() => togglePermission(permission.code)}
                            disabled={isAdminRole}
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
                  </ScrollArea>
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
              <ScrollArea className="h-64 border p-3">
                <div className="space-y-2">
                  {permissions.map((permission) => (
                    <label key={permission.code} className="flex items-start gap-2 text-sm">
                      <Checkbox
                        checked={createPermissions.includes(permission.code)}
                        onCheckedChange={() => toggleCreatePermission(permission.code)}
                      />
                      <span>{permission.code}</span>
                    </label>
                  ))}
                </div>
              </ScrollArea>
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
