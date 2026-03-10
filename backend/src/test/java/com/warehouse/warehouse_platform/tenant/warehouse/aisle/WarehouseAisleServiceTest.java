package com.warehouse.warehouse_platform.tenant.warehouse.aisle;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.side.WarehouseAisleSideRepository;
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
class WarehouseAisleServiceTest {

    @Mock
    private WarehouseLayoutRepository layoutRepository;

    @Mock
    private WarehouseAisleRepository aisleRepository;

    @Mock
    private WarehouseAisleSideRepository sideRepository;

    @Mock
    private WarehouseShelfRepository shelfRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private WarehouseAisleService service;

    private static final UUID LAYOUT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        service = new WarehouseAisleService(layoutRepository, aisleRepository, sideRepository, shelfRepository, tenantAuditService);
    }

    @Test
    void createAisle_shouldNormalizeCodeAndAudit() {
        WarehouseLayout layout = layout(LAYOUT_ID);
        when(layoutRepository.findById(LAYOUT_ID)).thenReturn(Optional.of(layout));
        when(aisleRepository.findByLayout_IdAndCodeIgnoreCase(LAYOUT_ID, "A01")).thenReturn(Optional.empty());
        when(aisleRepository.save(any(WarehouseAisle.class))).thenAnswer(invocation -> {
            WarehouseAisle saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            saved.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return saved;
        });

        WarehouseAisleService.AisleResult result = service.createAisle(LAYOUT_ID, " a01 ", "Aisle One");

        assertEquals("A01", result.code());
        assertEquals("Aisle One", result.name());
        verify(tenantAuditService).record(
                eq("WAREHOUSE_AISLE_CREATE"), eq("WAREHOUSE_AISLE"), eq(result.id().toString()), eq(null), any());

        ArgumentCaptor<WarehouseAisle> captor = ArgumentCaptor.forClass(WarehouseAisle.class);
        verify(aisleRepository).save(captor.capture());
        assertEquals("A01", captor.getValue().getCode());
    }

    @Test
    void createAisle_shouldRejectDuplicateCodeWithinLayout() {
        WarehouseLayout layout = layout(LAYOUT_ID);
        when(layoutRepository.findById(LAYOUT_ID)).thenReturn(Optional.of(layout));
        when(aisleRepository.findByLayout_IdAndCodeIgnoreCase(LAYOUT_ID, "A01"))
                .thenReturn(Optional.of(aisle(UUID.randomUUID(), layout, "A01", true)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.createAisle(LAYOUT_ID, "A01", "Duplicate Aisle"));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteAisle_shouldRejectWhenStillActive() {
        UUID aisleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        WarehouseLayout layout = layout(LAYOUT_ID);
        when(aisleRepository.findById(aisleId)).thenReturn(Optional.of(aisle(aisleId, layout, "A01", true)));

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.hardDeleteAisle(aisleId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteAisle_shouldRejectWhenSidesExist() {
        UUID aisleId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        WarehouseLayout layout = layout(LAYOUT_ID);
        when(aisleRepository.findById(aisleId)).thenReturn(Optional.of(aisle(aisleId, layout, "A01", false)));
        when(sideRepository.countByAisle_Id(aisleId)).thenReturn(1L);

        WarehouseManagementException ex = assertThrows(
                WarehouseManagementException.class,
                () -> service.hardDeleteAisle(aisleId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteAisle_shouldDeleteWhenInactiveAndNoSides() {
        UUID aisleId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        WarehouseLayout layout = layout(LAYOUT_ID);
        when(aisleRepository.findById(aisleId)).thenReturn(Optional.of(aisle(aisleId, layout, "A01", false)));
        when(sideRepository.countByAisle_Id(aisleId)).thenReturn(0L);

        service.hardDeleteAisle(aisleId);

        verify(aisleRepository).delete(any(WarehouseAisle.class));
        verify(tenantAuditService).record(
                eq("WAREHOUSE_AISLE_HARD_DELETE"), eq("WAREHOUSE_AISLE"), eq(aisleId.toString()), any(), eq(null));
    }

    @Test
    void updateAisle_shouldCascadeRenameWhenCodeChanges() {
        UUID aisleId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        WarehouseLayout layout = layout(LAYOUT_ID);
        WarehouseAisle existing = aisle(aisleId, layout, "A01", true);

        when(aisleRepository.findById(aisleId)).thenReturn(Optional.of(existing));
        when(aisleRepository.findByLayout_IdAndCodeIgnoreCase(LAYOUT_ID, "A02")).thenReturn(Optional.empty());
        when(aisleRepository.save(any(WarehouseAisle.class))).thenReturn(existing);
        when(shelfRepository.findAllByAisleId(aisleId)).thenReturn(Collections.emptyList());

        service.updateAisle(aisleId, "A02", "Updated Aisle");

        verify(shelfRepository).findAllByAisleId(aisleId);
        verify(shelfRepository).saveAll(any());
    }

    private WarehouseLayout layout(UUID id) {
        return WarehouseLayout.builder()
                .id(id)
                .code("WH1")
                .name("Main Warehouse")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private WarehouseAisle aisle(UUID id, WarehouseLayout layout, String code, boolean active) {
        return WarehouseAisle.builder()
                .id(id)
                .layout(layout)
                .code(code)
                .name("Test Aisle")
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
