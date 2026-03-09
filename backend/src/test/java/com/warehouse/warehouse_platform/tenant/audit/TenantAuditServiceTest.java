package com.warehouse.warehouse_platform.tenant.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private TenantAuditContextProvider tenantAuditContextProvider;

    private TenantAuditService service;

    @BeforeEach
    void setUp() {
        service = new TenantAuditService(auditLogRepository, tenantAuditContextProvider, new ObjectMapper());
    }

    @Test
    void record_shouldPersistSerializedSnapshots() {
        when(tenantAuditContextProvider.currentContext()).thenReturn(new TenantAuditContextProvider.AuditContext(
                "admin@acme.local",
                List.of("ROLE_ADMIN"),
                "acme",
                "/acme/products",
                "POST"));

        service.record(
                "PRODUCT_CREATE",
                "PRODUCT",
                "11111111-1111-1111-1111-111111111111",
                null,
                new Snapshot("SKU-1", "Product 1"));

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());

        AuditLog saved = logCaptor.getValue();
        assertEquals("admin@acme.local", saved.getActorEmail());
        assertEquals("PRODUCT_CREATE", saved.getAction());
        assertEquals("PRODUCT", saved.getEntityType());
        assertEquals("acme", saved.getTenantId());
        assertTrue(saved.getActorRoles().contains("ROLE_ADMIN"));
        assertTrue(saved.getAfterState().contains("SKU-1"));
    }

    @Test
    void listAuditLogs_shouldMapPagedResult() {
        AuditLog log = AuditLog.builder()
                .id(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
                .actorEmail("admin@acme.local")
                .actorRoles("[\"ROLE_ADMIN\"]")
                .action("PRODUCT_UPDATE")
                .entityType("PRODUCT")
                .entityId("pid-1")
                .beforeState("{\"name\":\"old\"}")
                .afterState("{\"name\":\"new\"}")
                .tenantId("acme")
                .requestPath("/acme/products/pid-1")
                .requestMethod("PUT")
                .build();

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        TenantAuditService.AuditPageResult result = service.listAuditLogs(
                0,
                20,
                "admin",
                "PRODUCT_UPDATE",
                "PRODUCT",
                "pid-1",
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-09"));

        assertEquals(1, result.totalElements());
        assertEquals("PRODUCT_UPDATE", result.content().getFirst().action());
        assertEquals("admin@acme.local", result.content().getFirst().actorEmail());
        verify(auditLogRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    private record Snapshot(String sku, String name) {
    }
}
