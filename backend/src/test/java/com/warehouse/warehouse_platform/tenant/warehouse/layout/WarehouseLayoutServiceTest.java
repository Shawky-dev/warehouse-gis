package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisleRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseLayoutServiceTest {

    @Mock
    private WarehouseLayoutRepository layoutRepository;

    @Mock
    private WarehouseAisleRepository aisleRepository;

    @Mock
    private WarehouseShelfRepository shelfRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private WarehouseLayoutService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseLayoutService(layoutRepository, aisleRepository, shelfRepository, tenantAuditService);
    }

    @Test
    void createLayout_shouldNormalizeCodeAndAudit() {
        when(layoutRepository.findByCodeIgnoreCase("WH1")).thenReturn(Optional.empty());
        when(layoutRepository.save(any(WarehouseLayout.class))).thenAnswer(invocation -> {
            WarehouseLayout saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            saved.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return saved;
        });

        WarehouseLayoutService.LayoutResult result = service.createLayout(" wh1 ", "Main Warehouse", null);

        assertEquals("WH1", result.code());
        assertEquals("Main Warehouse", result.name());
        verify(tenantAuditService).record(
                eq("WAREHOUSE_LAYOUT_CREATE"), eq("WAREHOUSE_LAYOUT"), eq(result.id().toString()), eq(null), any());

        ArgumentCaptor<WarehouseLayout> captor = ArgumentCaptor.forClass(WarehouseLayout.class);
        verify(layoutRepository).save(captor.capture());
        assertEquals("WH1", captor.getValue().getCode());
    }

    @Test
    void createLayout_shouldRejectDuplicateCode() {
        when(layoutRepository.findByCodeIgnoreCase("WH1"))
                .thenReturn(Optional.of(layout(UUID.randomUUID(), "WH1", true)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.createLayout("WH1", "Duplicate", null));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void createLayout_shouldRejectNonAlphanumericCode() {
        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.createLayout("WH-1", "Bad Code", null));

        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void hardDeleteLayout_shouldRejectWhenStillActive() {
        UUID layoutId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(layoutRepository.findById(layoutId)).thenReturn(Optional.of(layout(layoutId, "WH1", true)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.hardDeleteLayout(layoutId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteLayout_shouldRejectWhenAislesExist() {
        UUID layoutId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(layoutRepository.findById(layoutId)).thenReturn(Optional.of(layout(layoutId, "WH1", false)));
        when(aisleRepository.countByLayout_Id(layoutId)).thenReturn(2L);

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.hardDeleteLayout(layoutId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteLayout_shouldDeleteWhenInactiveAndNoAisles() {
        UUID layoutId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(layoutRepository.findById(layoutId)).thenReturn(Optional.of(layout(layoutId, "WH1", false)));
        when(aisleRepository.countByLayout_Id(layoutId)).thenReturn(0L);

        service.hardDeleteLayout(layoutId);

        verify(layoutRepository).delete(any(WarehouseLayout.class));
        verify(tenantAuditService).record(
                eq("WAREHOUSE_LAYOUT_HARD_DELETE"), eq("WAREHOUSE_LAYOUT"), eq(layoutId.toString()), any(), eq(null));
    }

    @Test
    void updateLayout_shouldCascadeRenameWhenCodeChanges() {
        UUID layoutId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        WarehouseLayout existing = layout(layoutId, "WH1", true);

        when(layoutRepository.findById(layoutId)).thenReturn(Optional.of(existing));
        when(layoutRepository.findByCodeIgnoreCase("WH2")).thenReturn(Optional.empty());
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(existing);
        when(shelfRepository.findAllByLayoutId(layoutId)).thenReturn(Collections.emptyList());

        service.updateLayout(layoutId, "WH2", "Updated Warehouse", null);

        verify(shelfRepository).findAllByLayoutId(layoutId);
        verify(shelfRepository).saveAll(any());
    }

    private WarehouseLayout layout(UUID id, String code, boolean active) {
        return WarehouseLayout.builder()
                .id(id)
                .code(code)
                .name("Test Warehouse")
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
