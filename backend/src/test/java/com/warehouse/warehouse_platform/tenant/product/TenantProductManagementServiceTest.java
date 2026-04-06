package com.warehouse.warehouse_platform.tenant.product;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardTypeRepository;
import com.warehouse.warehouse_platform.tenant.supplier.Supplier;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasure;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProductManagementServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UnitOfMeasureRepository unitOfMeasureRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private HazardTypeRepository hazardTypeRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductSupplierRepository productSupplierRepository;

    @Mock
    private TenantAuditService tenantAuditService;

    private TenantProductManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantProductManagementService(
                productRepository,
                unitOfMeasureRepository,
                productCategoryRepository,
                hazardTypeRepository,
                supplierRepository,
                productSupplierRepository,
                tenantAuditService);
    }

    @Test
    void createProduct_shouldNormalizeSkuAndPersistSupplierMappings() {
        UUID uomId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID supplierA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID supplierB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID categoryId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID hazardTypeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        UnitOfMeasure uom = UnitOfMeasure.builder().id(uomId).code("PCS").name("Pieces").build();
        Supplier firstSupplier = Supplier.builder().id(supplierA).code("SUP-A").name("Supplier A").build();
        Supplier secondSupplier = Supplier.builder().id(supplierB).code("SUP-B").name("Supplier B").build();
        ProductCategory category = ProductCategory.builder().id(categoryId).name("General").active(true).build();
        HazardType hazardType = HazardType.builder().id(hazardTypeId).code("NONE").displayName("None").isActive(true)
                .build();

        when(productRepository.findBySkuIgnoreCase("SKU-1")).thenReturn(Optional.empty());
        when(unitOfMeasureRepository.findById(uomId)).thenReturn(Optional.of(uom));
        when(productCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(hazardTypeRepository.findById(hazardTypeId)).thenReturn(Optional.of(hazardType));
        when(supplierRepository.findAllById(Set.of(supplierA, supplierB)))
                .thenReturn(List.of(firstSupplier, secondSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
            product.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            product.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return product;
        });
        when(productSupplierRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TenantProductManagementService.ProductResult result = service.createProduct(
                " sku-1 ",
                "Sample Product",
                "Desc",
                uomId,
                categoryId,
                hazardTypeId,
                true,
                false,
                Set.of(supplierA, supplierB),
                supplierB);

        assertEquals("SKU-1", result.sku());
        assertEquals(2, result.suppliers().size());
        assertEquals(1, result.suppliers().stream()
                .filter(TenantProductManagementService.ProductSupplierResult::primary).count());

        verify(productSupplierRepository).deleteByProduct_Id(result.id());
        verify(tenantAuditService).record(eq("PRODUCT_CREATE"), eq("PRODUCT"), eq(result.id().toString()), eq(null),
                any());
    }

    @Test
    void updateProduct_shouldRejectPrimarySupplierOutsideSupplierIds() {
        UUID productId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID uomId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID supplierA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID supplierB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID categoryId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID hazardTypeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        Product product = product(productId, uomId, true);
        ProductCategory category = ProductCategory.builder().id(categoryId).name("General").active(true).build();
        HazardType hazardType = HazardType.builder().id(hazardTypeId).code("NONE").displayName("None").isActive(true)
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productSupplierRepository.findAllByProduct_Id(productId)).thenReturn(List.of());
        when(productRepository.findBySkuIgnoreCase("SKU-1")).thenReturn(Optional.of(product));
        when(unitOfMeasureRepository.findById(uomId))
                .thenReturn(Optional.of(UnitOfMeasure.builder().id(uomId).code("PCS").name("Pieces").build()));
        when(productCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(hazardTypeRepository.findById(hazardTypeId)).thenReturn(Optional.of(hazardType));

        TenantProductManagementException ex = assertThrows(
                TenantProductManagementException.class,
                () -> service.updateProduct(
                        productId,
                        "SKU-1",
                        "Updated",
                        null,
                        uomId,
                        categoryId,
                        hazardTypeId,
                        false,
                        false,
                        Set.of(supplierA),
                        supplierB));

        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void hardDeleteProduct_shouldRejectWhenActive() {
        UUID productId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product(productId, UUID.randomUUID(), true)));

        TenantProductManagementException ex = assertThrows(
                TenantProductManagementException.class,
                () -> service.hardDeleteProduct(productId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void hardDeleteProduct_shouldRejectWhenSupplierMappingsExist() {
        UUID productId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product(productId, UUID.randomUUID(), false)));
        when(productSupplierRepository.countByProduct_Id(productId)).thenReturn(1L);

        TenantProductManagementException ex = assertThrows(
                TenantProductManagementException.class,
                () -> service.hardDeleteProduct(productId));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void hardDeleteProduct_shouldDeleteWhenInactiveAndUnreferenced() {
        UUID productId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product(productId, UUID.randomUUID(), false)));
        when(productSupplierRepository.countByProduct_Id(productId)).thenReturn(0L);

        service.hardDeleteProduct(productId);

        verify(productRepository).delete(any(Product.class));
        verify(tenantAuditService).record(eq("PRODUCT_HARD_DELETE"), eq("PRODUCT"), eq(productId.toString()), any(),
                eq(null));
    }

    private Product product(UUID productId, UUID uomId, boolean active) {
        UnitOfMeasure uom = UnitOfMeasure.builder()
                .id(uomId)
                .code("PCS")
                .name("Pieces")
                .build();

        return Product.builder()
                .id(productId)
                .sku("SKU-1")
                .name("Product")
                .baseUom(uom)
                .trackLot(false)
                .trackExpiry(false)
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
