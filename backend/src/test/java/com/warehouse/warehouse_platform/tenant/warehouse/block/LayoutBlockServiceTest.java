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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class LayoutBlockServiceTest {

        @Mock
        private LayoutBlockRepository layoutBlockRepository;
        @Mock
        private BlockTemplateRepository blockTemplateRepository;
        @Mock
        private WarehouseLayoutRepository layoutRepository;
        @Mock
        private TenantAuditService tenantAuditService;

        private LayoutBlockService service;

        static final UUID LAYOUT_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000000");
        static final UUID TEMPLATE_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000000");
        static final UUID TEMPLATE_ALPHA_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000001");
        static final UUID TEMPLATE_LR_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");

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
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
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

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, null, null);

                assertEquals(3, result.position()); // max was 2, so next = 3
                assertEquals("4", result.identifier());
                assertNull(result.parentId());
                assertNull(result.side());
        }

        @Test
        void addBlock_shouldUseAlphabeticIdentifierForAlphaTemplates() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ALPHA_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ALPHA_ID, BlockTemplate.IdentifierFormat.ALPHA, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of());
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        b.setId(UUID.randomUUID());
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ALPHA_ID, null, 27, null);

                assertEquals(27, result.position());
                assertEquals("AB", result.identifier());
        }

        @Test
        void addBlock_shouldRejectUnknownLayout() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(false);

                WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                                () -> service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, null));
                assertEquals("NOT_FOUND", ex.getCode());
        }

        @Test
        void addBlock_shouldRejectUnknownTemplate() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

                WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                                () -> service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, null));
                assertEquals("NOT_FOUND", ex.getCode());
        }

        @Test
        void addBlock_shouldNormalizeAllowedSideValues() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_LR_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_LR_ID, BlockTemplate.IdentifierFormat.ALPHA, BlockTemplate.SideConfig.LR,
                                null)));
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of());
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        b.setId(UUID.randomUUID());
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_LR_ID, null, 0, "r");

                assertEquals("R", result.side());
                assertEquals("A", result.identifier());
        }

        @Test
        void addBlock_shouldRejectSideForTemplatesWithoutSideConfiguration() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));

                WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                                () -> service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, "L"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertTrue(ex.getMessage().contains("side is not allowed"));
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
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.findById(parentId)).thenReturn(Optional.of(parent));
                // position 0 requested — shift existing child at 0 up to 1
                when(layoutBlockRepository.findByLayoutIdAndParentIdOrderByPositionAsc(LAYOUT_ID, parentId))
                                .thenReturn(List.of(existingChild));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.randomUUID());
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, parentId, 0, null);

                assertEquals(parentId, result.parentId());
                assertEquals(0, result.position());
                assertEquals("1", result.identifier());
                // Verify existing child was shifted
                assertEquals(1, existingChild.getPosition());
        }

        @Test
        void addBlocks_shouldCreateMultipleBlocksAndShiftFollowingSiblings() {
                LayoutBlock existingRoot = block(UUID.fromString("1212eeee-0000-0000-0000-000000000000"), LAYOUT_ID,
                                null, 1);
                AtomicInteger sequence = new AtomicInteger();

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(existingRoot));
                when(layoutBlockRepository.save(any())).thenAnswer(invocation -> {
                        LayoutBlock saved = invocation.getArgument(0);
                        saved.setId(UUID.fromString(
                                        "90000000-0000-0000-0000-00000000000" + sequence.incrementAndGet()));
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                });

                LayoutBlockService.BatchBlockResult result = service.addBlocks(LAYOUT_ID, TEMPLATE_ID, null, 1, 2,
                                null);

                assertEquals(2, result.createdBlocks().size());
                assertEquals(2, result.totalCreated());
                assertEquals(2, result.rootCount());
                assertEquals(1, result.createdBlocks().get(0).position());
                assertEquals("2", result.createdBlocks().get(0).identifier());
                assertEquals(2, result.createdBlocks().get(1).position());
                assertEquals("3", result.createdBlocks().get(1).identifier());
                assertEquals(3, existingRoot.getPosition());
                verify(tenantAuditService).record(eq("LAYOUT_BLOCK_BATCH_ADD"), eq("LAYOUT_BLOCK"),
                                eq(LAYOUT_ID.toString()), eq(null), any());
        }

        // -------------------------------------------------------------------------
        // moveBlock — cycle prevention
        // -------------------------------------------------------------------------

        @Test
        void moveBlock_shouldRejectMovingBlockUnderOwnDescendant() {
                UUID blockId = UUID.fromString("eeee0000-0000-0000-0000-000000000000");
                UUID childId = UUID.fromString("ffff0000-0000-0000-0000-000000000000");

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
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
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

        @Test
        void copySubtree_shouldCloneStructureAndRepeatRequestedCopies() {
                UUID sourceRootId = UUID.fromString("55550000-0000-0000-0000-000000000000");
                UUID sourceChildId = UUID.fromString("55550000-0000-0000-0000-000000000001");
                UUID existingRootId = UUID.fromString("55550000-0000-0000-0000-000000000002");
                LayoutBlock sourceRoot = block(sourceRootId, LAYOUT_ID, null, 0);
                LayoutBlock sourceChild = block(sourceChildId, LAYOUT_ID, sourceRootId, 0);
                sourceChild.setBlockTemplateId(TEMPLATE_LR_ID);
                sourceChild.setSide("L");
                LayoutBlock existingRoot = block(existingRootId, LAYOUT_ID, null, 1);

                List<LayoutBlock> savedBlocks = new ArrayList<>();
                AtomicInteger sequence = new AtomicInteger();

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(sourceRootId)).thenReturn(Optional.of(sourceRoot));
                when(layoutBlockRepository.findById(sourceChildId)).thenReturn(Optional.of(sourceChild));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(sourceRoot, sourceChild, existingRoot));
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(sourceRoot, existingRoot));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(
                                template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                                BlockTemplate.SideConfig.NONE, null),
                                template(TEMPLATE_LR_ID, BlockTemplate.IdentifierFormat.ALPHA,
                                                BlockTemplate.SideConfig.LR, null)));
                when(layoutBlockRepository.save(any())).thenAnswer(invocation -> {
                        LayoutBlock saved = invocation.getArgument(0);
                        saved.setId(UUID.fromString(
                                        "70000000-0000-0000-0000-00000000000" + sequence.incrementAndGet()));
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        savedBlocks.add(saved);
                        return saved;
                });

                LayoutBlockService.BatchBlockResult result = service.copySubtree(LAYOUT_ID, sourceRootId, null, 1, 2);

                assertEquals(2, result.createdBlocks().size());
                assertEquals(4, result.totalCreated());
                assertEquals(2, result.rootCount());
                assertEquals(1, result.createdBlocks().get(0).position());
                assertEquals(2, result.createdBlocks().get(1).position());
                assertEquals(3, existingRoot.getPosition());
                assertEquals(4, savedBlocks.size());
                assertNull(savedBlocks.get(0).getParentId());
                assertEquals(savedBlocks.get(0).getId(), savedBlocks.get(1).getParentId());
                assertEquals("L", savedBlocks.get(1).getSide());
                assertNull(savedBlocks.get(2).getParentId());
                assertEquals(savedBlocks.get(2).getId(), savedBlocks.get(3).getParentId());
                verify(tenantAuditService).record(eq("LAYOUT_BLOCK_SUBTREE_COPY"), eq("LAYOUT_BLOCK"),
                                eq(sourceRootId.toString()), eq(null), any());
        }

        // -------------------------------------------------------------------------
        // reassignTemplate
        // -------------------------------------------------------------------------

        @Test
        void reassignTemplate_shouldUpdateTemplateIdAndRetainCompatibleSide() {
                UUID blockId = UUID.fromString("4444bbbb-0000-0000-0000-000000000000");
                UUID newTemplateId = UUID.fromString("5555bbbb-0000-0000-0000-000000000000");
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);
                b.setSide("L");

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.LR,
                                null)));
                when(blockTemplateRepository.findById(newTemplateId)).thenReturn(Optional.of(template(
                                newTemplateId, BlockTemplate.IdentifierFormat.ALPHA, BlockTemplate.SideConfig.LR,
                                null)));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(layoutBlockRepository.save(any())).thenReturn(b);

                LayoutBlockService.BlockResult result = service.reassignTemplate(LAYOUT_ID, blockId, newTemplateId);

                assertEquals(newTemplateId, result.blockTemplateId());
                assertEquals("L", result.side());
                verify(tenantAuditService).record(eq("LAYOUT_BLOCK_REASSIGN"), eq("LAYOUT_BLOCK"),
                                eq(blockId.toString()), any(), any());
        }

        @Test
        void reassignTemplate_shouldClearIncompatibleExistingSide() {
                UUID blockId = UUID.fromString("6666bbbb-0000-0000-0000-000000000000");
                UUID newTemplateId = UUID.fromString("7777bbbb-0000-0000-0000-000000000000");
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);
                b.setSide("R");

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.LR,
                                null)));
                when(blockTemplateRepository.findById(newTemplateId)).thenReturn(Optional.of(template(
                                newTemplateId, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.AB,
                                null)));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                LayoutBlockService.BlockResult result = service.reassignTemplate(LAYOUT_ID, blockId, newTemplateId);

                assertEquals(newTemplateId, result.blockTemplateId());
                assertNull(result.side());
        }

        @Test
        void updateMetadata_shouldStoreValidatedCustomSide() {
                UUID blockId = UUID.fromString("8888bbbb-0000-0000-0000-000000000000");
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.CUSTOM,
                                "North,South")));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                LayoutBlockService.BlockResult result = service.updateMetadata(LAYOUT_ID, blockId, "south");

                assertEquals("South", result.side());
                verify(tenantAuditService).record(eq("LAYOUT_BLOCK_UPDATE_METADATA"), eq("LAYOUT_BLOCK"),
                                eq(blockId.toString()), any(), any());
        }

        // -------------------------------------------------------------------------
        // Helpers
        // -------------------------------------------------------------------------

        private BlockTemplate template(
                        UUID id,
                        BlockTemplate.IdentifierFormat identifierFormat,
                        BlockTemplate.SideConfig sideConfig,
                        String sideOptions) {
                return BlockTemplate.builder()
                                .id(id)
                                .name("Template-" + id)
                                .identifierFormat(identifierFormat)
                                .sideConfig(sideConfig)
                                .sideOptions(sideOptions)
                                .required(true)
                                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .build();
        }

        private LayoutBlock block(UUID id, UUID layoutId, UUID parentId, int position) {
                return LayoutBlock.builder()
                                .id(id)
                                .layoutId(layoutId)
                                .blockTemplateId(TEMPLATE_ID)
                                .parentId(parentId)
                                .position(position)
                                .side(null)
                                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .build();
        }
}
