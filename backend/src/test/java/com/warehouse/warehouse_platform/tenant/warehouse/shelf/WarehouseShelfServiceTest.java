package com.warehouse.warehouse_platform.tenant.warehouse.shelf;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisle;
import com.warehouse.warehouse_platform.tenant.warehouse.bay.WarehouseBay;
import com.warehouse.warehouse_platform.tenant.warehouse.code.WarehouseLocationCodeGenerator;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevel;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevelRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.side.WarehouseAisleSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseShelfServiceTest {

    @Mock
    private WarehouseBayLevelRepository levelRepository;

    @Mock
    private WarehouseShelfRepository shelfRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private WarehouseLocationCodeGenerator locationCodeGenerator;
    private WarehouseShelfService service;

    private static final UUID LEVEL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        locationCodeGenerator = new WarehouseLocationCodeGenerator();
        service = new WarehouseShelfService(levelRepository, shelfRepository, locationCodeGenerator, tenantAuditService);
    }

    @Test
    void createShelf_shouldGenerateLocationCodeAndAudit() {
        WarehouseBayLevel level = buildLevel(LEVEL_ID, 2);
        when(levelRepository.findById(LEVEL_ID)).thenReturn(Optional.of(level));
        when(shelfRepository.findByLevel_IdAndShelfNum(LEVEL_ID, 3)).thenReturn(Optional.empty());
        when(shelfRepository.save(any(WarehouseShelf.class))).thenAnswer(invocation -> {
            WarehouseShelf saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            saved.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return saved;
        });

        WarehouseShelfService.ShelfResult result = service.createShelf(LEVEL_ID, 3);

        assertEquals(3, result.shelfNum());
        assertEquals("WH1-A03-R-B12-L2-S3", result.locationCode());
        verify(tenantAuditService).record(
                eq("WAREHOUSE_SHELF_CREATE"), eq("WAREHOUSE_SHELF"), eq(result.id().toString()), eq(null), any());

        ArgumentCaptor<WarehouseShelf> captor = ArgumentCaptor.forClass(WarehouseShelf.class);
        verify(shelfRepository).save(captor.capture());
        assertEquals("WH1-A03-R-B12-L2-S3", captor.getValue().getLocationCode());
    }

    @Test
    void createShelf_shouldRejectDuplicateShelfNum() {
        WarehouseBayLevel level = buildLevel(LEVEL_ID, 1);
        when(levelRepository.findById(LEVEL_ID)).thenReturn(Optional.of(level));
        when(shelfRepository.findByLevel_IdAndShelfNum(LEVEL_ID, 1))
                .thenReturn(Optional.of(shelf(UUID.randomUUID(), level, 1)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.createShelf(LEVEL_ID, 1));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void createShelf_shouldRejectInvalidShelfNum() {
        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.createShelf(LEVEL_ID, 0));

        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void hardDeleteShelf_shouldRejectWhenStillActive() {
        UUID shelfId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        WarehouseBayLevel level = buildLevel(LEVEL_ID, 1);
        when(shelfRepository.findById(shelfId)).thenReturn(Optional.of(shelf(shelfId, level, 1)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.hardDeleteShelf(shelfId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteShelf_shouldDeleteWhenInactive() {
        UUID shelfId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        WarehouseBayLevel level = buildLevel(LEVEL_ID, 1);
        WarehouseShelf inactiveShelf = shelf(shelfId, level, 1);
        inactiveShelf.setActive(false);
        inactiveShelf.setDeactivatedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(shelfRepository.findById(shelfId)).thenReturn(Optional.of(inactiveShelf));

        service.hardDeleteShelf(shelfId);

        verify(shelfRepository).delete(any(WarehouseShelf.class));
        verify(tenantAuditService).record(
                eq("WAREHOUSE_SHELF_HARD_DELETE"), eq("WAREHOUSE_SHELF"), eq(shelfId.toString()), any(), eq(null));
    }

    private WarehouseBayLevel buildLevel(UUID levelId, int levelNum) {
        WarehouseLayout layout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .code("WH1")
                .name("Main Warehouse")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        WarehouseAisle aisle = WarehouseAisle.builder()
                .id(UUID.randomUUID())
                .layout(layout)
                .code("A03")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        WarehouseAisleSide side = WarehouseAisleSide.builder()
                .id(UUID.randomUUID())
                .aisle(aisle)
                .side("R")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        WarehouseBay bay = WarehouseBay.builder()
                .id(UUID.randomUUID())
                .side(side)
                .code("B12")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        return WarehouseBayLevel.builder()
                .id(levelId)
                .bay(bay)
                .levelNum(levelNum)
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private WarehouseShelf shelf(UUID id, WarehouseBayLevel level, int shelfNum) {
        return WarehouseShelf.builder()
                .id(id)
                .level(level)
                .shelfNum(shelfNum)
                .locationCode("WH1-A03-R-B12-L1-S1")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
