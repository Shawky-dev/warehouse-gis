package com.warehouse.warehouse_platform.tenant.supplier;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSupplierManagementServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductSupplierRepository productSupplierRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantSupplierManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantSupplierManagementService(supplierRepository, productSupplierRepository, tenantAuditService);
    }

    @Test
    void createSupplier_shouldNormalizeCodeAndAudit() {
        when(supplierRepository.findByCodeIgnoreCase("SUP-1")).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            supplier.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
            supplier.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            supplier.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return supplier;
        });

        TenantSupplierManagementService.SupplierResult result = service.createSupplier(
                " sup-1 ",
                "Supplier One",
                null,
                null,
                null,
                null);

        assertEquals("SUP-1", result.code());
        verify(tenantAuditService).record(eq("SUPPLIER_CREATE"), eq("SUPPLIER"), eq(result.id().toString()), eq(null), any());
    }

    @Test
    void hardDeleteSupplier_shouldRejectWhenActive() {
        UUID supplierId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId, true)));

        TenantSupplierManagementException ex = assertThrows(
                TenantSupplierManagementException.class,
                () -> service.hardDeleteSupplier(supplierId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteSupplier_shouldRejectWhenLinkedToProducts() {
        UUID supplierId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId, false)));
        when(productSupplierRepository.countBySupplier_Id(supplierId)).thenReturn(2L);

        TenantSupplierManagementException ex = assertThrows(
                TenantSupplierManagementException.class,
                () -> service.hardDeleteSupplier(supplierId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteSupplier_shouldDeleteWhenInactiveAndUnreferenced() {
        UUID supplierId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId, false)));
        when(productSupplierRepository.countBySupplier_Id(supplierId)).thenReturn(0L);

        service.hardDeleteSupplier(supplierId);

        verify(supplierRepository).delete(any(Supplier.class));
        verify(tenantAuditService).record(eq("SUPPLIER_HARD_DELETE"), eq("SUPPLIER"), eq(supplierId.toString()), any(), eq(null));
    }

    private Supplier supplier(UUID id, boolean active) {
        return Supplier.builder()
                .id(id)
                .code("SUP-1")
                .name("Supplier One")
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
