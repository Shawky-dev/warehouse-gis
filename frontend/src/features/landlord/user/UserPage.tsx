import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createLandlordUser,
  deactivateLandlordUser,
  extractRbacErrorMessage,
  getLandlordRoles,
  getLandlordUsers,
  reactivateLandlordUser,
  resetLandlordUserPassword,
  updateLandlordUser,
} from "@/features/landlord/api/rbacApi";
import type { LandlordRoleDetails, LandlordUserSummary } from "@/features/landlord/types/rbac";
import { LANDLORD_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/i18n";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

const PAGE_SIZE = 20;

type ActiveFilter = "all" | "active" | "inactive";

const UserPage = () => {
  const { t } = useI18n();
  const { user, hasPermission, hasRole } = useAuth();
  const actorIsAdmin = hasRole("ROLE_ADMIN");

  const canCreate = hasPermission(LANDLORD_PERMISSIONS.USERS_CREATE);
  const canEdit = hasPermission(LANDLORD_PERMISSIONS.USERS_EDIT);
  const canResetPassword = hasPermission(LANDLORD_PERMISSIONS.USERS_RESET_PASSWORD);
  const canDeactivate = hasPermission(LANDLORD_PERMISSIONS.USERS_DEACTIVATE);
  const canReactivate = hasPermission(LANDLORD_PERMISSIONS.USERS_REACTIVATE);

  const [users, setUsers] = useState<LandlordUserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>("all");
  const [isLoading, setIsLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [roleOptions, setRoleOptions] = useState<LandlordRoleDetails[]>([]);
  const [roleOptionsAvailable, setRoleOptionsAvailable] = useState(true);

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isResetOpen, setIsResetOpen] = useState(false);
  const [isDeactivateOpen, setIsDeactivateOpen] = useState(false);
  const [isReactivateOpen, setIsReactivateOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<LandlordUserSummary | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [createEmail, setCreateEmail] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [createRole, setCreateRole] = useState("MANAGER");

  const [editEmail, setEditEmail] = useState("");
  const [editRole, setEditRole] = useState("");

  const [newPassword, setNewPassword] = useState("");

  const activeParam = useMemo(() => {
    if (activeFilter === "all") {
      return undefined;
    }
    return activeFilter === "active";
  }, [activeFilter]);

  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    setListError(null);

    try {
      const result = await getLandlordUsers({
        page,
        size: PAGE_SIZE,
        search: searchQuery || undefined,
        active: activeParam,
      });
      setUsers(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (error) {
      setListError(extractRbacErrorMessage(error) ?? t("users.listLoadFailed"));
    } finally {
      setIsLoading(false);
    }
  }, [activeParam, page, searchQuery, t]);

  const loadRoles = useCallback(async () => {
    if (!canCreate && !canEdit) {
      return;
    }

    try {
      const result = await getLandlordRoles();
      setRoleOptions(result);
      setRoleOptionsAvailable(true);
      if (result.length > 0) {
        setCreateRole((current) => current || result[0].code);
      }
    } catch {
      setRoleOptionsAvailable(false);
      setRoleOptions([]);
    }
  }, [canCreate, canEdit]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    void loadRoles();
  }, [loadRoles]);

  useEffect(() => {
    if (isCreateOpen) {
      setDialogError(null);
      setCreateEmail("");
      setCreatePassword("");
      setCreateRole(roleOptions[0]?.code ?? "MANAGER");
    }
  }, [isCreateOpen, roleOptions]);

  const openEditDialog = (targetUser: LandlordUserSummary) => {
    setSelectedUser(targetUser);
    setEditEmail(targetUser.email);
    setEditRole(targetUser.role);
    setDialogError(null);
    setIsEditOpen(true);
  };

  const openResetDialog = (targetUser: LandlordUserSummary) => {
    setSelectedUser(targetUser);
    setNewPassword("");
    setDialogError(null);
    setIsResetOpen(true);
  };

  const openDeactivateDialog = (targetUser: LandlordUserSummary) => {
    setSelectedUser(targetUser);
    setDialogError(null);
    setIsDeactivateOpen(true);
  };

  const openReactivateDialog = (targetUser: LandlordUserSummary) => {
    setSelectedUser(targetUser);
    setDialogError(null);
    setIsReactivateOpen(true);
  };

  const handleRefresh = async () => {
    await loadUsers();
  };

  const handleCreate = async () => {
    if (!createEmail.trim() || !createPassword || !createRole.trim()) {
      setDialogError(t("users.validationRequired"));
      return;
    }
    if (createPassword.length < 8) {
      setDialogError(t("users.passwordMinLength"));
      return;
    }

    setDialogError(null);
    setIsSubmitting(true);
    try {
      await createLandlordUser({
        email: createEmail.trim(),
        password: createPassword,
        role: createRole.trim().toUpperCase(),
      });
      setIsCreateOpen(false);
      setSuccessMessage(t("users.createSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(extractRbacErrorMessage(error) ?? t("users.actionFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEdit = async () => {
    if (!selectedUser) {
      return;
    }
    if (!editEmail.trim() || !editRole.trim()) {
      setDialogError(t("users.validationRequired"));
      return;
    }

    setDialogError(null);
    setIsSubmitting(true);
    try {
      await updateLandlordUser(selectedUser.id, {
        email: editEmail.trim(),
        role: editRole.trim().toUpperCase(),
      });
      setIsEditOpen(false);
      setSuccessMessage(t("users.updateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(extractRbacErrorMessage(error) ?? t("users.actionFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetPassword = async () => {
    if (!selectedUser) {
      return;
    }
    if (!newPassword) {
      setDialogError(t("users.validationRequired"));
      return;
    }
    if (newPassword.length < 8) {
      setDialogError(t("users.passwordMinLength"));
      return;
    }

    setDialogError(null);
    setIsSubmitting(true);
    try {
      await resetLandlordUserPassword(selectedUser.id, {
        newPassword,
      });
      setIsResetOpen(false);
      setSuccessMessage(t("users.resetSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(extractRbacErrorMessage(error) ?? t("users.actionFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeactivate = async () => {
    if (!selectedUser) {
      return;
    }

    if (selectedUser.email.toLowerCase() === user?.email.toLowerCase()) {
      setDialogError(t("users.selfDeactivateForbidden"));
      return;
    }

    if (!actorIsAdmin && selectedUser.role.toUpperCase() === "ADMIN") {
      setDialogError(t("users.adminDeactivateForbidden"));
      return;
    }

    setDialogError(null);
    setIsSubmitting(true);
    try {
      await deactivateLandlordUser(selectedUser.id);
      setIsDeactivateOpen(false);
      setSuccessMessage(t("users.deactivateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(extractRbacErrorMessage(error) ?? t("users.actionFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReactivate = async () => {
    if (!selectedUser) {
      return;
    }

    setDialogError(null);
    setIsSubmitting(true);
    try {
      await reactivateLandlordUser(selectedUser.id);
      setIsReactivateOpen(false);
      setSuccessMessage(t("users.reactivateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(extractRbacErrorMessage(error) ?? t("users.actionFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const applySearch = () => {
    setPage(0);
    setSearchQuery(searchInput.trim());
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("pages.usersTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("pages.usersDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("users.filtersTitle")}</CardTitle>
          <CardDescription>{t("users.filtersDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-[1fr_auto_auto_auto]">
            <Input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={t("users.searchPlaceholder")}
            />
            <select
              aria-label={t("users.activeFilterLabel")}
              value={activeFilter}
              onChange={(event) => {
                setPage(0);
                setActiveFilter(event.target.value as ActiveFilter);
              }}
              className="border-input h-9 border px-3 text-sm"
            >
              <option value="all">{t("users.activeFilterAll")}</option>
              <option value="active">{t("users.activeFilterActive")}</option>
              <option value="inactive">{t("users.activeFilterInactive")}</option>
            </select>
            <Button type="button" onClick={applySearch}>
              {t("users.applyFilters")}
            </Button>
            {canCreate ? (
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(true)}>
                {t("users.createAction")}
              </Button>
            ) : null}
          </div>
          {successMessage ? <p className="text-xs text-emerald-600">{successMessage}</p> : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("users.listTitle")}</CardTitle>
          <CardDescription>{t("users.listCount", { count: String(totalElements) })}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {listError ? <p className="text-xs text-destructive">{listError}</p> : null}

          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("users.loading")}</p>
          ) : users.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("users.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="py-2 pe-4">{t("users.tableEmail")}</th>
                    <th className="py-2 pe-4">{t("users.tableRole")}</th>
                    <th className="py-2 pe-4">{t("users.tableStatus")}</th>
                    <th className="py-2">{t("users.tableActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((tenantUser) => {
                    const isSelf = tenantUser.email.toLowerCase() === user?.email.toLowerCase();

                    return (
                      <tr key={tenantUser.id} className="border-b align-top">
                        <td className="py-2 pe-4">{tenantUser.email}</td>
                        <td className="py-2 pe-4">{tenantUser.role}</td>
                        <td className="py-2 pe-4">
                          {tenantUser.active ? t("users.statusActive") : t("users.statusInactive")}
                        </td>
                        <td className="py-2">
                          <div className="flex flex-wrap gap-2">
                            {canEdit ? (
                              <Button size="sm" variant="outline" onClick={() => openEditDialog(tenantUser)}>
                                {t("users.editAction")}
                              </Button>
                            ) : null}
                            {canResetPassword ? (
                              <Button size="sm" variant="outline" onClick={() => openResetDialog(tenantUser)}>
                                {t("users.resetPasswordAction")}
                              </Button>
                            ) : null}
                            {canDeactivate ? (
                                  <Button
                                size="sm"
                                variant="outline"
                                onClick={() => openDeactivateDialog(tenantUser)}
                                disabled={
                                  isSelf ||
                                  !tenantUser.active ||
                                  (!actorIsAdmin && tenantUser.role.toUpperCase() === "ADMIN")
                                }
                              >
                                {t("users.deactivateAction")}
                              </Button>
                            ) : null}
                            {canReactivate ? (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => openReactivateDialog(tenantUser)}
                                disabled={tenantUser.active}
                              >
                                {t("users.reactivateAction")}
                              </Button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-muted-foreground">
              {t("users.paginationInfo", {
                page: String(page + 1),
                totalPages: String(Math.max(totalPages, 1)),
              })}
            </p>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
                disabled={page === 0 || isLoading}
              >
                {t("users.paginationPrevious")}
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => setPage((current) => current + 1)}
                disabled={isLoading || page + 1 >= totalPages}
              >
                {t("users.paginationNext")}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.createDialogTitle")}</DialogTitle>
            <DialogDescription>{t("users.createDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="create-user-email">{t("users.tableEmail")}</Label>
              <Input
                id="create-user-email"
                type="email"
                value={createEmail}
                onChange={(event) => setCreateEmail(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-user-password">{t("users.passwordLabel")}</Label>
              <Input
                id="create-user-password"
                type="password"
                value={createPassword}
                onChange={(event) => setCreatePassword(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-user-role">{t("users.tableRole")}</Label>
              {roleOptionsAvailable && roleOptions.length > 0 ? (
                <select
                  id="create-user-role"
                  value={createRole}
                  onChange={(event) => setCreateRole(event.target.value)}
                  className="border-input h-9 w-full border px-3 text-sm"
                >
                  {roleOptions.map((roleOption) => (
                    <option key={roleOption.code} value={roleOption.code}>
                      {roleOption.code}
                    </option>
                  ))}
                </select>
              ) : (
                <Input
                  id="create-user-role"
                  value={createRole}
                  onChange={(event) => setCreateRole(event.target.value)}
                />
              )}
              {!roleOptionsAvailable ? (
                <p className="text-xs text-muted-foreground">{t("users.rolesFallbackHint")}</p>
              ) : null}
            </div>
            {dialogError ? <p className="text-xs text-destructive">{dialogError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateOpen(false)} disabled={isSubmitting}>
              {t("users.cancelAction")}
            </Button>
            <Button onClick={handleCreate} disabled={isSubmitting}>
              {isSubmitting ? t("users.saving") : t("users.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.editDialogTitle")}</DialogTitle>
            <DialogDescription>{t("users.editDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="edit-user-email">{t("users.tableEmail")}</Label>
              <Input
                id="edit-user-email"
                type="email"
                value={editEmail}
                onChange={(event) => setEditEmail(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="edit-user-role">{t("users.tableRole")}</Label>
              {roleOptionsAvailable && roleOptions.length > 0 ? (
                <select
                  id="edit-user-role"
                  value={editRole}
                  onChange={(event) => setEditRole(event.target.value)}
                  className="border-input h-9 w-full border px-3 text-sm"
                >
                  {roleOptions.map((roleOption) => (
                    <option key={roleOption.code} value={roleOption.code}>
                      {roleOption.code}
                    </option>
                  ))}
                </select>
              ) : (
                <Input
                  id="edit-user-role"
                  value={editRole}
                  onChange={(event) => setEditRole(event.target.value)}
                />
              )}
            </div>
            {dialogError ? <p className="text-xs text-destructive">{dialogError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsEditOpen(false)} disabled={isSubmitting}>
              {t("users.cancelAction")}
            </Button>
            <Button onClick={handleEdit} disabled={isSubmitting}>
              {isSubmitting ? t("users.saving") : t("users.editAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isResetOpen} onOpenChange={setIsResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.resetDialogTitle")}</DialogTitle>
            <DialogDescription>{t("users.resetDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="reset-user-password">{t("users.passwordLabel")}</Label>
              <Input
                id="reset-user-password"
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
            </div>
            {dialogError ? <p className="text-xs text-destructive">{dialogError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsResetOpen(false)} disabled={isSubmitting}>
              {t("users.cancelAction")}
            </Button>
            <Button onClick={handleResetPassword} disabled={isSubmitting}>
              {isSubmitting ? t("users.saving") : t("users.resetPasswordAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isDeactivateOpen} onOpenChange={setIsDeactivateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.deactivateDialogTitle")}</DialogTitle>
            <DialogDescription>{t("users.deactivateDialogDescription")}</DialogDescription>
          </DialogHeader>
          {dialogError ? <p className="text-xs text-destructive">{dialogError}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDeactivateOpen(false)} disabled={isSubmitting}>
              {t("users.cancelAction")}
            </Button>
            <Button onClick={handleDeactivate} disabled={isSubmitting}>
              {isSubmitting ? t("users.saving") : t("users.deactivateAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isReactivateOpen} onOpenChange={setIsReactivateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.reactivateDialogTitle")}</DialogTitle>
            <DialogDescription>{t("users.reactivateDialogDescription")}</DialogDescription>
          </DialogHeader>
          {dialogError ? <p className="text-xs text-destructive">{dialogError}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsReactivateOpen(false)} disabled={isSubmitting}>
              {t("users.cancelAction")}
            </Button>
            <Button onClick={handleReactivate} disabled={isSubmitting}>
              {isSubmitting ? t("users.saving") : t("users.reactivateAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default UserPage;
