package com.warehouse.warehouse_platform.tenant.dispatch;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DispatchServiceTest {

        @Mock
        DispatchDocumentRepository dispatchDocumentRepository;
        @Mock
        DispatchLineRepository dispatchLineRepository;
        @Mock
        ProductRepository productRepository;
        @Mock
        LayoutBlockRepository layoutBlockRepository;
        @Mock
        BlockTemplateRepository blockTemplateRepository;
        @Mock
        StockMovementRepository stockMovementRepository;
        @Mock
        InventoryLedgerService inventoryLedgerService;

        DispatchService service;

        private static final UUID DISPATCH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID LINE_ID_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        private static final UUID LINE_ID_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        private static final UUID PRODUCT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID LOCATION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        @BeforeEach
        void setUp() {
                service = new DispatchService(
                                dispatchDocumentRepository,
                                dispatchLineRepository,
                                productRepository,
                                layoutBlockRepository,
                                blockTemplateRepository,
                                stockMovementRepository,
                                inventoryLedgerService);
        }

        @Test
        void addLine_toLotTrackedProduct_requiresLot() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(activeProduct(true)));
                when(layoutBlockRepository.findById(LOCATION_ID)).thenReturn(Optional.of(storageLeaf()));
                when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);

                DispatchManagementException ex = assertThrows(
                                DispatchManagementException.class,
                                () -> service.addLine(
                                                DISPATCH_ID,
                                                PRODUCT_ID,
                                                LOCATION_ID,
                                                new BigDecimal("2.0000"),
                                                null,
                                                null,
                                                "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Lot number is required for lot-tracked products", ex.getMessage());
        }

        @Test
        void postDispatch_withInsufficientStock_throwsBeforeAnyMovement() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));
                when(dispatchLineRepository.findByDispatchIdOrderByPosition(DISPATCH_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("5.0000"), null, 0)));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(false)));
                when(stockMovementRepository.findStockQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                                .thenReturn(Optional.of(new BigDecimal("2.0000")));

                DispatchManagementException ex = assertThrows(
                                DispatchManagementException.class,
                                () -> service.postDispatch(DISPATCH_ID, "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                verify(inventoryLedgerService, never()).pick(any(), any(), any(), any(), any(), any());
        }

        @Test
        void postDispatch_withLotTrackedProduct_checksLotStock() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));
                when(dispatchLineRepository.findByDispatchIdOrderByPosition(DISPATCH_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("5.0000"), "LOT-A", 0)));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true)));
                when(stockMovementRepository.findStockQtyByLocationProductAndLot(LOCATION_ID, PRODUCT_ID, "LOT-A"))
                                .thenReturn(Optional.of(new BigDecimal("10.0000")));
                when(dispatchDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true)));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.postDispatch(DISPATCH_ID, "actor");

                verify(stockMovementRepository).findStockQtyByLocationProductAndLot(LOCATION_ID, PRODUCT_ID, "LOT-A");
                verify(stockMovementRepository, never()).findStockQtyByLocationAndProduct(any(), any());
        }

        @Test
        void postDispatch_createsPickMovementPerLine() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));
                when(dispatchLineRepository.findByDispatchIdOrderByPosition(DISPATCH_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("2.0000"), "LOT-A", 0),
                                line(LINE_ID_2, new BigDecimal("3.0000"), "LOT-B", 1)));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true)));
                when(stockMovementRepository.findStockQtyByLocationProductAndLot(eq(LOCATION_ID), eq(PRODUCT_ID),
                                any()))
                                .thenReturn(Optional.of(new BigDecimal("10.0000")));
                when(dispatchDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.postDispatch(DISPATCH_ID, "actor");

                verify(inventoryLedgerService, times(2)).pick(
                                eq(LOCATION_ID),
                                eq(PRODUCT_ID),
                                any(BigDecimal.class),
                                any(),
                                eq(DISPATCH_ID),
                                eq("actor"));
        }

        @Test
        void postDispatch_stampsSourceDocumentId() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));
                when(dispatchLineRepository.findByDispatchIdOrderByPosition(DISPATCH_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("2.0000"), "LOT-A", 0)));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true)));
                when(stockMovementRepository.findStockQtyByLocationProductAndLot(LOCATION_ID, PRODUCT_ID, "LOT-A"))
                                .thenReturn(Optional.of(new BigDecimal("10.0000")));
                when(dispatchDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.postDispatch(DISPATCH_ID, "actor");

                verify(inventoryLedgerService).pick(
                                LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("2.0000"),
                                "LOT-A",
                                DISPATCH_ID,
                                "actor");
        }

        @Test
        void voidDispatch_createsReceiveMovementPerLine() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(postedDispatch()));
                when(dispatchLineRepository.findByDispatchIdOrderByPosition(DISPATCH_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("2.0000"), "LOT-A", 0),
                                line(LINE_ID_2, new BigDecimal("3.0000"), null, 1)));
                when(dispatchDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true)));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.voidDispatch(DISPATCH_ID, "actor");

                verify(inventoryLedgerService).receive(
                                LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("2.0000"),
                                "LOT-A",
                                null,
                                "Void dispatch NO_REF (" + DISPATCH_ID + ")",
                                DISPATCH_ID,
                                "VOID_DISPATCH",
                                "actor",
                                false);
                verify(inventoryLedgerService).receive(
                                LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("3.0000"),
                                null,
                                null,
                                "Void dispatch NO_REF (" + DISPATCH_ID + ")",
                                DISPATCH_ID,
                                "VOID_DISPATCH",
                                "actor",
                                false);
        }

        @Test
        void voidDispatch_onDraftDispatch_throws() {
                when(dispatchDocumentRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(draftDispatch()));

                DispatchManagementException ex = assertThrows(
                                DispatchManagementException.class,
                                () -> service.voidDispatch(DISPATCH_ID, "actor"));

                assertEquals("CONFLICT", ex.getCode());
                assertEquals("Only posted dispatches can be voided", ex.getMessage());
                verify(inventoryLedgerService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(),
                                any(), any());
        }

        private DispatchDocument draftDispatch() {
                return DispatchDocument.builder()
                                .id(DISPATCH_ID)
                                .status(DispatchStatus.DRAFT)
                                .createdBy("creator")
                                .build();
        }

        private DispatchDocument postedDispatch() {
                return DispatchDocument.builder()
                                .id(DISPATCH_ID)
                                .status(DispatchStatus.POSTED)
                                .createdBy("creator")
                                .build();
        }

        private DispatchLine line(UUID id, BigDecimal qty, String lot, int position) {
                return DispatchLine.builder()
                                .id(id)
                                .dispatchId(DISPATCH_ID)
                                .productId(PRODUCT_ID)
                                .sourceLocationId(LOCATION_ID)
                                .qty(qty)
                                .lotNumber(lot)
                                .position(position)
                                .build();
        }

        private Product activeProduct(boolean trackLot) {
                return Product.builder()
                                .id(PRODUCT_ID)
                                .sku("SKU-1")
                                .name("Product")
                                .trackLot(trackLot)
                                .trackExpiry(false)
                                .active(true)
                                .build();
        }

        private LayoutBlock storageLeaf() {
                return LayoutBlock.builder()
                                .id(LOCATION_ID)
                                .fullCode("A-01-L1-S1")
                                .locationKind(WarehouseLocationKind.builder().name("Storage").build())
                                .build();
        }
}
