package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("null")
class WarehouseLayoutServiceTest {

    @Mock
    private WarehouseLayoutRepository layoutRepository;
    @Mock
    private LayoutBlockRepository layoutBlockRepository;
    @Mock
    private BlockTemplateRepository blockTemplateRepository;
    @Mock
    private LayoutBlockService layoutBlockService;
    @Mock
    private TenantAuditService tenantAuditService;

    private WarehouseLayoutService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseLayoutService(layoutRepository, layoutBlockRepository, blockTemplateRepository,
                layoutBlockService, tenantAuditService);
    }

    // -------------------------------------------------------------------------
    // createLayout
    // -------------------------------------------------------------------------

    @Test
    void createLayout_shouldSaveAndAudit() {
        when(layoutRepository.findByNameIgnoreCase("Main Warehouse")).thenReturn(Optional.empty());
        when(layoutRepository.save(any())).thenAnswer(inv -> {
            WarehouseLayout l = inv.getArgument(0);
            l.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            l.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            l.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return l;
        });

        WarehouseLayoutService.LayoutResult result = service.createLayout("Main Warehouse", "A description");

        assertEquals("Main Warehouse", result.name());
        assertEquals("A description", result.description());
        assertFalse(result.isActive());
        verify(tenantAuditService).record(eq("WAREHOUSE_LAYOUT_CREATE"), eq("WAREHOUSE_LAYOUT"),
                eq(result.id().toString()), eq(null), any());
    }

    @Test
    void createLayout_shouldRejectDuplicateName() {
        when(layoutRepository.findByNameIgnoreCase("Dupe"))
                .thenReturn(Optional.of(layout(UUID.randomUUID(), "Dupe", false)));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createLayout("Dupe", null));
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void createLayout_shouldRejectBlankName() {
        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createLayout("   ", null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void createClassicPreset_shouldCreateNestedDefaultBlocks() {
        UUID aisleId = UUID.fromString("21000000-0000-0000-0000-000000000001");
        UUID bayId = UUID.fromString("21000000-0000-0000-0000-000000000002");
        UUID levelId = UUID.fromString("21000000-0000-0000-0000-000000000003");
        UUID shelfId = UUID.fromString("21000000-0000-0000-0000-000000000004");
        when(layoutRepository.findByNameIgnoreCase("Classic Layout")).thenReturn(Optional.empty());
        when(blockTemplateRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(layoutRepository.save(any(WarehouseLayout.class))).thenAnswer(inv -> {
            WarehouseLayout l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            }
            l.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            l.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return l;
        });
        when(blockTemplateRepository.save(any(BlockTemplate.class))).thenAnswer(inv -> {
            BlockTemplate t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            t.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            t.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return t;
        });
        when(layoutBlockService.addBlock(any(), any(), eq(null), eq(0), eq(null))).thenReturn(
                new LayoutBlockService.BlockResult(
                        aisleId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("31000000-0000-0000-0000-000000000001"),
                        null,
                        0,
                        "A",
                        null,
                        UUID.fromString("41000000-0000-0000-0000-000000000001"),
                        "Storage",
                        "A",
                        "A",
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-03-01T00:00:00Z")));
        when(layoutBlockService.addBlock(any(), any(), eq(aisleId), eq(0), eq(null))).thenReturn(
                new LayoutBlockService.BlockResult(
                        bayId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("31000000-0000-0000-0000-000000000002"),
                        aisleId,
                        0,
                        "1",
                        null,
                        UUID.fromString("41000000-0000-0000-0000-000000000001"),
                        "Storage",
                        "A-01",
                        "A-01",
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-03-01T00:00:00Z")));
        when(layoutBlockService.addBlock(any(), any(), eq(bayId), eq(0), eq(null))).thenReturn(
                new LayoutBlockService.BlockResult(
                        levelId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("31000000-0000-0000-0000-000000000003"),
                        bayId,
                        0,
                        "1",
                        null,
                        UUID.fromString("41000000-0000-0000-0000-000000000001"),
                        "Storage",
                        "A-01-01",
                        "A-01-01",
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-03-01T00:00:00Z")));
        when(layoutBlockService.addBlock(any(), any(), eq(levelId), eq(0), eq(null))).thenReturn(
                new LayoutBlockService.BlockResult(
                        shelfId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("31000000-0000-0000-0000-000000000004"),
                        levelId,
                        0,
                        "1",
                        null,
                        UUID.fromString("41000000-0000-0000-0000-000000000001"),
                        "Storage",
                        "A-01-01-01",
                        "A-01-01-01",
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-03-01T00:00:00Z")));

        WarehouseLayoutService.LayoutResult result = service.createClassicPreset("Classic Layout", "Default tree",
                true);

        assertTrue(result.isActive());
        verify(blockTemplateRepository).save(argThat(
                template -> "Aisle".equals(template.getName()) && "AlignJustify".equals(template.getIconName())));
        verify(blockTemplateRepository).save(argThat(
                template -> "Bay".equals(template.getName())
                        && template.getSideConfig() == BlockTemplate.SideConfig.LR));
        verify(blockTemplateRepository).save(
                argThat(template -> "Shelf".equals(template.getName()) && "MapPin".equals(template.getIconName())));
        verify(layoutBlockService).addBlock(any(), any(), eq(null), eq(0), eq(null));
        verify(layoutBlockService).addBlock(any(), any(), eq(aisleId), eq(0), eq(null));
        verify(layoutBlockService).addBlock(any(), any(), eq(bayId), eq(0), eq(null));
        verify(layoutBlockService).addBlock(any(), any(), eq(levelId), eq(0), eq(null));
        verify(tenantAuditService).record(eq("WAREHOUSE_LAYOUT_CREATE_CLASSIC_PRESET"), eq("WAREHOUSE_LAYOUT"),
                eq(result.id().toString()), eq(null), any());
    }

    // -------------------------------------------------------------------------
    // activateLayout — one-active constraint
    // -------------------------------------------------------------------------

    @Test
    void activateLayout_shouldDeactivatePreviousAndActivateNew() {
        UUID currentActiveId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID newId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        WarehouseLayout currentActive = layout(currentActiveId, "Old", true);
        WarehouseLayout toActivate = layout(newId, "New", false);

        when(layoutRepository.findById(newId)).thenReturn(Optional.of(toActivate));
        when(layoutRepository.findByIsActiveTrue()).thenReturn(Optional.of(currentActive));
        when(layoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activateLayout(newId);

        assertFalse(currentActive.getIsActive());
        assertTrue(toActivate.getIsActive());

        ArgumentCaptor<WarehouseLayout> captor = ArgumentCaptor.forClass(WarehouseLayout.class);
        verify(layoutRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    }

    @Test
    void activateLayout_shouldBeNoopIfAlreadyActive() {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(layoutRepository.findById(id)).thenReturn(Optional.of(layout(id, "Layout", true)));

        service.activateLayout(id);

        verify(layoutRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // deleteLayout
    // -------------------------------------------------------------------------

    @Test
    void deleteLayout_shouldRejectActiveLayout() {
        UUID id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(layoutRepository.findById(id)).thenReturn(Optional.of(layout(id, "Active", true)));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.deleteLayout(id));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void deleteLayout_shouldRejectWhenBlocksExist() {
        UUID id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        when(layoutRepository.findById(id)).thenReturn(Optional.of(layout(id, "Inactive", false)));
        when(layoutBlockRepository.countByLayoutId(id)).thenReturn(3L);

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.deleteLayout(id));
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void deleteLayout_shouldSucceedWhenInactiveAndEmpty() {
        UUID id = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        WarehouseLayout l = layout(id, "Empty", false);
        when(layoutRepository.findById(id)).thenReturn(Optional.of(l));
        when(layoutBlockRepository.countByLayoutId(id)).thenReturn(0L);

        service.deleteLayout(id);

        verify(layoutRepository).delete(l);
        verify(tenantAuditService).record(eq("WAREHOUSE_LAYOUT_DELETE"), eq("WAREHOUSE_LAYOUT"),
                eq(id.toString()), any(), eq(null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WarehouseLayout layout(UUID id, String name, boolean active) {
        return WarehouseLayout.builder()
                .id(id)
                .name(name)
                .isActive(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
