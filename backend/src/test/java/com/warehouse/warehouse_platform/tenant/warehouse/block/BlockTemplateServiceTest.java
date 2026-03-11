package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockTemplateServiceTest {

    @Mock
    private BlockTemplateRepository templateRepository;
    @Mock
    private LayoutBlockRepository layoutBlockRepository;
    @Mock
    private TenantAuditService tenantAuditService;

    private BlockTemplateService service;

    @BeforeEach
    void setUp() {
        service = new BlockTemplateService(templateRepository, layoutBlockRepository, tenantAuditService);
    }

    // -------------------------------------------------------------------------
    // createTemplate
    // -------------------------------------------------------------------------

    @Test
    void createTemplate_shouldSaveAndAudit() {
        when(templateRepository.findByNameIgnoreCase("Aisle")).thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(inv -> {
            BlockTemplate t = inv.getArgument(0);
            t.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            t.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            t.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return t;
        });

        BlockTemplateService.TemplateResult result = service.createTemplate(
                "Aisle", BlockTemplate.IdentifierFormat.ALPHA, BlockTemplate.SideConfig.LR,
                null, true, "Aisle template", "AlignJustify");

        assertEquals("Aisle", result.name());
        assertEquals(BlockTemplate.IdentifierFormat.ALPHA, result.identifierFormat());
        assertEquals(BlockTemplate.SideConfig.LR, result.sideConfig());
        assertEquals("AlignJustify", result.iconName());
        assertNull(result.sideOptions());
        verify(tenantAuditService).record(eq("BLOCK_TEMPLATE_CREATE"), eq("BLOCK_TEMPLATE"),
                eq(result.id().toString()), eq(null), any());
    }

    @Test
    void createTemplate_shouldRejectDuplicateName() {
        when(templateRepository.findByNameIgnoreCase("Bay"))
                .thenReturn(Optional.of(template(UUID.randomUUID(), "Bay")));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createTemplate("Bay", BlockTemplate.IdentifierFormat.NUMERIC,
                        BlockTemplate.SideConfig.NONE, null, true, null, null));
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void createTemplate_shouldRejectBlankName() {
        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createTemplate("  ", BlockTemplate.IdentifierFormat.NUMERIC,
                        BlockTemplate.SideConfig.NONE, null, true, null, null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void createTemplate_shouldRequireSideOptionsWhenCustom() {
        when(templateRepository.findByNameIgnoreCase("Zone")).thenReturn(Optional.empty());

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createTemplate("Zone", BlockTemplate.IdentifierFormat.ALPHA,
                        BlockTemplate.SideConfig.CUSTOM, null, true, null, null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void createTemplate_shouldRequireAtLeastTwoDistinctSideOptions() {
        when(templateRepository.findByNameIgnoreCase("Zone")).thenReturn(Optional.empty());

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.createTemplate("Zone", BlockTemplate.IdentifierFormat.ALPHA,
                        BlockTemplate.SideConfig.CUSTOM, List.of("OnlyOne"), true, null, null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void createTemplate_shouldStoreSideOptionsAsCommaSeparated() {
        when(templateRepository.findByNameIgnoreCase("Room")).thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(inv -> {
            BlockTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        BlockTemplateService.TemplateResult result = service.createTemplate(
                "Room", BlockTemplate.IdentifierFormat.FREE_TEXT,
                BlockTemplate.SideConfig.CUSTOM, List.of("North", "South", "East", "West"),
                false, null, "MapPin");

        assertEquals(List.of("North", "South", "East", "West"), result.sideOptions());
        assertEquals("MapPin", result.iconName());
    }

    // -------------------------------------------------------------------------
    // deleteTemplate
    // -------------------------------------------------------------------------

    @Test
    void deleteTemplate_shouldRejectWhenInUse() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(templateRepository.findById(id)).thenReturn(Optional.of(template(id, "Shelf")));
        when(layoutBlockRepository.countByBlockTemplateId(id)).thenReturn(5L);

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> service.deleteTemplate(id));
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void deleteTemplate_shouldSucceedWhenNotInUse() {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        BlockTemplate t = template(id, "UnusedLevel");
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));
        when(layoutBlockRepository.countByBlockTemplateId(id)).thenReturn(0L);

        service.deleteTemplate(id);

        verify(templateRepository).delete(t);
        verify(tenantAuditService).record(eq("BLOCK_TEMPLATE_DELETE"), eq("BLOCK_TEMPLATE"),
                eq(id.toString()), any(), eq(null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BlockTemplate template(UUID id, String name) {
        return BlockTemplate.builder()
                .id(id)
                .name(name)
                .identifierFormat(BlockTemplate.IdentifierFormat.NUMERIC)
                .sideConfig(BlockTemplate.SideConfig.NONE)
                .required(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
