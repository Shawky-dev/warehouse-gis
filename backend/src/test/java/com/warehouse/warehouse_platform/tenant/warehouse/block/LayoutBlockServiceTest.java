package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LayoutBlockServiceTest {

    @Mock private LayoutBlockRepository layoutBlockRepository;
    @Mock private BlockTemplateRepository blockTemplateRepository;
    @Mock private WarehouseLayoutRepository layoutRepository;
    @Mock private TenantAuditService tenantAuditService;

    private LayoutBlockService service;

    static final UUID LAYOUT_ID   = UUID.fromString("aaaa0000-0000-0000-0000-000000000000");
    static final UUID TEMPLATE_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        service = new LayoutBlockService(
                layoutBlockRepository, blockTemplateRepository, layoutRepository, tenantAuditService);
    }

    // -------------------------------------------------------------------------
    // addBlock — root level, auto-position
    // -------------------------------------------------------------------------

    @Test
    void addBlock_shouldAppendAtRootWhenNoPositionGiven() {
        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(blockTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(true);
        when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(2);
        when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                .thenReturn(List.of());
        when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
            LayoutBlock b = inv.getArgument(0);
            b.setId(UUID.fromString("cccc0000-0000-0000-0000-000000000000"));
            b.setCreatedAt(Instant.now());
            b.setUpdatedAt(Instant.now());
            return b;
        });

        LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, null);

        assertEquals(3, result.position()); // max was 2, so next = 3
        assertNull(result.parentId());
    }

    @Test
    void addBlock_shouldUseExplicitPosition() {
        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(blockTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(true);
        when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                .thenReturn(List.of());
        when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
            LayoutBlock b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            b.setUpdatedAt(Instant.now());
            return b;
        });

        LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 1);

        assertEquals(1, result.position());
    }

    @Test
    void addBlock_shouldRejectUnknownLayout() {
        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(false);

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void addBlock_shouldRejectUnknownTemplate() {
        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(blockTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(false);

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    // -------------------------------------------------------------------------
    // addBlock — nested
    // -------------------------------------------------------------------------

    @Test
    void addBlock_shouldAddUnderParentAndShiftSiblings() {
        UUID parentId = UUID.fromString("dddd0000-0000-0000-0000-000000000000");
        LayoutBlock parent = block(parentId, LAYOUT_ID, null, 0);
        LayoutBlock existingChild = block(UUID.randomUUID(), LAYOUT_ID, parentId, 0);

        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(blockTemplateRepository.existsById(TEMPLATE_ID)).thenReturn(true);
        when(layoutBlockRepository.findById(parentId)).thenReturn(Optional.of(parent));
        // position 0 requested — shift existing child at 0 up to 1
        when(layoutBlockRepository.findByLayoutIdAndParentIdOrderByPositionAsc(LAYOUT_ID, parentId))
                .thenReturn(List.of(existingChild));
        when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
            LayoutBlock b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            b.setUpdatedAt(Instant.now());
            return b;
        });

        LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, parentId, 0);

        assertEquals(parentId, result.parentId());
        assertEquals(0, result.position());
        // Verify existing child was shifted
        assertEquals(1, existingChild.getPosition());
    }

    // -------------------------------------------------------------------------
    // moveBlock — cycle prevention
    // -------------------------------------------------------------------------

    @Test
    void moveBlock_shouldRejectMovingBlockUnderOwnDescendant() {
        UUID blockId      = UUID.fromString("eeee0000-0000-0000-0000-000000000000");
        UUID childId      = UUID.fromString("ffff0000-0000-0000-0000-000000000000");

        LayoutBlock block = block(blockId, LAYOUT_ID, null, 0);
        LayoutBlock child = block(childId, LAYOUT_ID, blockId, 0);

        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(layoutBlockRepository.findById(childId)).thenReturn(Optional.of(child));
        when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                .thenReturn(List.of(block, child));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.moveBlock(LAYOUT_ID, blockId, childId, 0));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    // -------------------------------------------------------------------------
    // removeBlock
    // -------------------------------------------------------------------------

    @Test
    void removeBlock_shouldDeleteAndCloseGap() {
        UUID blockId = UUID.fromString("1111eeee-0000-0000-0000-000000000000");
        UUID sibling1Id = UUID.fromString("2222eeee-0000-0000-0000-000000000000");
        UUID sibling2Id = UUID.fromString("3333eeee-0000-0000-0000-000000000000");

        LayoutBlock toRemove = block(blockId, LAYOUT_ID, null, 0);
        LayoutBlock sibling1 = block(sibling1Id, LAYOUT_ID, null, 1);
        LayoutBlock sibling2 = block(sibling2Id, LAYOUT_ID, null, 2);

        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(toRemove));
        when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                .thenReturn(List.of(sibling1, sibling2));

        service.removeBlock(LAYOUT_ID, blockId);

        verify(layoutBlockRepository).delete(toRemove);
        // siblings at position >= 1 should have been shifted down by 1
        assertEquals(0, sibling1.getPosition());
        assertEquals(1, sibling2.getPosition());
        verify(tenantAuditService).record(eq("LAYOUT_BLOCK_REMOVE"), eq("LAYOUT_BLOCK"),
                eq(blockId.toString()), any(), eq(null));
    }

    // -------------------------------------------------------------------------
    // reassignTemplate
    // -------------------------------------------------------------------------

    @Test
    void reassignTemplate_shouldUpdateTemplateId() {
        UUID blockId = UUID.fromString("4444bbbb-0000-0000-0000-000000000000");
        UUID newTemplateId = UUID.fromString("5555bbbb-0000-0000-0000-000000000000");
        LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);

        when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
        when(blockTemplateRepository.existsById(newTemplateId)).thenReturn(true);
        when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
        when(layoutBlockRepository.save(any())).thenReturn(b);

        LayoutBlockService.BlockResult result = service.reassignTemplate(LAYOUT_ID, blockId, newTemplateId);

        assertEquals(newTemplateId, result.blockTemplateId());
        verify(tenantAuditService).record(eq("LAYOUT_BLOCK_REASSIGN"), eq("LAYOUT_BLOCK"),
                eq(blockId.toString()), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LayoutBlock block(UUID id, UUID layoutId, UUID parentId, int position) {
        return LayoutBlock.builder()
                .id(id)
                .layoutId(layoutId)
                .blockTemplateId(TEMPLATE_ID)
                .parentId(parentId)
                .position(position)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
