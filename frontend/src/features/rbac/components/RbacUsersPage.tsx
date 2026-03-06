import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/i18n";
import type {
  RbacPermissionSet,
  RbacRoleDetails,
  RbacUserSummary,
  RbacUsersPageAdapter,
} from "@/features/rbac/types";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { PasswordInput } from "@/shared/components/ui/password-input";
import { Label } from "@/shared/components/ui/label";
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

const PAGE_SIZE = 20;

type ActiveFilter = "all" | "active" | "inactive";

interface RbacUsersPageProps {
  adapter: RbacUsersPageAdapter;
  permissions: RbacPermissionSet;
}

export default function RbacUsersPage({ adapter, permissions }: RbacUsersPageProps) {
  const { t } = useI18n();
  const { user, hasPermission, hasRole } = useAuth();
  const actorIsAdmin = hasRole("ROLE_ADMIN");

  const canCreate = hasPermission(permissions.USERS_CREATE);
  const canEdit = hasPermission(permissions.USERS_EDIT);
  const canResetPassword = hasPermission(permissions.USERS_RESET_PASSWORD);
  const canDeactivate = hasPermission(permissions.USERS_DEACTIVATE);
  const canReactivate = hasPermission(permissions.USERS_REACTIVATE);

  const [users, setUsers] = useState<RbacUserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>("all");
  const [isLoading, setIsLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [roleOptions, setRoleOptions] = useState<RbacRoleDetails[]>([]);
  const [roleOptionsAvailable, setRoleOptionsAvailable] = useState(true);

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isResetOpen, setIsResetOpen] = useState(false);
  const [isDeactivateOpen, setIsDeactivateOpen] = useState(false);
  const [isReactivateOpen, setIsReactivateOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<RbacUserSummary | null>(null);
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
      const result = await adapter.listUsers({
        page,
        size: PAGE_SIZE,
        search: searchQuery || undefined,
        active: activeParam,
      });
      setUsers(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (error) {
      setListError(adapter.extractErrorMessage(error) ?? t("users.listLoadFailed"));
    } finally {
      setIsLoading(false);
    }
  }, [activeParam, adapter, page, searchQuery, t]);

  const loadRoles = useCallback(async () => {
    if (!canCreate && !canEdit) {
      return;
    }

    try {
      const result = await adapter.listRoles();
      setRoleOptions(result);
      setRoleOptionsAvailable(true);
      if (result.length > 0) {
        setCreateRole((current) => current || result[0].code);
      }
    } catch {
      setRoleOptionsAvailable(false);
      setRoleOptions([]);
    }
  }, [adapter, canCreate, canEdit]);

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

  const openEditDialog = (targetUser: RbacUserSummary) => {
    setSelectedUser(targetUser);
    setEditEmail(targetUser.email);
    setEditRole(targetUser.role);
    setDialogError(null);
    setIsEditOpen(true);
  };

  const openResetDialog = (targetUser: RbacUserSummary) => {
    setSelectedUser(targetUser);
    setNewPassword("");
    setDialogError(null);
    setIsResetOpen(true);
  };

  const openDeactivateDialog = (targetUser: RbacUserSummary) => {
    setSelectedUser(targetUser);
    setDialogError(null);
    setIsDeactivateOpen(true);
  };

  const openReactivateDialog = (targetUser: RbacUserSummary) => {
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
      await adapter.createUser({
        email: createEmail.trim(),
        password: createPassword,
        role: createRole.trim().toUpperCase(),
      });
      setIsCreateOpen(false);
      setSuccessMessage(t("users.createSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(adapter.extractErrorMessage(error) ?? t("users.actionFailed"));
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
      await adapter.updateUser(selectedUser.id, {
        email: editEmail.trim(),
        role: editRole.trim().toUpperCase(),
      });
      setIsEditOpen(false);
      setSuccessMessage(t("users.updateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(adapter.extractErrorMessage(error) ?? t("users.actionFailed"));
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
      await adapter.resetUserPassword(selectedUser.id, {
        newPassword,
      });
      setIsResetOpen(false);
      setSuccessMessage(t("users.resetSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(adapter.extractErrorMessage(error) ?? t("users.actionFailed"));
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
      await adapter.deactivateUser(selectedUser.id);
      setIsDeactivateOpen(false);
      setSuccessMessage(t("users.deactivateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(adapter.extractErrorMessage(error) ?? t("users.actionFailed"));
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
      await adapter.reactivateUser(selectedUser.id);
      setIsReactivateOpen(false);
      setSuccessMessage(t("users.reactivateSuccess"));
      await handleRefresh();
    } catch (error) {
      setDialogError(adapter.extractErrorMessage(error) ?? t("users.actionFailed"));
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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("users.tableEmail")}</TableHead>
                  <TableHead>{t("users.tableRole")}</TableHead>
                  <TableHead>{t("users.tableStatus")}</TableHead>
                  <TableHead align="right">{t("users.tableActions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((row) => {
                  const isSelf = row.email.toLowerCase() === user?.email.toLowerCase();
                  const targetIsAdmin = row.role.toUpperCase() === "ADMIN";
                  const canDeactivateTarget = canDeactivate && !isSelf && (actorIsAdmin || !targetIsAdmin);

                  return (
                    <TableRow key={row.id}>
                      <TableCell className="font-medium">{row.email}</TableCell>
                      <TableCell>{row.role}</TableCell>
                      <TableCell>{row.active ? t("users.statusActive") : t("users.statusInactive")}</TableCell>
                      <TableCell align="right">
                        <div className="flex flex-wrap justify-end gap-2">
                          {canEdit ? (
                            <Button type="button" size="sm" variant="outline" onClick={() => openEditDialog(row)}>
                              {t("users.editAction")}
                            </Button>
                          ) : null}
                          {canResetPassword ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openResetDialog(row)}
                            >
                              {t("users.resetPasswordAction")}
                            </Button>
                          ) : null}
                          {row.active
                            ? canDeactivate && (
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="destructive"
                                  onClick={() => openDeactivateDialog(row)}
                                  disabled={!canDeactivateTarget}
                                >
                                  {t("users.deactivateAction")}
                                </Button>
                              )
                            : canReactivate && (
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => openReactivateDialog(row)}
                                >
                                  {t("users.reactivateAction")}
                                </Button>
                              )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}

          <div className="flex items-center justify-between gap-2">
            <p className="text-xs text-muted-foreground">
              {t("users.paginationInfo", {
                page: String(totalPages === 0 ? 0 : page + 1),
                totalPages: String(totalPages),
              })}
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                {t("users.paginationPrevious")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={totalPages === 0 || page + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
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
              <Label htmlFor="create-email">{t("login.email")}</Label>
              <Input id="create-email" value={createEmail} onChange={(event) => setCreateEmail(event.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-password">{t("users.passwordLabel")}</Label>
              <PasswordInput
                id="create-password"
                value={createPassword}
                onChange={(event) => setCreatePassword(event.target.value)}
                showLabel={t("common.showPassword")}
                hideLabel={t("common.hidePassword")}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-role">{t("users.tableRole")}</Label>
              {roleOptionsAvailable && roleOptions.length > 0 ? (
                <select
                  id="create-role"
                  value={createRole}
                  onChange={(event) => setCreateRole(event.target.value)}
                  className="border-input h-9 w-full border px-3 text-sm"
                >
                  {roleOptions.map((role) => (
                    <option key={role.code} value={role.code}>
                      {role.code}
                    </option>
                  ))}
                </select>
              ) : (
                <>
                  <Input id="create-role" value={createRole} onChange={(event) => setCreateRole(event.target.value)} />
                  <p className="text-xs text-muted-foreground">{t("users.rolesFallbackHint")}</p>
                </>
              )}
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
              <Label htmlFor="edit-email">{t("login.email")}</Label>
              <Input id="edit-email" value={editEmail} onChange={(event) => setEditEmail(event.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="edit-role">{t("users.tableRole")}</Label>
              {roleOptionsAvailable && roleOptions.length > 0 ? (
                <select
                  id="edit-role"
                  value={editRole}
                  onChange={(event) => setEditRole(event.target.value)}
                  className="border-input h-9 w-full border px-3 text-sm"
                >
                  {roleOptions.map((role) => (
                    <option key={role.code} value={role.code}>
                      {role.code}
                    </option>
                  ))}
                </select>
              ) : (
                <Input id="edit-role" value={editRole} onChange={(event) => setEditRole(event.target.value)} />
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
              <Label htmlFor="new-password">{t("users.passwordLabel")}</Label>
              <PasswordInput
                id="new-password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                showLabel={t("common.showPassword")}
                hideLabel={t("common.hidePassword")}
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
            <Button variant="destructive" onClick={handleDeactivate} disabled={isSubmitting}>
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
}
