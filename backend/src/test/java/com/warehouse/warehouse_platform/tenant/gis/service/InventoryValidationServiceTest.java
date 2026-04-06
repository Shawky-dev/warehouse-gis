package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.gis.StorageRuleViolationException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryValidationServiceTest {

    @Mock
    GisBlockRepository gisBlockRepository;
    @Mock
    GisZoneValidationService gisZoneValidationService;
    @Mock
    GeometryService geometryService;

    InventoryValidationService service;

    static final UUID LOCATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID HAZARD_TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ZONE_TYPE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID BUFFER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        service = new InventoryValidationService(gisBlockRepository, gisZoneValidationService, geometryService);
    }

    // ── no block → skip all checks ────────────────────────────────────────────

    @Test
    void assertLocationAllowsProduct_shouldSkipAll_whenNoGisBlockExists() {
        when(gisBlockRepository.findByLayoutBlockId(LOCATION_ID)).thenReturn(Optional.empty());

        Product product = productWithHazardType("FLAMMABLE");
        assertDoesNotThrow(() -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        verify(geometryService, never()).findMatchingHazardBuffers(any(), any());
        verify(gisZoneValidationService, never()).assertLocationAllowsProduct(any(), any(), any(boolean.class));
    }

    // ── hazard-buffer check ───────────────────────────────────────────────────

    @Test
    void assertLocationAllowsProduct_shouldSkipHazardBufferCheck_forNoneHazardType() {
        givenGisBlock();
        Product product = productWithHazardType("NONE");

        assertDoesNotThrow(() -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        verify(geometryService, never()).findMatchingHazardBuffers(any(), any());
    }

    @Test
    void assertLocationAllowsProduct_shouldThrowHazardBufferViolation_whenBufferMatches() {
        givenGisBlock();
        HazardType flammable = hazardType(HAZARD_TYPE_ID, "FLAMMABLE");
        Product product = productWith(flammable, null, null);

        GisHazardBuffer buffer = GisHazardBuffer.builder()
                .id(BUFFER_ID)
                .name("Chemical Exclusion Zone")
                .source("IMPORT")
                .restrictedHazardTypes(List.of(flammable))
                .build();

        when(geometryService.findMatchingHazardBuffers(LOCATION_ID, HAZARD_TYPE_ID))
                .thenReturn(List.of(buffer));

        StorageRuleViolationException ex = assertThrows(
                StorageRuleViolationException.class,
                () -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        assertEquals(StorageRuleViolationException.RuleType.HAZARD_BUFFER, ex.getRuleType());
        assertEquals("BLOCK", ex.getViolationAction());
        assertEquals("HAZARD_BUFFER_VIOLATION", ex.getCode());
    }

    // ── required-zone check ───────────────────────────────────────────────────

    @Test
    void assertLocationAllowsProduct_shouldSkipRequiredZone_whenCategoryHasNoRequiredZoneType() {
        givenGisBlock();
        HazardType none = hazardType(null, "NONE");
        ProductCategory category = category(CATEGORY_ID, null);
        Product product = productWith(none, category, null);

        assertDoesNotThrow(() -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        verify(geometryService, never()).isLocationWithinZoneType(any(), any());
    }

    @Test
    void assertLocationAllowsProduct_shouldThrowRequiredZoneViolation_whenLocationNotInType() {
        givenGisBlock();
        HazardType none = hazardType(null, "NONE");
        ZoneType refrigerated = zoneType(ZONE_TYPE_ID, "REFRIGERATED");
        ProductCategory category = category(CATEGORY_ID, refrigerated);
        Product product = productWith(none, category, null);

        when(geometryService.isLocationWithinZoneType(LOCATION_ID, ZONE_TYPE_ID)).thenReturn(false);
        when(geometryService.findZonesByZoneType(ZONE_TYPE_ID)).thenReturn(List.of());

        StorageRuleViolationException ex = assertThrows(
                StorageRuleViolationException.class,
                () -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        assertEquals(StorageRuleViolationException.RuleType.REQUIRED_ZONE, ex.getRuleType());
        assertEquals("WARN", ex.getViolationAction());
        assertEquals("REQUIRED_ZONE_VIOLATION", ex.getCode());
    }

    @Test
    void assertLocationAllowsProduct_shouldPass_whenLocationIsInRequiredZoneType() {
        givenGisBlock();
        HazardType none = hazardType(null, "NONE");
        ZoneType refrigerated = zoneType(ZONE_TYPE_ID, "REFRIGERATED");
        ProductCategory category = category(CATEGORY_ID, refrigerated);
        Product product = productWith(none, category, null);

        when(geometryService.isLocationWithinZoneType(LOCATION_ID, ZONE_TYPE_ID)).thenReturn(true);

        assertDoesNotThrow(() -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));
    }

    @Test
    void assertLocationAllowsProduct_shouldIncludeSuggestedZones_inRequiredZoneViolation() {
        givenGisBlock();
        HazardType none = hazardType(null, "NONE");
        ZoneType refrigerated = zoneType(ZONE_TYPE_ID, "REFRIGERATED");
        ProductCategory category = category(CATEGORY_ID, refrigerated);
        Product product = productWith(none, category, null);

        GisZone suggested = zone("Cold Storage A");
        when(geometryService.isLocationWithinZoneType(LOCATION_ID, ZONE_TYPE_ID)).thenReturn(false);
        when(geometryService.findZonesByZoneType(ZONE_TYPE_ID)).thenReturn(List.of(suggested));

        StorageRuleViolationException ex = assertThrows(
                StorageRuleViolationException.class,
                () -> service.assertLocationAllowsProduct(LOCATION_ID, product, false));

        assertEquals(1, ex.getSuggestedZones().size());
        assertEquals("Cold Storage A", ex.getSuggestedZones().get(0).name());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void givenGisBlock() {
        com.warehouse.warehouse_platform.tenant.gis.model.GisBlock block = new com.warehouse.warehouse_platform.tenant.gis.model.GisBlock();
        when(gisBlockRepository.findByLayoutBlockId(LOCATION_ID)).thenReturn(Optional.of(block));
    }

    private HazardType hazardType(UUID id, String code) {
        HazardType ht = new HazardType();
        ht.setId(id);
        ht.setCode(code);
        ht.setDisplayName(code);
        ht.setIsActive(true);
        return ht;
    }

    private ZoneType zoneType(UUID id, String code) {
        ZoneType zt = new ZoneType();
        zt.setId(id);
        zt.setCode(code);
        zt.setDisplayName(code);
        zt.setIsActive(true);
        return zt;
    }

    private ProductCategory category(UUID id, ZoneType requiredZoneType) {
        ProductCategory c = new ProductCategory();
        c.setId(id);
        c.setName("Test Category");
        c.setCode("TEST");
        c.setRequiredZoneType(requiredZoneType);
        return c;
    }

    private Product productWithHazardType(String hazardCode) {
        HazardType ht = hazardType(HAZARD_TYPE_ID, hazardCode);
        return productWith(ht, null, null);
    }

    private Product productWith(HazardType hazardType, ProductCategory category, UUID id) {
        Product p = new Product();
        p.setId(id != null ? id : HAZARD_TYPE_ID);
        p.setHazardType(hazardType);
        p.setCategory(category);
        return p;
    }

    private GisZone zone(String name) {
        GisZone z = new GisZone();
        z.setId(UUID.randomUUID());
        z.setName(name);
        z.setViolationAction("WARN");
        return z;
    }
}
