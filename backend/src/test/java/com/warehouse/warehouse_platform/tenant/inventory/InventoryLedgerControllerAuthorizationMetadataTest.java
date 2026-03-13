package com.warehouse.warehouse_platform.tenant.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryLedgerControllerAuthorizationMetadataTest {

    @Test
    void getAllOnHand_shouldRequireInventoryViewPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "getAllOnHand",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)");
    }

    @Test
    void getOnHandByLocation_shouldRequireInventoryViewPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "getOnHandByLocation",
                String.class,
                UUID.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)");
    }

    @Test
    void getOnHandByProduct_shouldRequireInventoryViewPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "getOnHandByProduct",
                String.class,
                UUID.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)");
    }

    @Test
    void getMovementsByLocation_shouldRequireInventoryViewPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "getMovementsByLocation",
                String.class,
                UUID.class,
                int.class,
                int.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)");
    }

    @Test
    void getMovementsByProduct_shouldRequireInventoryViewPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "getMovementsByProduct",
                String.class,
                UUID.class,
                int.class,
                int.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW)");
    }

    @Test
    void receive_shouldRequireInventoryReceivePermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "receive",
                String.class,
                InventoryLedgerController.ReceiveRequest.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE)");
    }

    @Test
    void transfer_shouldRequireInventoryTransferPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "transfer",
                String.class,
                InventoryLedgerController.TransferRequest.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER)");
    }

    @Test
    void adjust_shouldRequireInventoryAdjustPermission() throws Exception {
        Method method = InventoryLedgerController.class.getMethod(
                "adjust",
                String.class,
                InventoryLedgerController.AdjustRequest.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST)");
    }

    private void assertPreAuthorizeValue(Method method, String expected) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(expected, preAuthorize.value());
    }
}
