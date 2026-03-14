package com.warehouse.warehouse_platform.tenant.counting;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.inventory.StockEntry;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CountSessionServiceTest {

    @Mock
    CountSessionRepository countSessionRepository;
    @Mock
    CountLineRepository countLineRepository;
    @Mock
    LayoutBlockRepository layoutBlockRepository;
    @Mock
    BlockTemplateRepository blockTemplateRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    StockMovementRepository stockMovementRepository;
    @Mock
    InventoryLedgerService inventoryLedgerService;

    CountSessionService service;

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LINE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LOCATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PRODUCT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @BeforeEach
    void setUp() {
        service = new CountSessionService(
                countSessionRepository,
                countLineRepository,
                layoutBlockRepository,
                blockTemplateRepository,
                productRepository,
                stockMovementRepository,
                inventoryLedgerService);
    }

    @Test
    void openSession_snapshotsOnHandIntoLines() {
        CountSession session = openSessionEntity();
        when(layoutBlockRepository.findAllById(List.of(LOCATION_ID))).thenReturn(List.of(leafLocation()));
        when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);
        when(countSessionRepository.save(any())).thenReturn(session);
        when(stockMovementRepository.findStockByLocationIds(List.of(LOCATION_ID))).thenReturn(List.of(
                stockEntry(LOCATION_ID, PRODUCT_ID, null, new BigDecimal("7.5000"))));
        when(countLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findAllById(List.of(PRODUCT_ID))).thenReturn(List.of(product()));

        CountSessionService.CountSessionDetailResult result = service.openSession(
                "Cycle Count",
                List.of(LOCATION_ID),
                "actor");

        assertEquals(SESSION_ID, result.id());
        assertEquals(1, result.lines().size());
        assertEquals(new BigDecimal("7.5000"), result.lines().get(0).expectedQty());
    }

    @Test
    void openSession_withLotTrackedProduct_createsLotLevelLines() {
        CountSession session = openSessionEntity();
        when(layoutBlockRepository.findAllById(List.of(LOCATION_ID))).thenReturn(List.of(leafLocation()));
        when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);
        when(countSessionRepository.save(any())).thenReturn(session);
        when(stockMovementRepository.findStockByLocationIds(List.of(LOCATION_ID))).thenReturn(List.of(
                stockEntry(LOCATION_ID, PRODUCT_ID, "LOT-A", new BigDecimal("5.0000")),
                stockEntry(LOCATION_ID, PRODUCT_ID, "LOT-B", new BigDecimal("2.0000"))));
        when(countLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findAllById(List.of(PRODUCT_ID))).thenReturn(List.of(product()));

        CountSessionService.CountSessionDetailResult result = service.openSession(
                "Cycle Count",
                List.of(LOCATION_ID),
                "actor");

        assertEquals(2, result.lines().size());
        assertEquals("LOT-A", result.lines().get(0).lotNumber());
        assertEquals("LOT-B", result.lines().get(1).lotNumber());
    }

    @Test
    void openSession_withEmptyLocation_createsNoLines() {
        CountSession session = openSessionEntity();
        when(layoutBlockRepository.findAllById(List.of(LOCATION_ID))).thenReturn(List.of(leafLocation()));
        when(layoutBlockRepository.existsByParentId(LOCATION_ID)).thenReturn(false);
        when(countSessionRepository.save(any())).thenReturn(session);
        when(stockMovementRepository.findStockByLocationIds(List.of(LOCATION_ID))).thenReturn(List.of());

        CountSessionService.CountSessionDetailResult result = service.openSession(
                "Cycle Count",
                List.of(LOCATION_ID),
                "actor");

        assertEquals(0, result.lines().size());
        verify(countLineRepository, never()).saveAll(any());
    }

    @Test
    void updateCountLine_withNegativeQty_throws() {
        CountSessionManagementException ex = assertThrows(
                CountSessionManagementException.class,
                () -> service.updateCountLine(SESSION_ID, LINE_ID, new BigDecimal("-1.0000"), "actor"));

        assertEquals("BAD_REQUEST", ex.getCode());
        verify(countSessionRepository, never()).findById(any());
    }

    @Test
    void postSession_withUnfilledLines_throws() {
        when(countSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(openSessionEntity()));
        when(countLineRepository.findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(SESSION_ID))
                .thenReturn(List.of(
                        countLine(new BigDecimal("3.0000"), null, null)));

        CountSessionManagementException ex = assertThrows(
                CountSessionManagementException.class,
                () -> service.postSession(SESSION_ID, "actor"));

        assertEquals("BAD_REQUEST", ex.getCode());
        verify(inventoryLedgerService, never()).adjust(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void postSession_writesAdjustOnlyForVariances() {
        when(countSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(openSessionEntity()));
        when(countLineRepository.findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(SESSION_ID))
                .thenReturn(List.of(
                        countLine(new BigDecimal("5.0000"), new BigDecimal("8.0000"), "LOT-A"),
                        countLine(new BigDecimal("6.0000"), new BigDecimal("4.0000"), null)));
        when(countSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafLocation()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product()));

        service.postSession(SESSION_ID, "actor");

        verify(inventoryLedgerService).adjust(
                LOCATION_ID,
                PRODUCT_ID,
                new BigDecimal("3.0000"),
                "LOT-A",
                "Count session Cycle Count (" + SESSION_ID + ")",
                SESSION_ID,
                "COUNT_ADJUSTMENT",
                "actor");
        verify(inventoryLedgerService).adjust(
                LOCATION_ID,
                PRODUCT_ID,
                new BigDecimal("-2.0000"),
                null,
                "Count session Cycle Count (" + SESSION_ID + ")",
                SESSION_ID,
                "COUNT_ADJUSTMENT",
                "actor");
    }

    @Test
    void postSession_zeroVarianceLines_writesNoMovement() {
        when(countSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(openSessionEntity()));
        when(countLineRepository.findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(SESSION_ID))
                .thenReturn(List.of(
                        countLine(new BigDecimal("5.0000"), new BigDecimal("5.0000"), null)));
        when(countSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(layoutBlockRepository.findAllById(any())).thenReturn(List.of(leafLocation()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product()));

        service.postSession(SESSION_ID, "actor");

        verify(inventoryLedgerService, never()).adjust(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void voidSession_onPostedSession_throws() {
        when(countSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(postedSessionEntity()));

        CountSessionManagementException ex = assertThrows(
                CountSessionManagementException.class,
                () -> service.voidSession(SESSION_ID, "actor"));

        assertEquals("CONFLICT", ex.getCode());
        verify(countSessionRepository, never()).save(any());
    }

    private CountSession openSessionEntity() {
        return CountSession.builder()
                .id(SESSION_ID)
                .name("Cycle Count")
                .status(CountStatus.OPEN)
                .createdBy("creator")
                .locationIds(new LinkedHashSet<>(List.of(LOCATION_ID)))
                .build();
    }

    private CountSession postedSessionEntity() {
        return CountSession.builder()
                .id(SESSION_ID)
                .name("Cycle Count")
                .status(CountStatus.POSTED)
                .createdBy("creator")
                .locationIds(new LinkedHashSet<>(List.of(LOCATION_ID)))
                .build();
    }

    private CountLine countLine(BigDecimal expected, BigDecimal counted, String lotNumber) {
        return CountLine.builder()
                .id(LINE_ID)
                .sessionId(SESSION_ID)
                .locationId(LOCATION_ID)
                .productId(PRODUCT_ID)
                .lotNumber(lotNumber)
                .expectedQty(expected)
                .countedQty(counted)
                .build();
    }

    private LayoutBlock leafLocation() {
        return LayoutBlock.builder()
                .id(LOCATION_ID)
                .fullCode("A-01-L1-S1")
                .build();
    }

    private Product product() {
        return Product.builder()
                .id(PRODUCT_ID)
                .sku("SKU-1")
                .name("Product")
                .active(true)
                .build();
    }

    private StockEntry stockEntry(UUID locationId, UUID productId, String lotNumber, BigDecimal qtyStock) {
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
                return qtyStock;
            }
        };
    }
}