package com.warehouse.warehouse_platform.security.permissions;

public final class TenantPermissions {

    public static final String USERS_VIEW = "tenant.users.view";
    public static final String USERS_CREATE = "tenant.users.create";
    public static final String USERS_EDIT = "tenant.users.edit";
    public static final String USERS_RESET_PASSWORD = "tenant.users.reset_password";
    public static final String USERS_DEACTIVATE = "tenant.users.deactivate";
    public static final String USERS_REACTIVATE = "tenant.users.reactivate";
    public static final String ROLES_EDIT = "tenant.roles.edit";
    public static final String UOMS_VIEW = "tenant.uoms.view";
    public static final String UOMS_CREATE = "tenant.uoms.create";
    public static final String UOMS_EDIT = "tenant.uoms.edit";
    public static final String UOMS_SOFT_DELETE = "tenant.uoms.soft_delete";
    public static final String UOMS_RESTORE = "tenant.uoms.restore";
    public static final String UOMS_HARD_DELETE = "tenant.uoms.hard_delete";
    public static final String SUPPLIERS_VIEW = "tenant.suppliers.view";
    public static final String SUPPLIERS_CREATE = "tenant.suppliers.create";
    public static final String SUPPLIERS_EDIT = "tenant.suppliers.edit";
    public static final String SUPPLIERS_SOFT_DELETE = "tenant.suppliers.soft_delete";
    public static final String SUPPLIERS_RESTORE = "tenant.suppliers.restore";
    public static final String SUPPLIERS_HARD_DELETE = "tenant.suppliers.hard_delete";
    public static final String PRODUCTS_VIEW = "tenant.products.view";
    public static final String PRODUCTS_CREATE = "tenant.products.create";
    public static final String PRODUCTS_EDIT = "tenant.products.edit";
    public static final String PRODUCTS_SOFT_DELETE = "tenant.products.soft_delete";
    public static final String PRODUCTS_RESTORE = "tenant.products.restore";
    public static final String PRODUCTS_HARD_DELETE = "tenant.products.hard_delete";
    public static final String CATEGORIES_VIEW = "tenant.categories.view";
    public static final String CATEGORIES_CREATE = "tenant.categories.create";
    public static final String CATEGORIES_EDIT = "tenant.categories.edit";
    public static final String CATEGORIES_SOFT_DELETE = "tenant.categories.soft_delete";
    public static final String CATEGORIES_RESTORE = "tenant.categories.restore";
    public static final String CATEGORIES_HARD_DELETE = "tenant.categories.hard_delete";
    public static final String AUDIT_VIEW = "tenant.audit.view";

    private TenantPermissions() {
    }
}
