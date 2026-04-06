package com.warehouse.warehouse_platform.tenant.hazardtype;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HazardTypeServiceTest {

    @Mock
    HazardTypeRepository hazardTypeRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    TenantAuditService tenantAuditService;

    HazardTypeService service;

    static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        service = new HazardTypeService(hazardTypeRepository, productRepository, tenantAuditService);
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_shouldReturnSortedByCode() {
        HazardType flammable = hazardType(UUID.randomUUID(), "FLAMMABLE", "Flammable", true);
        HazardType chemical = hazardType(UUID.randomUUID(), "CHEMICAL", "Chemical", true);
        when(hazardTypeRepository.findAll()).thenReturn(List.of(flammable, chemical));

        List<HazardTypeService.HazardTypeResult> results = service.listAll();

        assertEquals(2, results.size());
        assertEquals("CHEMICAL", results.get(0).code());
        assertEquals("FLAMMABLE", results.get(1).code());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_shouldNormalizeCodeAndPersist() {
        when(hazardTypeRepository.existsByCodeIgnoreCase("EXPLOSIVE")).thenReturn(false);
        when(hazardTypeRepository.save(any())).thenAnswer(inv -> {
            HazardType ht = inv.getArgument(0);
            ht.setId(ID);
            return ht;
        });

        HazardTypeService.HazardTypeResult result = service.create("explosive", "Explosive Materials");

        assertEquals("EXPLOSIVE", result.code());
        assertEquals("Explosive Materials", result.displayName());
        assertTrue(result.active());
        verify(hazardTypeRepository).save(any());
        verify(tenantAuditService).record(eq("HAZARD_TYPE_CREATE"), eq("HAZARD_TYPE"), any(), any(), any());
    }

    @Test
    void create_shouldNormalizeCodeWithSpecialChars() {
        when(hazardTypeRepository.existsByCodeIgnoreCase("HIGH_VOLTAGE_")).thenReturn(false);
        when(hazardTypeRepository.save(any())).thenAnswer(inv -> {
            HazardType ht = inv.getArgument(0);
            ht.setId(ID);
            return ht;
        });

        HazardTypeService.HazardTypeResult result = service.create("high-voltage!", "High Voltage");

        assertEquals("HIGH_VOLTAGE_", result.code());
    }

    @Test
    void create_shouldThrowConflict_whenCodeAlreadyExists() {
        when(hazardTypeRepository.existsByCodeIgnoreCase("EXPLOSIVE")).thenReturn(true);

        HazardTypeException ex = assertThrows(
                HazardTypeException.class,
                () -> service.create("explosive", "Explosive Materials"));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void create_shouldRejectBlankCode() {
        HazardTypeException ex = assertThrows(
                HazardTypeException.class,
                () -> service.create("  ", "Some Name"));

        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void create_shouldRejectBlankDisplayName() {
        HazardTypeException ex = assertThrows(
                HazardTypeException.class,
                () -> service.create("EXPLOSIVE", ""));

        assertEquals("BAD_REQUEST", ex.getCode());
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    void deactivate_shouldSetInactive() {
        HazardType entity = hazardType(ID, "FLAMMABLE", "Flammable", true);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(hazardTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(ID);

        assertFalse(entity.getIsActive());
        assertNotNull(entity.getDeactivatedAt());
    }

    @Test
    void deactivate_shouldRejectNoneHazardType() {
        HazardType none = hazardType(ID, "NONE", "None", true);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(none));

        HazardTypeException ex = assertThrows(HazardTypeException.class, () -> service.deactivate(ID));

        assertEquals("FORBIDDEN_OPERATION", ex.getCode());
    }

    @Test
    void deactivate_shouldBeIdempotent_whenAlreadyInactive() {
        HazardType entity = hazardType(ID, "FLAMMABLE", "Flammable", false);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(entity));

        service.deactivate(ID);

        verify(hazardTypeRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_shouldRejectWhenReferencedByProducts() {
        HazardType entity = hazardType(ID, "FLAMMABLE", "Flammable", false);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(productRepository.countByHazardType_Id(ID)).thenReturn(2L);

        HazardTypeException ex = assertThrows(HazardTypeException.class, () -> service.delete(ID));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void delete_shouldRejectNoneHazardType() {
        HazardType none = hazardType(ID, "NONE", "None", true);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(none));

        HazardTypeException ex = assertThrows(HazardTypeException.class, () -> service.delete(ID));

        assertEquals("FORBIDDEN_OPERATION", ex.getCode());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_shouldRejectRenamingNoneHazardType() {
        HazardType none = hazardType(ID, "NONE", "None", true);
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.of(none));
        when(hazardTypeRepository.existsByIdNotAndCodeIgnoreCase(ID, "NEWCODE")).thenReturn(false);

        HazardTypeException ex = assertThrows(HazardTypeException.class,
                () -> service.update(ID, "NEWCODE", "New Name"));

        assertEquals("FORBIDDEN_OPERATION", ex.getCode());
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_shouldThrowNotFound_whenMissing() {
        when(hazardTypeRepository.findById(ID)).thenReturn(Optional.empty());

        HazardTypeException ex = assertThrows(HazardTypeException.class, () -> service.get(ID));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HazardType hazardType(UUID id, String code, String displayName, boolean active) {
        HazardType ht = new HazardType();
        ht.setId(id);
        ht.setCode(code);
        ht.setDisplayName(displayName);
        ht.setIsActive(active);
        return ht;
    }
}
