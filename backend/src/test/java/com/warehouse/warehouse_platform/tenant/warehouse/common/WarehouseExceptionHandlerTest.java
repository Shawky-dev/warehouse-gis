package com.warehouse.warehouse_platform.tenant.warehouse.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarehouseExceptionHandlerTest {

    @Test
    void handleWarehouseException_shouldReturnStructuredConflictResponse() {
        WarehouseExceptionHandler handler = new WarehouseExceptionHandler();

        var response = handler.handleWarehouseException(
                WarehouseManagementException.conflict("Layout name already exists"));
        var body = Objects.requireNonNull(response.getBody());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", body.code());
        assertEquals("Layout name already exists", body.message());
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnFriendlyBlockPositionConflict() {
        WarehouseExceptionHandler handler = new WarehouseExceptionHandler();
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "commit failed",
                new RuntimeException(
                        "duplicate key value violates unique constraint \"uq_layout_blocks_position_rooted\""));

        var response = handler.handleDataIntegrityViolation(exception);
        var body = Objects.requireNonNull(response.getBody());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", body.code());
        assertEquals("Another block already uses this position under the selected parent.", body.message());
    }
}