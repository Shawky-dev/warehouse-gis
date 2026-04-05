package com.warehouse.warehouse_platform.tenant.receipt;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
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
class ReceiptServiceTest {

        @Mock
        ReceiptDocumentRepository receiptDocumentRepository;
        @Mock
        ReceiptLineRepository receiptLineRepository;
        @Mock
        SupplierRepository supplierRepository;
        @Mock
        ProductRepository productRepository;
        @Mock
        LayoutBlockRepository layoutBlockRepository;
        @Mock
        BlockTemplateRepository blockTemplateRepository;
        @Mock
        InventoryLedgerService inventoryLedgerService;

        ReceiptService service;

        private static final UUID RECEIPT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID LINE_ID_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        private static final UUID LINE_ID_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        private static final UUID PRODUCT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID LOCATION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        @BeforeEach
        void setUp() {
                service = new ReceiptService(
                                receiptDocumentRepository,
                                receiptLineRepository,
                                supplierRepository,
                                productRepository,
                                layoutBlockRepository,
                                blockTemplateRepository,
                                inventoryLedgerService);
        }

        @Test
        void createDraft_returnsReceiptWithDraftStatus() {
                when(receiptDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                ReceiptService.ReceiptDetailResult result = service.createDraft(null, "RC-001", "initial", "actor");

                assertEquals(ReceiptStatus.DRAFT, result.status());
                assertEquals("RC-001", result.reference());
                assertEquals("initial", result.notes());
        }

        @Test
        void addLine_withLotTrackedProduct_requiresLotNumber() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(activeProduct(true, false)));
                when(layoutBlockRepository.findById(LOCATION_ID)).thenReturn(Optional.of(storageLeaf()));
                when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.addLine(
                                                RECEIPT_ID,
                                                PRODUCT_ID,
                                                LOCATION_ID,
                                                new BigDecimal("2.0000"),
                                                null,
                                                null,
                                                null,
                                                "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Lot number is required for lot-tracked products", ex.getMessage());
        }

        @Test
        void addLine_toNonLeafLocation_throws() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(activeProduct(false, false)));
                when(layoutBlockRepository.findById(LOCATION_ID)).thenReturn(Optional.of(storageLeaf()));
                when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(true);

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.addLine(
                                                RECEIPT_ID,
                                                PRODUCT_ID,
                                                LOCATION_ID,
                                                new BigDecimal("2.0000"),
                                                null,
                                                null,
                                                null,
                                                "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Destination location must be a leaf block", ex.getMessage());
        }

        @Test
        void addLine_toDispatchLocation_throws() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(activeProduct(false, false)));
                when(layoutBlockRepository.findById(LOCATION_ID)).thenReturn(Optional.of(dispatchLeaf()));
                when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.addLine(
                                                RECEIPT_ID,
                                                PRODUCT_ID,
                                                LOCATION_ID,
                                                new BigDecimal("2.0000"),
                                                null,
                                                null,
                                                null,
                                                "actor"));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Destination location kind DISPATCH is not allowed", ex.getMessage());
        }

        @Test
        void postReceipt_callsReceiveForEachLine() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));
                when(receiptLineRepository.findByReceiptIdOrderByPosition(RECEIPT_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("2.0000"), "LOT-A", 0),
                                line(LINE_ID_2, new BigDecimal("3.0000"), "LOT-B", 1)));
                when(receiptDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true, false)));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.postReceipt(RECEIPT_ID, "actor", false);

                verify(inventoryLedgerService, times(2)).receive(
                                eq(LOCATION_ID),
                                eq(PRODUCT_ID),
                                any(BigDecimal.class),
                                any(),
                                any(),
                                any(),
                                eq(RECEIPT_ID),
                                eq(null),
                                eq("actor"),
                                eq(false));
        }

        @Test
        void postReceipt_withNoLines_throws() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));
                when(receiptLineRepository.findByReceiptIdOrderByPosition(RECEIPT_ID)).thenReturn(List.of());

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.postReceipt(RECEIPT_ID, "actor", false));

                assertEquals("BAD_REQUEST", ex.getCode());
                assertEquals("Receipt must contain at least one line before posting", ex.getMessage());
        }

        @Test
        void postReceipt_alreadyPosted_throws() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(postedReceipt()));

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.postReceipt(RECEIPT_ID, "actor", false));

                assertEquals("CONFLICT", ex.getCode());
                assertEquals("Only draft receipts can be posted", ex.getMessage());
        }

        @Test
        void voidReceipt_createsNegativeAdjustPerLine() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(postedReceipt()));
                when(receiptLineRepository.findByReceiptIdOrderByPosition(RECEIPT_ID)).thenReturn(List.of(
                                line(LINE_ID_1, new BigDecimal("2.0000"), "LOT-A", 0),
                                line(LINE_ID_2, new BigDecimal("3.0000"), null, 1)));
                when(receiptDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct(true, false)));
                when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(storageLeaf()));

                service.voidReceipt(RECEIPT_ID, "actor");

                verify(inventoryLedgerService).adjust(
                                LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("-2.0000"),
                                "LOT-A",
                                "Void receipt NO_REF (" + RECEIPT_ID + ")",
                                RECEIPT_ID,
                                "VOID_RECEIPT",
                                "actor");
                verify(inventoryLedgerService).adjust(
                                LOCATION_ID,
                                PRODUCT_ID,
                                new BigDecimal("-3.0000"),
                                null,
                                "Void receipt NO_REF (" + RECEIPT_ID + ")",
                                RECEIPT_ID,
                                "VOID_RECEIPT",
                                "actor");
        }

        @Test
        void voidReceipt_onDraftReceipt_throws() {
                when(receiptDocumentRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(draftReceipt()));

                ReceiptManagementException ex = assertThrows(
                                ReceiptManagementException.class,
                                () -> service.voidReceipt(RECEIPT_ID, "actor"));

                assertEquals("CONFLICT", ex.getCode());
                assertEquals("Only posted receipts can be voided", ex.getMessage());
                verify(inventoryLedgerService, never()).adjust(
                                any(), any(), any(), any(), any(), any(), any(), any());
        }

        private ReceiptDocument draftReceipt() {
                return ReceiptDocument.builder()
                                .id(RECEIPT_ID)
                                .status(ReceiptStatus.DRAFT)
                                .createdBy("creator")
                                .build();
        }

        private ReceiptDocument postedReceipt() {
                return ReceiptDocument.builder()
                                .id(RECEIPT_ID)
                                .status(ReceiptStatus.POSTED)
                                .createdBy("creator")
                                .build();
        }

        private ReceiptLine line(UUID id, BigDecimal qty, String lot, int position) {
                return ReceiptLine.builder()
                                .id(id)
                                .receiptId(RECEIPT_ID)
                                .productId(PRODUCT_ID)
                                .destinationLocationId(LOCATION_ID)
                                .qty(qty)
                                .lotNumber(lot)
                                .position(position)
                                .build();
        }

        private Product activeProduct(boolean trackLot, boolean trackExpiry) {
                return Product.builder()
                                .id(PRODUCT_ID)
                                .sku("SKU-1")
                                .name("Product")
                                .trackLot(trackLot)
                                .trackExpiry(trackExpiry)
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

        private LayoutBlock dispatchLeaf() {
                return LayoutBlock.builder()
                                .id(LOCATION_ID)
                                .fullCode("DISPATCH-01")
                                .locationKind(WarehouseLocationKind.builder().name("Dispatch").build())
                                .build();
        }
}
