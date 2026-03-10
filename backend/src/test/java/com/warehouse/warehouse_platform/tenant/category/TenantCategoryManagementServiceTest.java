package com.warehouse.warehouse_platform.tenant.category;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCategoryManagementServiceTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantCategoryManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantCategoryManagementService(productCategoryRepository, productRepository, tenantAuditService);
    }

    @Test
    void createCategory_shouldNormalizeNameAndAudit() {
        when(productCategoryRepository.findByNameIgnoreCase("Perishables")).thenReturn(Optional.empty());
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
            ProductCategory saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            saved.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return saved;
        });

        TenantCategoryManagementService.CategoryResult result =
                service.createCategory("  Perishables  ", "Food items with expiry");

        assertEquals("Perishables", result.name());
        assertEquals("Food items with expiry", result.description());
        verify(tenantAuditService).record(eq("CATEGORY_CREATE"), eq("CATEGORY"), eq(result.id().toString()), eq(null), any());

        ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepository).save(captor.capture());
        assertEquals("Perishables", captor.getValue().getName());
    }

    @Test
    void createCategory_shouldRejectDuplicateName() {
        when(productCategoryRepository.findByNameIgnoreCase("Metals"))
                .thenReturn(Optional.of(category(UUID.randomUUID(), "Metals", true)));

        TenantCategoryManagementException ex = assertThrows(
                TenantCategoryManagementException.class,
                () -> service.createCategory("Metals", null));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteCategory_shouldRejectWhenStillActive() {
        UUID categoryId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(productCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId, "Metals", true)));

        TenantCategoryManagementException ex = assertThrows(
                TenantCategoryManagementException.class,
                () -> service.hardDeleteCategory(categoryId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteCategory_shouldRejectWhenReferencedByProducts() {
        UUID categoryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(productCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId, "Metals", false)));
        when(productRepository.countByCategory_Id(categoryId)).thenReturn(2L);

        TenantCategoryManagementException ex = assertThrows(
                TenantCategoryManagementException.class,
                () -> service.hardDeleteCategory(categoryId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteCategory_shouldDeleteWhenInactiveAndUnreferenced() {
        UUID categoryId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(productCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId, "Metals", false)));
        when(productRepository.countByCategory_Id(categoryId)).thenReturn(0L);

        service.hardDeleteCategory(categoryId);

        verify(productCategoryRepository).delete(any(ProductCategory.class));
        verify(tenantAuditService).record(eq("CATEGORY_HARD_DELETE"), eq("CATEGORY"), eq(categoryId.toString()), any(), eq(null));
    }

    private ProductCategory category(UUID id, String name, boolean active) {
        return ProductCategory.builder()
                .id(id)
                .name(name)
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
