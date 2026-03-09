package com.warehouse.warehouse_platform.tenant.uom;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUomManagementServiceTest {

    @Mock
    private UnitOfMeasureRepository unitOfMeasureRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantUomManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantUomManagementService(unitOfMeasureRepository, productRepository, tenantAuditService);
    }

    @Test
    void createUom_shouldNormalizeCodeAndAudit() {
        when(unitOfMeasureRepository.findByCodeIgnoreCase("KG")).thenReturn(Optional.empty());
        when(unitOfMeasureRepository.save(any(UnitOfMeasure.class))).thenAnswer(invocation -> {
            UnitOfMeasure saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            saved.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return saved;
        });

        TenantUomManagementService.UomResult result = service.createUom(" kg ", "Kilogram", "kg");

        assertEquals("KG", result.code());
        assertEquals("Kilogram", result.name());
        verify(tenantAuditService).record(eq("UOM_CREATE"), eq("UOM"), eq(result.id().toString()), eq(null), any());

        ArgumentCaptor<UnitOfMeasure> captor = ArgumentCaptor.forClass(UnitOfMeasure.class);
        verify(unitOfMeasureRepository).save(captor.capture());
        assertEquals("KG", captor.getValue().getCode());
    }

    @Test
    void hardDeleteUom_shouldRejectWhenStillActive() {
        UUID uomId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(unitOfMeasureRepository.findById(uomId)).thenReturn(Optional.of(uom(uomId, true)));

        TenantUomManagementException ex = assertThrows(
                TenantUomManagementException.class,
                () -> service.hardDeleteUom(uomId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteUom_shouldRejectWhenReferencedByProducts() {
        UUID uomId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(unitOfMeasureRepository.findById(uomId)).thenReturn(Optional.of(uom(uomId, false)));
        when(productRepository.countByBaseUom_Id(uomId)).thenReturn(1L);

        TenantUomManagementException ex = assertThrows(
                TenantUomManagementException.class,
                () -> service.hardDeleteUom(uomId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteUom_shouldDeleteWhenInactiveAndUnreferenced() {
        UUID uomId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(unitOfMeasureRepository.findById(uomId)).thenReturn(Optional.of(uom(uomId, false)));
        when(productRepository.countByBaseUom_Id(uomId)).thenReturn(0L);

        service.hardDeleteUom(uomId);

        verify(unitOfMeasureRepository).delete(any(UnitOfMeasure.class));
        verify(tenantAuditService).record(eq("UOM_HARD_DELETE"), eq("UOM"), eq(uomId.toString()), any(), eq(null));
    }

    private UnitOfMeasure uom(UUID id, boolean active) {
        return UnitOfMeasure.builder()
                .id(id)
                .code("KG")
                .name("Kilogram")
                .symbol("kg")
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
