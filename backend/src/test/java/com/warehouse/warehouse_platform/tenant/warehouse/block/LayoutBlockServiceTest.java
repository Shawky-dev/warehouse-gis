package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        @Mock
        private WarehouseLocationKindService warehouseLocationKindService;

        private LayoutBlockService service;

        static final UUID LAYOUT_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000000");
        static final UUID TEMPLATE_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000000");
        static final UUID TEMPLATE_ALPHA_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000001");
        static final UUID TEMPLATE_LR_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
        static final UUID STORAGE_KIND_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
        static final UUID STAGING_KIND_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");

        @BeforeEach
        void setUp() {
                service = new LayoutBlockService(
                                layoutBlockRepository, blockTemplateRepository, layoutRepository, tenantAuditService,
                                warehouseLocationKindService);
                when(warehouseLocationKindService.getDefaultLocationKind())
                                .thenReturn(locationKind(STORAGE_KIND_ID, "Storage"));
                when(warehouseLocationKindService.getRequired(STAGING_KIND_ID))
                                .thenReturn(locationKind(STAGING_KIND_ID, "Staging"));
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
                        // Only assign a new ID (and track) for genuinely new blocks; existing blocks
                        // re-saved during scan-code recalculation already have an ID.
                        if (saved.getId() == null) {
                                saved.setId(UUID.fromString(String.format(
                                                "70000000-0000-0000-0000-%012d", sequence.incrementAndGet())));
                                savedBlocks.add(saved);
                        }
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                });
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                LayoutBlockService.BatchBlockResult result = service.copySubtree(LAYOUT_ID, sourceRootId, null, 1, 2);

                assertEquals(2, result.createdBlocks().size());
                assertEquals(4, result.totalCreated());
                assertEquals(2, result.rootCount());
                assertEquals(1, result.createdBlocks().get(0).position());
                assertEquals(2, result.createdBlocks().get(1).position());
                assertEquals(4, savedBlocks.stream().map(LayoutBlock::getId).distinct().count());
                assertEquals(2, savedBlocks.stream().filter(b -> b.getParentId() == null).map(LayoutBlock::getId)
                                .distinct().count());
                assertEquals(2, savedBlocks.stream().filter(b -> b.getParentId() != null).map(LayoutBlock::getId)
                                .distinct().count());
                assertTrue(savedBlocks.stream().anyMatch(b -> "L".equals(b.getSide())));
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
        // C1-A: locationKind + scanCode
        // -------------------------------------------------------------------------

        @Test
        void addBlock_setsDefaultStorageKind() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(null);
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.randomUUID());
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, null);

                assertEquals(STORAGE_KIND_ID, result.locationKindId());
                assertEquals("Storage", result.locationKindName());
        }

        @Test
        void addBlock_generatesScanCode() {
                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(null);
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.randomUUID());
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });

                LayoutBlockService.BlockResult result = service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, null);

                assertNotNull(result.scanCode());
                assertNotNull(result.fullCode());
                assertTrue(result.scanCode().length() > 0);
        }

        @Test
        void copySubtree_generatesNewScanCode() {
                UUID sourceRootId = UUID.fromString("aa110000-0000-0000-0000-000000000000");
                UUID sourceChildId = UUID.fromString("aa110000-0000-0000-0000-000000000001");
                LayoutBlock sourceRoot = block(sourceRootId, LAYOUT_ID, null, 0);
                sourceRoot.setScanCode("OLD-ROOT-CODE");
                sourceRoot.setFullCode("OLD-ROOT-CODE");
                LayoutBlock sourceChild = block(sourceChildId, LAYOUT_ID, sourceRootId, 0);
                sourceChild.setScanCode("OLD-CHILD-CODE");
                sourceChild.setFullCode("OLD-CHILD-CODE");

                List<LayoutBlock> allNewBlocks = new ArrayList<>();
                AtomicInteger seq = new AtomicInteger();

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(sourceRootId)).thenReturn(Optional.of(sourceRoot));
                when(layoutBlockRepository.findById(sourceChildId)).thenReturn(Optional.of(sourceChild));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(sourceRoot, sourceChild));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(
                                template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                                BlockTemplate.SideConfig.NONE, null)));
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.fromString(
                                                String.format("00000000-0000-0000-0000-%012d", seq.incrementAndGet())));
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        allNewBlocks.add(b);
                        return b;
                });

                service.copySubtree(LAYOUT_ID, sourceRootId, null, 1, 1);

                // Cloned blocks must not carry the source's scan codes
                allNewBlocks.stream()
                                .filter(b -> !b.getId().equals(sourceRootId) && !b.getId().equals(sourceChildId))
                                .forEach(b -> {
                                        assertNotEquals("OLD-ROOT-CODE", b.getScanCode());
                                        assertNotEquals("OLD-CHILD-CODE", b.getScanCode());
                                });
        }

        @Test
        void updateLocationKind_changesKindAndPersists() {
                UUID blockId = UUID.fromString("cc220000-0000-0000-0000-000000000000");
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE,
                                null)));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                LayoutBlockService.BlockResult result = service.updateLocationKind(LAYOUT_ID, blockId,
                                STAGING_KIND_ID);

                assertEquals(STAGING_KIND_ID, result.locationKindId());
                assertEquals("Staging", result.locationKindName());
                verify(layoutBlockRepository).save(any());
        }

        @Test
        void updateLocationKind_rejectsNullKind() {
                UUID blockId = UUID.fromString("dd330000-0000-0000-0000-000000000000");

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);

                WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                                () -> service.updateLocationKind(LAYOUT_ID, blockId, null));
                assertEquals("BAD_REQUEST", ex.getCode());
        }

        // -------------------------------------------------------------------------
        // moveBlock — scan code recalculation
        // -------------------------------------------------------------------------

        /**
         * Common mock setup used by scan-code-recalculation tests.
         * Every test that triggers recalculateScanCodes needs:
         *   - findByLayoutIdOrderByParentIdAscPositionAsc to return the current tree
         *   - findAllById (templates) to return templates
         *   - existsByScanCodeAndIdNot to return false (no collisions)
         *   - saveAndFlush to work (used by the two-pass clear step)
         */
        private void stubRecalculation(List<LayoutBlock> allBlocks, BlockTemplate... templates) {
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(allBlocks);
                when(blockTemplateRepository.findAllById(any()))
                                .thenReturn(List.of(templates));
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void moveBlock_shouldUpdateScanCodeWhenReparented() {
                UUID parentId = UUID.fromString("1100aaaa-0000-0000-0000-000000000000");
                UUID blockId = UUID.fromString("1100bbbb-0000-0000-0000-000000000000");

                // parent at root position 0 (ALPHA → "A"), block is child at position 0 (NUMERIC → "01")
                LayoutBlock parent = block(parentId, LAYOUT_ID, null, 0);
                parent.setBlockTemplateId(TEMPLATE_ALPHA_ID);
                LayoutBlock movingBlock = block(blockId, LAYOUT_ID, parentId, 0);
                movingBlock.setScanCode("A-01"); // stale after move

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(movingBlock));
                // newParentId = null → moving to root
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(parent, movingBlock));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(0);
                // sibling queries for old parent and new (root) parent
                when(layoutBlockRepository.findByLayoutIdAndParentIdOrderByPositionAsc(LAYOUT_ID, parentId))
                                .thenReturn(List.of());
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(parent, movingBlock));
                stubRecalculation(List.of(parent, movingBlock),
                                template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                                BlockTemplate.SideConfig.NONE, null),
                                template(TEMPLATE_ALPHA_ID, BlockTemplate.IdentifierFormat.ALPHA,
                                                BlockTemplate.SideConfig.NONE, null));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null)));

                LayoutBlockService.BlockResult result = service.moveBlock(LAYOUT_ID, blockId, null, 1);

                // After reparenting to root at position 1, code should be "02" (no parent prefix)
                assertEquals("02", result.scanCode());
        }

        @Test
        void moveBlock_shouldUpdateShiftedSiblingsAtOldParent() {
                UUID parentId = UUID.fromString("2200aaaa-0000-0000-0000-000000000000");
                UUID blockId = UUID.fromString("2200bbbb-0000-0000-0000-000000000000");
                UUID siblingId = UUID.fromString("2200cccc-0000-0000-0000-000000000000");

                LayoutBlock parent = block(parentId, LAYOUT_ID, null, 0);
                LayoutBlock movingBlock = block(blockId, LAYOUT_ID, parentId, 0);
                movingBlock.setScanCode("01-01");
                // sibling was at position 1, after move it shifts to 0
                LayoutBlock sibling = block(siblingId, LAYOUT_ID, parentId, 0); // position already updated
                sibling.setScanCode("01-02"); // stale code

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(movingBlock));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(parent, sibling));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(0);
                when(layoutBlockRepository.findMaxChildPosition(LAYOUT_ID, parentId)).thenReturn(1);
                // old parent siblings
                when(layoutBlockRepository.findByLayoutIdAndParentIdOrderByPositionAsc(LAYOUT_ID, parentId))
                                .thenReturn(List.of(sibling));
                // new parent (root) siblings
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(parent));
                stubRecalculation(List.of(parent, sibling), numericTemplate);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));
                when(layoutBlockRepository.findById(siblingId)).thenReturn(Optional.of(sibling));

                service.moveBlock(LAYOUT_ID, blockId, null, 1);

                // sibling shifted to position 0 under parent (position 0, NUMERIC → "01"),
                // with parent also at position 0 (NUMERIC → "01"), so full code = "01-01"
                assertEquals("01-01", sibling.getScanCode());
        }

        @Test
        void moveBlock_shouldUpdateDescendantCodesAfterMove() {
                UUID blockId = UUID.fromString("3300bbbb-0000-0000-0000-000000000000");
                UUID childId = UUID.fromString("3300cccc-0000-0000-0000-000000000000");

                // block at root position 0 with a child at position 0
                LayoutBlock movingBlock = block(blockId, LAYOUT_ID, null, 1); // moving to position 0
                movingBlock.setScanCode("02");
                LayoutBlock child = block(childId, LAYOUT_ID, blockId, 0);
                child.setScanCode("02-01"); // stale after parent moves

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(movingBlock));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(movingBlock, child));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(1);
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(movingBlock));
                stubRecalculation(List.of(movingBlock, child), numericTemplate);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));

                service.moveBlock(LAYOUT_ID, blockId, null, 0);

                // block moves to position 0 → "01"; child is still position 0 under block → "01-01"
                assertEquals("01", movingBlock.getScanCode());
                assertEquals("01-01", child.getScanCode());
        }

        // -------------------------------------------------------------------------
        // reassignTemplate — scan code recalculation
        // -------------------------------------------------------------------------

        @Test
        void reassignTemplate_shouldRegenerateScanCodeAfterFormatChange() {
                UUID blockId = UUID.fromString("4400bbbb-0000-0000-0000-000000000000");
                UUID newTemplateId = UUID.fromString("4400cccc-0000-0000-0000-000000000000");

                // Block at position 0 with NUMERIC template → scanCode "01"
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);
                b.setScanCode("01");

                BlockTemplate alphaTemplate = template(newTemplateId, BlockTemplate.IdentifierFormat.ALPHA,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(
                                TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null)));
                when(blockTemplateRepository.findById(newTemplateId)).thenReturn(Optional.of(alphaTemplate));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                stubRecalculation(List.of(b), alphaTemplate);

                LayoutBlockService.BlockResult result = service.reassignTemplate(LAYOUT_ID, blockId, newTemplateId);

                // Position 0 with ALPHA format → "A"
                assertEquals("A", result.scanCode());
        }

        @Test
        void reassignTemplate_shouldRegenerateDescendantCodesAfterFormatChange() {
                UUID blockId = UUID.fromString("5500bbbb-0000-0000-0000-000000000000");
                UUID childId = UUID.fromString("5500cccc-0000-0000-0000-000000000000");
                UUID newTemplateId = UUID.fromString("5500dddd-0000-0000-0000-000000000000");

                // Block at position 0 with NUMERIC template; child at position 0 with NUMERIC
                LayoutBlock b = block(blockId, LAYOUT_ID, null, 0);
                b.setScanCode("01");
                LayoutBlock child = block(childId, LAYOUT_ID, blockId, 0);
                child.setScanCode("01-01"); // prefix segment will change when block swaps to ALPHA

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);
                BlockTemplate alphaTemplate = template(newTemplateId, BlockTemplate.IdentifierFormat.ALPHA,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));
                when(blockTemplateRepository.findById(newTemplateId)).thenReturn(Optional.of(alphaTemplate));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(b));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                stubRecalculation(List.of(b, child), numericTemplate, alphaTemplate);

                service.reassignTemplate(LAYOUT_ID, blockId, newTemplateId);

                // Block → "A", child → "A-01"
                assertEquals("A", b.getScanCode());
                assertEquals("A-01", child.getScanCode());
        }

        // -------------------------------------------------------------------------
        // addBlock — sibling shift recalculation
        // -------------------------------------------------------------------------

        @Test
        void addBlock_shouldUpdateSiblingCodesAfterInsert() {
                UUID existingId = UUID.fromString("6600aaaa-0000-0000-0000-000000000000");
                // Existing block was at position 0; after insert at position 0 it shifts to position 1
                LayoutBlock existing = block(existingId, LAYOUT_ID, null, 1);
                existing.setScanCode("01"); // stale — should become "02"

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(null);
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null) b.setId(UUID.fromString("6600bbbb-0000-0000-0000-000000000000"));
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });
                // After insert, sibling query returns existing block (now at position 1)
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(existing));
                stubRecalculation(List.of(existing), numericTemplate);

                service.addBlock(LAYOUT_ID, TEMPLATE_ID, null, 0, null);

                // existing was shifted to position 1 → should now have code "02"
                assertEquals("02", existing.getScanCode());
        }

        @Test
        void addBlocks_shouldUpdateSiblingCodesAfterBatchInsert() {
                UUID existingId = UUID.fromString("7700aaaa-0000-0000-0000-000000000000");
                // Existing block was at position 0; after inserting 2 blocks at position 0 it shifts to position 2
                LayoutBlock existing = block(existingId, LAYOUT_ID, null, 2);
                existing.setScanCode("01"); // stale — should become "03"

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));
                when(layoutBlockRepository.findMaxRootPosition(LAYOUT_ID)).thenReturn(null);
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                AtomicInteger seq = new AtomicInteger();
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.fromString("77000000-0000-0000-0000-00000000000" + seq.incrementAndGet()));
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(existing));
                stubRecalculation(List.of(existing), numericTemplate);

                service.addBlocks(LAYOUT_ID, TEMPLATE_ID, null, 0, 2, null);

                // existing shifted to position 2 → "03"
                assertEquals("03", existing.getScanCode());
        }

        // -------------------------------------------------------------------------
        // removeBlock — sibling shift recalculation
        // -------------------------------------------------------------------------

        @Test
        void removeBlock_shouldUpdateSiblingCodesAfterDelete() {
                UUID blockId = UUID.fromString("8800aaaa-0000-0000-0000-000000000000");
                UUID sibling1Id = UUID.fromString("8800bbbb-0000-0000-0000-000000000000");
                UUID sibling2Id = UUID.fromString("8800cccc-0000-0000-0000-000000000000");

                LayoutBlock toRemove = block(blockId, LAYOUT_ID, null, 0);
                // After deletion siblings shift: position 1→0, 2→1
                LayoutBlock sibling1 = block(sibling1Id, LAYOUT_ID, null, 0);
                sibling1.setScanCode("02"); // stale — should become "01"
                LayoutBlock sibling2 = block(sibling2Id, LAYOUT_ID, null, 1);
                sibling2.setScanCode("03"); // stale — should become "02"

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(blockTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(numericTemplate));
                when(layoutBlockRepository.findById(blockId)).thenReturn(Optional.of(toRemove));
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(sibling1, sibling2));
                stubRecalculation(List.of(sibling1, sibling2), numericTemplate);

                service.removeBlock(LAYOUT_ID, blockId);

                assertEquals("01", sibling1.getScanCode());
                assertEquals("02", sibling2.getScanCode());
        }

        // -------------------------------------------------------------------------
        // copySubtree — sibling shift recalculation
        // -------------------------------------------------------------------------

        @Test
        void copySubtree_shouldUpdateShiftedSiblingCodesAfterCopy() {
                UUID sourceRootId = UUID.fromString("9900aaaa-0000-0000-0000-000000000000");
                UUID existingSiblingId = UUID.fromString("9900bbbb-0000-0000-0000-000000000000");

                // Source block at position 0 to be copied; existing sibling at position 1
                // After copying 1 copy at position 0, existing sibling shifts to position 2
                LayoutBlock sourceRoot = block(sourceRootId, LAYOUT_ID, null, 0);
                LayoutBlock existingSibling = block(existingSiblingId, LAYOUT_ID, null, 2);
                existingSibling.setScanCode("02"); // stale — should become "03"

                BlockTemplate numericTemplate = template(TEMPLATE_ID, BlockTemplate.IdentifierFormat.NUMERIC,
                                BlockTemplate.SideConfig.NONE, null);
                AtomicInteger seq = new AtomicInteger();

                when(layoutRepository.existsById(LAYOUT_ID)).thenReturn(true);
                when(layoutBlockRepository.findById(sourceRootId)).thenReturn(Optional.of(sourceRoot));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(sourceRoot, existingSibling));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(numericTemplate));
                when(layoutBlockRepository.existsByScanCodeAndIdNot(anyString(), any())).thenReturn(false);
                when(layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(LAYOUT_ID))
                                .thenReturn(List.of(existingSibling));
                when(layoutBlockRepository.save(any())).thenAnswer(inv -> {
                        LayoutBlock b = inv.getArgument(0);
                        if (b.getId() == null)
                                b.setId(UUID.fromString("99000000-0000-0000-0000-00000000000" + seq.incrementAndGet()));
                        b.setCreatedAt(Instant.now());
                        b.setUpdatedAt(Instant.now());
                        return b;
                });
                when(layoutBlockRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                service.copySubtree(LAYOUT_ID, sourceRootId, null, 0, 1);

                // existing sibling shifted to position 2 → "03"
                assertEquals("03", existingSibling.getScanCode());
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
                                .locationKind(locationKind(STORAGE_KIND_ID, "Storage"))
                                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .build();
        }

        private WarehouseLocationKind locationKind(UUID id, String name) {
                return WarehouseLocationKind.builder()
                                .id(id)
                                .name(name)
                                .sortOrder(0)
                                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                                .build();
        }
}
