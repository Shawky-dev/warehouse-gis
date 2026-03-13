package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InventoryLedgerServiceTest {

    @Mock StockMovementRepository movementRepository;
    @Mock LayoutBlockRepository layoutBlockRepository;
    @Mock ProductRepository productRepository;

    InventoryLedgerService service;

    static final UUID LOCATION_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    static final UUID LOCATION_B_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000002");
    static final UUID PRODUCT_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000001");
    static final String ACTOR = "test@example.com";

    @BeforeEach
    void setUp() {
        service = new InventoryLedgerService(movementRepository, layoutBlockRepository, productRepository);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void givenLocationExists(UUID locationId) {
        when(layoutBlockRepository.existsById(locationId)).thenReturn(true);
    }

    private void givenProductExists(UUID productId) {
        when(productRepository.existsById(productId)).thenReturn(true);
    }

    private OnHandEntry onHandEntry(UUID locationId, UUID productId, String qty) {
        return new OnHandEntry() {
            public UUID getLocationId() { return locationId; }
            public UUID getProductId() { return productId; }
            public BigDecimal getQtyOnHand() { return new BigDecimal(qty); }
        };
    }

    private StockMovement savedMovement(UUID id) {
        return StockMovement.builder()
                .id(id)
                .locationId(LOCATION_ID)
                .productId(PRODUCT_ID)
                .qty(BigDecimal.TEN)
                .type(MovementType.RECEIVE)
                .createdBy(ACTOR)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RECEIVE
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class Receive {

        @Test
        void shouldSaveReceiveMovementWithPositiveQty() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            UUID id = UUID.randomUUID();
            when(movementRepository.save(any())).thenAnswer(inv -> {
                StockMovement m = inv.getArgument(0);
                return StockMovement.builder()
                        .id(id).locationId(m.getLocationId()).productId(m.getProductId())
                        .qty(m.getQty()).type(m.getType()).notes(m.getNotes())
                        .lotNumber(m.getLotNumber()).expiryDate(m.getExpiryDate())
                        .createdBy(m.getCreatedBy()).build();
            });

            InventoryLedgerService.MovementResult result = service.receive(
                    LOCATION_ID, PRODUCT_ID, new BigDecimal("10.5"),
                    "LOT-001", LocalDate.of(2027, 1, 1), "inbound shipment", ACTOR);

            assertEquals(id, result.id());
            assertEquals(MovementType.RECEIVE, result.type());
            assertEquals(new BigDecimal("10.5"), result.qty());
            assertEquals("LOT-001", result.lotNumber());
            assertEquals(LocalDate.of(2027, 1, 1), result.expiryDate());

            ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
            verify(movementRepository).save(captor.capture());
            assertEquals(MovementType.RECEIVE, captor.getValue().getType());
            assertNull(captor.getValue().getReferenceId());
        }

        @Test
        void shouldRejectZeroQty() {
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.receive(LOCATION_ID, PRODUCT_ID, BigDecimal.ZERO, null, null, null, ACTOR));
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        void shouldRejectNegativeQty() {
            assertThrows(InventoryLedgerException.class,
                    () -> service.receive(LOCATION_ID, PRODUCT_ID, new BigDecimal("-1"), null, null, null, ACTOR));
        }

        @Test
        void shouldRejectUnknownLocation() {
            when(layoutBlockRepository.existsById(LOCATION_ID)).thenReturn(false);
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.receive(LOCATION_ID, PRODUCT_ID, BigDecimal.ONE, null, null, null, ACTOR));
            assertEquals("NOT_FOUND", ex.getCode());
        }

        @Test
        void shouldRejectUnknownProduct() {
            givenLocationExists(LOCATION_ID);
            when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.receive(LOCATION_ID, PRODUCT_ID, BigDecimal.ONE, null, null, null, ACTOR));
            assertEquals("NOT_FOUND", ex.getCode());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSFER
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class Transfer {

        @Test
        void shouldSaveTwoMovementsWithSharedReferenceId() {
            givenLocationExists(LOCATION_ID);
            givenLocationExists(LOCATION_B_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("50")));
            when(movementRepository.save(any())).thenAnswer(inv -> {
                StockMovement m = inv.getArgument(0);
                return StockMovement.builder().id(UUID.randomUUID())
                        .locationId(m.getLocationId()).productId(m.getProductId())
                        .qty(m.getQty()).type(m.getType()).referenceId(m.getReferenceId())
                        .createdBy(m.getCreatedBy()).build();
            });

            InventoryLedgerService.TransferResult result = service.transfer(
                    LOCATION_ID, LOCATION_B_ID, PRODUCT_ID, new BigDecimal("10"), null, "move", ACTOR);

            assertNotNull(result.referenceId());
            assertEquals(result.referenceId(), result.out().referenceId());
            assertEquals(result.referenceId(), result.in().referenceId());
            assertEquals(MovementType.TRANSFER_OUT, result.out().type());
            assertEquals(MovementType.TRANSFER_IN, result.in().type());
            assertTrue(result.out().qty().compareTo(BigDecimal.ZERO) < 0, "out qty must be negative");
            assertTrue(result.in().qty().compareTo(BigDecimal.ZERO) > 0, "in qty must be positive");

            verify(movementRepository, times(2)).save(any());
        }

        @Test
        void shouldRejectSameSourceAndDestination() {
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.transfer(LOCATION_ID, LOCATION_ID, PRODUCT_ID, BigDecimal.TEN, null, null, ACTOR));
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        void shouldRejectInsufficientStock() {
            givenLocationExists(LOCATION_ID);
            givenLocationExists(LOCATION_B_ID);
            givenProductExists(PRODUCT_ID);
            // on-hand is 5, trying to transfer 10
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("5")));
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.transfer(LOCATION_ID, LOCATION_B_ID, PRODUCT_ID, new BigDecimal("10"), null, null, ACTOR));
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        void shouldRejectZeroTransferQty() {
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.transfer(LOCATION_ID, LOCATION_B_ID, PRODUCT_ID, BigDecimal.ZERO, null, null, ACTOR));
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        void shouldAllowExactFullStockTransfer() {
            givenLocationExists(LOCATION_ID);
            givenLocationExists(LOCATION_B_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("10")));
            when(movementRepository.save(any())).thenAnswer(inv -> {
                StockMovement m = inv.getArgument(0);
                return StockMovement.builder().id(UUID.randomUUID())
                        .locationId(m.getLocationId()).productId(m.getProductId())
                        .qty(m.getQty()).type(m.getType()).referenceId(m.getReferenceId())
                        .createdBy(m.getCreatedBy()).build();
            });
            // should not throw
            assertDoesNotThrow(() -> service.transfer(
                    LOCATION_ID, LOCATION_B_ID, PRODUCT_ID, new BigDecimal("10"), null, null, ACTOR));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ADJUST
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class Adjust {

        @Test
        void shouldSavePositiveAdjustment() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.save(any())).thenAnswer(inv -> {
                StockMovement m = inv.getArgument(0);
                return StockMovement.builder().id(UUID.randomUUID())
                        .locationId(m.getLocationId()).productId(m.getProductId())
                        .qty(m.getQty()).type(m.getType()).notes(m.getNotes())
                        .createdBy(m.getCreatedBy()).build();
            });

            InventoryLedgerService.MovementResult result = service.adjust(
                    LOCATION_ID, PRODUCT_ID, new BigDecimal("5"), "found extra units", ACTOR);

            assertEquals(MovementType.ADJUST, result.type());
            assertEquals(new BigDecimal("5"), result.qty());
        }

        @Test
        void shouldSaveNegativeAdjustmentWhenStockSufficient() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("20")));
            when(movementRepository.save(any())).thenAnswer(inv -> {
                StockMovement m = inv.getArgument(0);
                return StockMovement.builder().id(UUID.randomUUID())
                        .locationId(m.getLocationId()).productId(m.getProductId())
                        .qty(m.getQty()).type(m.getType()).notes(m.getNotes())
                        .createdBy(m.getCreatedBy()).build();
            });

            InventoryLedgerService.MovementResult result = service.adjust(
                    LOCATION_ID, PRODUCT_ID, new BigDecimal("-5"), "damaged units", ACTOR);

            assertEquals(new BigDecimal("-5"), result.qty());
            assertEquals(MovementType.ADJUST, result.type());
        }

        @Test
        void shouldRejectZeroAdjustment() {
            assertThrows(InventoryLedgerException.class,
                    () -> service.adjust(LOCATION_ID, PRODUCT_ID, BigDecimal.ZERO, "reason", ACTOR));
        }

        @Test
        void shouldRejectNegativeAdjustmentWithInsufficientStock() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("3")));
            InventoryLedgerException ex = assertThrows(InventoryLedgerException.class,
                    () -> service.adjust(LOCATION_ID, PRODUCT_ID, new BigDecimal("-10"), "write-off", ACTOR));
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        void shouldRejectAdjustmentWithBlankNote() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            assertThrows(InventoryLedgerException.class,
                    () -> service.adjust(LOCATION_ID, PRODUCT_ID, new BigDecimal("5"), "  ", ACTOR));
        }

        @Test
        void shouldRejectAdjustmentWithNullNote() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            assertThrows(InventoryLedgerException.class,
                    () -> service.adjust(LOCATION_ID, PRODUCT_ID, new BigDecimal("5"), null, ACTOR));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ON-HAND QUERIES
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class OnHand {

        @Test
        void getAllOnHand_shouldReturnRepositoryResult() {
            List<OnHandEntry> expected = List.of(onHandEntry(LOCATION_ID, PRODUCT_ID, "15"));
            when(movementRepository.findAllOnHand()).thenReturn(expected);

            List<OnHandEntry> result = service.getAllOnHand();

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("15"), result.get(0).getQtyOnHand());
        }

        @Test
        void getOnHandByLocation_shouldRejectUnknownLocation() {
            when(layoutBlockRepository.existsById(LOCATION_ID)).thenReturn(false);
            assertThrows(InventoryLedgerException.class,
                    () -> service.getOnHandByLocation(LOCATION_ID));
        }

        @Test
        void getOnHandByLocation_shouldReturnEntries() {
            givenLocationExists(LOCATION_ID);
            when(movementRepository.findOnHandByLocation(LOCATION_ID))
                    .thenReturn(List.of(onHandEntry(LOCATION_ID, PRODUCT_ID, "7")));

            List<OnHandEntry> result = service.getOnHandByLocation(LOCATION_ID);

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("7"), result.get(0).getQtyOnHand());
        }

        @Test
        void getOnHandByProduct_shouldRejectUnknownProduct() {
            when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);
            assertThrows(InventoryLedgerException.class,
                    () -> service.getOnHandByProduct(PRODUCT_ID));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // IMMUTABILITY GUARD — repository must never be asked to delete/update
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class ImmutabilityGuard {

        @Test
        void receive_shouldNeverCallDelete() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.save(any())).thenReturn(savedMovement(UUID.randomUUID()));

            service.receive(LOCATION_ID, PRODUCT_ID, BigDecimal.ONE, null, null, null, ACTOR);

            verify(movementRepository, never()).delete(any());
            verify(movementRepository, never()).deleteAll();
            verify(movementRepository, never()).deleteById(any());
        }

        @Test
        void transfer_shouldNeverCallDelete() {
            givenLocationExists(LOCATION_ID);
            givenLocationExists(LOCATION_B_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.findOnHandQtyByLocationAndProduct(LOCATION_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(new BigDecimal("50")));
            when(movementRepository.save(any())).thenReturn(savedMovement(UUID.randomUUID()));

            service.transfer(LOCATION_ID, LOCATION_B_ID, PRODUCT_ID, BigDecimal.ONE, null, null, ACTOR);

            verify(movementRepository, never()).delete(any());
            verify(movementRepository, never()).deleteAll();
        }

        @Test
        void adjust_shouldNeverCallDelete() {
            givenLocationExists(LOCATION_ID);
            givenProductExists(PRODUCT_ID);
            when(movementRepository.save(any())).thenReturn(savedMovement(UUID.randomUUID()));

            service.adjust(LOCATION_ID, PRODUCT_ID, BigDecimal.ONE, "reason", ACTOR);

            verify(movementRepository, never()).delete(any());
            verify(movementRepository, never()).deleteAll();
        }
    }
}
