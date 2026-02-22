package com.warehouse.warehouse_platform.security.permissions;

public final class LandlordPermissions {

    public static final String TENANTS_VIEW = "landlord.tenants.view";
    public static final String TENANTS_CREATE = "landlord.tenants.create";
    public static final String USERS_VIEW = "landlord.users.view";
    public static final String USERS_CREATE = "landlord.users.create";
    public static final String USERS_EDIT = "landlord.users.edit";
    public static final String USERS_RESET_PASSWORD = "landlord.users.reset_password";
    public static final String USERS_DEACTIVATE = "landlord.users.deactivate";

    private LandlordPermissions() {
    }
}
