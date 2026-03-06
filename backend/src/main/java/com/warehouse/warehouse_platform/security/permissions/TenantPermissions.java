package com.warehouse.warehouse_platform.security.permissions;

public final class TenantPermissions {

    public static final String USERS_VIEW = "tenant.users.view";
    public static final String USERS_CREATE = "tenant.users.create";
    public static final String USERS_EDIT = "tenant.users.edit";
    public static final String USERS_RESET_PASSWORD = "tenant.users.reset_password";
    public static final String USERS_DEACTIVATE = "tenant.users.deactivate";
    public static final String USERS_REACTIVATE = "tenant.users.reactivate";
    public static final String ROLES_EDIT = "tenant.roles.edit";

    private TenantPermissions() {
    }
}
