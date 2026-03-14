package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKindService;
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
    private TenantAuditService tenantAuditService;
    @Mock
    private WarehouseLocationKindService warehouseLocationKindService;

    private WarehouseLayoutService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseLayoutService(layoutRepository, layoutBlockRepository, blockTemplateRepository,
                tenantAuditService, warehouseLocationKindService);
        when(warehouseLocationKindService.getDefaultLocationKind()).thenReturn(WarehouseLocationKind.builder()
                .id(UUID.fromString("41000000-0000-0000-0000-000000000001"))
                .name("Storage")
                .sortOrder(0)
                .build());
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
        when(layoutBlockRepository.save(any(LayoutBlock.class))).thenAnswer(inv -> {
            LayoutBlock block = inv.getArgument(0);
            if (block.getId() == null) {
                block.setId(UUID.randomUUID());
            }
            block.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            block.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return block;
        });

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
        verify(layoutBlockRepository, org.mockito.Mockito.times(4)).save(any(LayoutBlock.class));
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
