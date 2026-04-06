package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasure;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
import com.warehouse.warehouse_platform.tenant.gis.service.GisZoneValidationService;
import com.warehouse.warehouse_platform.tenant.gis.service.InventoryValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InventoryLedgerServiceTest {

        @Mock
        StockMovementRepository movementRepository;
        @Mock
        LayoutBlockRepository layoutBlockRepository;
        @Mock
        ProductRepository productRepository;
        @Mock
        WarehouseLayoutRepository warehouseLayoutRepository;
        @Mock
        BlockTemplateRepository blockTemplateRepository;
        @Mock
        GisZoneValidationService gisZoneValidationService;
        @Mock
        InventoryValidationService inventoryValidationService;

        InventoryLedgerService service;

        static final UUID ACTIVE_LAYOUT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        static final UUID ROOT_LOCATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");
        static final UUID LEAF_LOCATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        static final UUID OTHER_LEAF_LOCATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222223");
        static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        static final UUID TEMPLATE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

        @BeforeEach
        void setUp() {
                service = new InventoryLedgerService(
                                movementRepository,
                                layoutBlockRepository,
                                productRepository,
                                warehouseLayoutRepository,
                                blockTemplateRepository,
                                gisZoneValidationService,
                                inventoryValidationService);
        }

        @Test
        void receive_shouldRejectNonLeafLocationInActiveLayout() {
                givenActiveLayout();
                givenActiveLayoutBlocks();

                InventoryLedgerException ex = assertThrows(
                                InventoryLedgerException.class,
                                () -> service.receive(ROOT_LOCATION_ID, PRODUCT_ID, BigDecimal.ONE, null, null, null,
                                                null, null,
                                                "actor", false));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Location is not selectable for inventory operations in the active layout",
                                ex.getMessage());
        }

        @Test
        void receive_shouldReturnEnrichedResultWithoutCounterpartLocation() {
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));
                when(movementRepository.save(any())).thenAnswer(invocation -> {
                        StockMovement movement = invocation.getArgument(0);
                        return StockMovement.builder()
                                        .id(UUID.fromString("88888888-8888-8888-8888-888888888888"))
                                        .locationId(movement.getLocationId())
                                        .productId(movement.getProductId())
                                        .qty(movement.getQty())
                                        .type(movement.getType())
                                        .createdBy(movement.getCreatedBy())
                                        .build();
                });
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));

                InventoryLedgerService.MovementResult result = service.receive(
                                LEAF_LOCATION_ID,
                                PRODUCT_ID,
                                BigDecimal.ONE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "actor",
                                false);

                assertEquals("Shelf · 2", result.locationLabel());
                assertEquals("SKU-1", result.productSku());
                assertEquals(null, result.counterpartLocationId());
        }

        @Test
        void transfer_shouldRejectWhenNoActiveLayoutExists() {
                when(warehouseLayoutRepository.findByIsActiveTrue()).thenReturn(Optional.empty());

                InventoryLedgerException ex = assertThrows(
                                InventoryLedgerException.class,
                                () -> service.transfer(LEAF_LOCATION_ID, OTHER_LEAF_LOCATION_ID, PRODUCT_ID,
                                                BigDecimal.ONE, null, null,
                                                null, "actor", false));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Inventory operations require an active warehouse layout", ex.getMessage());
        }

        @Test
        void getStock_shouldEnrichLocationAndProductLabels() {
                when(movementRepository.findAllStock())
                                .thenReturn(List.of(stockEntry(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-A", "12.5000")));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));

                List<InventoryLedgerService.StockResult> result = service.getStock(null, null);

                assertEquals(1, result.size());
                InventoryLedgerService.StockResult row = result.get(0);
                assertEquals("Shelf · 2", row.locationLabel());
                assertEquals("Shelf · 1 / Shelf · 2", row.locationPathLabel());
                assertEquals("SKU-1", row.productSku());
                assertEquals("Sample Product", row.productName());
                assertEquals("EA", row.baseUomCode());
                assertEquals("LOT-A", row.lotNumber());
        }

        @Test
        void transfer_withLotTrackedProduct_checksStockAtSpecificLot() {
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(movementRepository.findStockQtyByLocationProductAndLot(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-B"))
                                .thenReturn(Optional.of(new BigDecimal("1.0000")));

                InventoryLedgerException ex = assertThrows(
                                InventoryLedgerException.class,
                                () -> service.transfer(
                                                LEAF_LOCATION_ID,
                                                OTHER_LEAF_LOCATION_ID,
                                                PRODUCT_ID,
                                                new BigDecimal("2.0000"),
                                                "LOT-B",
                                                null,
                                                null,
                                                "actor",
                                                false));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertTrue(ex.getMessage().contains("Insufficient stock"));
                verify(movementRepository).findStockQtyByLocationProductAndLot(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-B");
        }

        @Test
        void getStock_returnsSeparateRowsPerLot() {
                when(movementRepository.findAllStock()).thenReturn(List.of(
                                stockEntry(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-A", "5.0000"),
                                stockEntry(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-B", "7.0000")));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));

                List<InventoryLedgerService.StockResult> result = service.getStock(null, null);

                assertEquals(2, result.size());
                assertEquals("LOT-A", result.get(0).lotNumber());
                assertEquals("LOT-B", result.get(1).lotNumber());
        }

        @Test
        void adjust_negativeWithLot_checksLotStock() {
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(movementRepository.findStockQtyByLocationProductAndLot(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-A"))
                                .thenReturn(Optional.of(new BigDecimal("1.0000")));

                InventoryLedgerException ex = assertThrows(
                                InventoryLedgerException.class,
                                () -> service.adjust(
                                                LEAF_LOCATION_ID,
                                                PRODUCT_ID,
                                                new BigDecimal("-2.0000"),
                                                "LOT-A",
                                                "manual correction",
                                                null,
                                                null,
                                                "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertTrue(ex.getMessage().contains("Insufficient stock"));
                verify(movementRepository).findStockQtyByLocationProductAndLot(LEAF_LOCATION_ID, PRODUCT_ID, "LOT-A");
        }

        @Test
        void adjust_withReasonCode_persistsReasonCode() {
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(movementRepository.save(any())).thenAnswer(invocation -> {
                        StockMovement movement = invocation.getArgument(0);
                        return StockMovement.builder()
                                        .id(UUID.fromString("99999999-9999-9999-9999-999999999991"))
                                        .locationId(movement.getLocationId())
                                        .productId(movement.getProductId())
                                        .qty(movement.getQty())
                                        .type(movement.getType())
                                        .reasonCode(movement.getReasonCode())
                                        .lotNumber(movement.getLotNumber())
                                        .notes(movement.getNotes())
                                        .createdBy(movement.getCreatedBy())
                                        .build();
                });

                InventoryLedgerService.MovementResult result = service.adjust(
                                LEAF_LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("3.0000"),
                                "LOT-A",
                                "manual correction",
                                null,
                                "DAMAGED",
                                "actor");

                assertEquals("DAMAGED", result.reasonCode());
        }

        @Test
        void receive_withSourceDocumentId_persistsDocumentId() {
                UUID sourceDocumentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(movementRepository.save(any())).thenAnswer(invocation -> {
                        StockMovement movement = invocation.getArgument(0);
                        return StockMovement.builder()
                                        .id(UUID.fromString("99999999-9999-9999-9999-999999999992"))
                                        .locationId(movement.getLocationId())
                                        .productId(movement.getProductId())
                                        .qty(movement.getQty())
                                        .type(movement.getType())
                                        .sourceDocumentId(movement.getSourceDocumentId())
                                        .reasonCode(movement.getReasonCode())
                                        .lotNumber(movement.getLotNumber())
                                        .notes(movement.getNotes())
                                        .createdBy(movement.getCreatedBy())
                                        .build();
                });

                InventoryLedgerService.MovementResult result = service.receive(
                                LEAF_LOCATION_ID,
                                PRODUCT_ID,
                                BigDecimal.ONE,
                                "LOT-A",
                                null,
                                null,
                                sourceDocumentId,
                                null,
                                "actor",
                                false);

                assertEquals(sourceDocumentId, result.sourceDocumentId());
        }

        @Test
        void adjust_withNullReasonCode_persistsNull() {
                givenActiveLayout();
                givenActiveLayoutBlocks();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(movementRepository.save(any())).thenAnswer(invocation -> {
                        StockMovement movement = invocation.getArgument(0);
                        return StockMovement.builder()
                                        .id(UUID.fromString("99999999-9999-9999-9999-999999999993"))
                                        .locationId(movement.getLocationId())
                                        .productId(movement.getProductId())
                                        .qty(movement.getQty())
                                        .type(movement.getType())
                                        .reasonCode(movement.getReasonCode())
                                        .lotNumber(movement.getLotNumber())
                                        .notes(movement.getNotes())
                                        .createdBy(movement.getCreatedBy())
                                        .build();
                });

                InventoryLedgerService.MovementResult result = service.adjust(
                                LEAF_LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("1.0000"),
                                null,
                                "manual correction",
                                null,
                                null,
                                "actor");

                assertEquals(null, result.reasonCode());
        }

        @Test
        void getMovements_shouldIncludeCounterpartLocationForTransfers() {
                UUID ref = UUID.fromString("55555555-5555-5555-5555-555555555555");
                StockMovement out = StockMovement.builder()
                                .id(UUID.fromString("66666666-6666-6666-6666-666666666661"))
                                .locationId(LEAF_LOCATION_ID)
                                .productId(PRODUCT_ID)
                                .qty(new BigDecimal("-2.0000"))
                                .type(MovementType.TRANSFER_OUT)
                                .referenceId(ref)
                                .createdBy("actor")
                                .build();
                StockMovement in = StockMovement.builder()
                                .id(UUID.fromString("66666666-6666-6666-6666-666666666662"))
                                .locationId(OTHER_LEAF_LOCATION_ID)
                                .productId(PRODUCT_ID)
                                .qty(new BigDecimal("2.0000"))
                                .type(MovementType.TRANSFER_IN)
                                .referenceId(ref)
                                .createdBy("actor")
                                .build();

                when(movementRepository.findAll(
                                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<StockMovement>>any(),
                                org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any()))
                                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(out)));
                when(movementRepository.findByReferenceIdIn(List.of(ref))).thenReturn(List.of(out, in));
                when(layoutBlockRepository.findAllById(any()))
                                .thenReturn(List.of(leafBlock(), otherLeafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock(), otherLeafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));

                InventoryLedgerService.MovementPageResult page = service.getMovements(null, null, 0, 50);

                assertEquals(1, page.content().size());
                InventoryLedgerService.MovementResult row = page.content().get(0);
                assertEquals(OTHER_LEAF_LOCATION_ID, row.counterpartLocationId());
                assertEquals("Shelf · 3", row.counterpartLocationLabel());
        }

        @Test
        void getStock_filteredByLocationKind_returnsOnlyMatchingLocations() {
                WarehouseLocationKind stagingKind = WarehouseLocationKind.builder()
                                .id(UUID.randomUUID())
                                .name("STAGING")
                                .sortOrder(1)
                                .build();

                LayoutBlock stagingLeaf = LayoutBlock.builder()
                                .id(OTHER_LEAF_LOCATION_ID)
                                .layoutId(ACTIVE_LAYOUT_ID)
                                .blockTemplateId(TEMPLATE_ID)
                                .parentId(ROOT_LOCATION_ID)
                                .position(2)
                                .locationKind(stagingKind)
                                .build();

                when(movementRepository.findAllStock()).thenReturn(List.of(
                                stockEntry(LEAF_LOCATION_ID, PRODUCT_ID, null, "5.0000"),
                                stockEntry(OTHER_LEAF_LOCATION_ID, PRODUCT_ID, null, "3.0000")));
                when(layoutBlockRepository.findAllById(any()))
                                .thenReturn(List.of(leafBlock(), stagingLeaf));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock(), stagingLeaf));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));

                List<InventoryLedgerService.StockResult> result = service.getStock(null, null, "STAGING");

                assertEquals(1, result.size());
                assertEquals(OTHER_LEAF_LOCATION_ID, result.get(0).locationId());
        }

        @Test
        void getMovements_filteredBySourceDocumentId_returnsMatchingMovements() {
                UUID sourceDocId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
                StockMovement movement = StockMovement.builder()
                                .id(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
                                .locationId(LEAF_LOCATION_ID)
                                .productId(PRODUCT_ID)
                                .qty(BigDecimal.ONE)
                                .type(MovementType.RECEIVE)
                                .sourceDocumentId(sourceDocId)
                                .createdBy("actor")
                                .build();

                when(movementRepository.findAll(
                                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<StockMovement>>any(),
                                org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any()))
                                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(movement)));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafBlock()));
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock()));
                when(blockTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
                when(warehouseLayoutRepository.findAllById(any())).thenReturn(List.of(activeLayout()));
                when(productRepository.findAllById(any())).thenReturn(List.of(product()));

                InventoryLedgerService.MovementPageResult page = service.getMovements(null, null, sourceDocId, null, 0,
                                50);

                assertEquals(1, page.content().size());
                assertEquals(sourceDocId, page.content().get(0).sourceDocumentId());
        }

        private void givenActiveLayout() {
                when(warehouseLayoutRepository.findByIsActiveTrue()).thenReturn(Optional.of(activeLayout()));
        }

        private void givenActiveLayoutBlocks() {
                when(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(ACTIVE_LAYOUT_ID))
                                .thenReturn(List.of(rootBlock(), leafBlock(), otherLeafBlock()));
        }

        private WarehouseLayout activeLayout() {
                return WarehouseLayout.builder()
                                .id(ACTIVE_LAYOUT_ID)
                                .name("Main Layout")
                                .isActive(true)
                                .build();
        }

        private LayoutBlock rootBlock() {
                return LayoutBlock.builder()
                                .id(ROOT_LOCATION_ID)
                                .layoutId(ACTIVE_LAYOUT_ID)
                                .blockTemplateId(TEMPLATE_ID)
                                .parentId(null)
                                .position(0)
                                .build();
        }

        private LayoutBlock leafBlock() {
                return LayoutBlock.builder()
                                .id(LEAF_LOCATION_ID)
                                .layoutId(ACTIVE_LAYOUT_ID)
                                .blockTemplateId(TEMPLATE_ID)
                                .parentId(ROOT_LOCATION_ID)
                                .position(1)
                                .build();
        }

        private LayoutBlock otherLeafBlock() {
                return LayoutBlock.builder()
                                .id(OTHER_LEAF_LOCATION_ID)
                                .layoutId(ACTIVE_LAYOUT_ID)
                                .blockTemplateId(TEMPLATE_ID)
                                .parentId(ROOT_LOCATION_ID)
                                .position(2)
                                .build();
        }

        private BlockTemplate template() {
                return BlockTemplate.builder()
                                .id(TEMPLATE_ID)
                                .name("Shelf")
                                .identifierFormat(BlockTemplate.IdentifierFormat.NUMERIC)
                                .sideConfig(BlockTemplate.SideConfig.NONE)
                                .required(true)
                                .build();
        }

        private Product product() {
                UnitOfMeasure uom = new UnitOfMeasure();
                uom.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
                uom.setCode("EA");
                uom.setName("Each");

                return Product.builder()
                                .id(PRODUCT_ID)
                                .sku("SKU-1")
                                .name("Sample Product")
                                .baseUom(uom)
                                .trackLot(true)
                                .trackExpiry(false)
                                .active(true)
                                .build();
        }

        private StockEntry stockEntry(UUID locationId, UUID productId, String lotNumber, String qty) {
                return new StockEntry() {
                        @Override
                        public UUID getLocationId() {
                                return locationId;
                        }

                        @Override
                        public UUID getProductId() {
                                return productId;
                        }

                        @Override
                        public String getLotNumber() {
                                return lotNumber;
                        }

                        @Override
                        public BigDecimal getQtyStock() {
                                return new BigDecimal(qty);
                        }
                };
        }
}
