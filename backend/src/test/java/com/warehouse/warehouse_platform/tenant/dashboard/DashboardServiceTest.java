package com.warehouse.warehouse_platform.tenant.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;

    @Mock
    private DashboardRepository.SpatialSummaryProjection spatialSummary;

    @Mock
    private DashboardRepository.InventoryOpsSummaryProjection inventorySummary;

    @Mock
    private DashboardRepository.WarningSummaryProjection warningSummary;

    @Mock
    private DashboardRepository.MasterDataSummaryProjection masterDataSummary;

    @Mock
    private DashboardRepository.ActivitySummaryProjection activitySummary;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(dashboardRepository);
    }

    @Test
    void getSpatialKpis_shouldBuildOverviewWidgets() {
        DashboardRepository.TopZoneProjection topZone = projectionZone("Cold storage", new BigDecimal("42.5"));

        when(spatialSummary.getTotalStockQty()).thenReturn(new BigDecimal("120.5"));
        when(spatialSummary.getTotalStorageLocations()).thenReturn(10L);
        when(spatialSummary.getOccupiedStorageLocations()).thenReturn(4L);
        when(spatialSummary.getTotalZones()).thenReturn(3L);
        when(spatialSummary.getEmptyZones()).thenReturn(1L);
        when(dashboardRepository.fetchSpatialSummary()).thenReturn(spatialSummary);
        when(dashboardRepository.fetchTopZonesByStock()).thenReturn(List.of(topZone));

        DashboardService.DashboardSectionResponse response = dashboardService.getSpatialKpis();

        assertEquals("spatial-kpis", response.section());
        assertEquals(2, response.widgets().size());
        assertEquals("metric-grid", response.widgets().getFirst().type());
        assertEquals("40%", response.widgets().getFirst().metrics().getFirst().value());
        assertEquals("bar-list", response.widgets().get(1).type());
        assertEquals("Cold storage", response.widgets().get(1).items().getFirst().label());
    }

    @Test
    void getInventoryOps_shouldBuildTimelineAndLists() {
        DashboardRepository.TopProductProjection topProduct = projectionProduct("Widget A", new BigDecimal("19"));
        DashboardRepository.OpenCountSessionProjection openSession = projectionSession("Cycle Count 1", new BigDecimal("3"));

        when(inventorySummary.getTodayReceipts()).thenReturn(5L);
        when(inventorySummary.getTodayDispatches()).thenReturn(2L);
        when(inventorySummary.getDraftReceipts()).thenReturn(1L);
        when(inventorySummary.getDraftDispatches()).thenReturn(4L);
        when(inventorySummary.getOpenCountSessions()).thenReturn(2L);
        when(inventorySummary.getStaleCountSessions()).thenReturn(1L);
        when(dashboardRepository.fetchInventoryOpsSummary()).thenReturn(inventorySummary);
        when(dashboardRepository.fetchMovementVolume(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                projectionMovement(LocalDate.now().minusDays(1), "RECEIVE", new BigDecimal("7")),
                projectionMovement(LocalDate.now().minusDays(1), "PICK", new BigDecimal("3"))));
        when(dashboardRepository.fetchTopProductsByMovement(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(topProduct));
        when(dashboardRepository.fetchOpenCountSessions()).thenReturn(List.of(openSession));

        DashboardService.DashboardSectionResponse response = dashboardService.getInventoryOps();

        assertEquals("inventory-ops", response.section());
        assertEquals(4, response.widgets().size());
        assertEquals("timeline", response.widgets().get(1).type());
        assertFalse(response.widgets().get(1).timeline().isEmpty());
        assertEquals("Widget A", response.widgets().get(2).items().getFirst().label());
        assertEquals("Cycle Count 1", response.widgets().get(3).items().getFirst().label());
    }

    @Test
    void getWarnings_shouldReturnEmptySection() {
        DashboardService.DashboardSectionResponse response = dashboardService.getWarnings();

        assertEquals("warnings", response.section());
        assertEquals(0, response.stats().size());
        assertEquals(0, response.highlights().size());
        assertEquals(0, response.widgets().size());
    }

    @Test
    void getMasterData_shouldBuildCatalogWidgets() {
        when(masterDataSummary.getTotalProducts()).thenReturn(30L);
        when(masterDataSummary.getActiveProducts()).thenReturn(24L);
        when(masterDataSummary.getInactiveProducts()).thenReturn(6L);
        when(masterDataSummary.getProductsWithoutSuppliers()).thenReturn(3L);
        when(masterDataSummary.getTotalCategories()).thenReturn(8L);
        when(masterDataSummary.getActiveSuppliers()).thenReturn(5L);
        when(masterDataSummary.getActiveUoms()).thenReturn(7L);
        when(masterDataSummary.getUsedUoms()).thenReturn(4L);
        when(dashboardRepository.fetchMasterDataSummary()).thenReturn(masterDataSummary);
        when(dashboardRepository.fetchCategoryDistribution()).thenReturn(List.of(projectionCategory("Chemicals", 11L)));
        when(dashboardRepository.fetchProductsWithoutSuppliers()).thenReturn(List.of(projectionProductGap("Paint A", "PA-01")));
        when(dashboardRepository.fetchUnusedUoms()).thenReturn(List.of(projectionUomGap("Each", "EA")));

        DashboardService.DashboardSectionResponse response = dashboardService.getMasterData();

        assertEquals("master-data", response.section());
        assertEquals(3, response.widgets().size());
        assertEquals("metric-grid", response.widgets().getFirst().type());
        assertEquals("bar-list", response.widgets().get(1).type());
        assertEquals("Chemicals", response.widgets().get(1).items().getFirst().label());
        assertEquals("alert-list", response.widgets().get(2).type());
        assertEquals(2, response.widgets().get(2).items().size());
    }

    @Test
    void getActivity_shouldBuildAuditWidgets() {
        when(activitySummary.getEvents24h()).thenReturn(12L);
        when(activitySummary.getEvents7d()).thenReturn(40L);
        when(activitySummary.getUniqueActors7d()).thenReturn(5L);
        when(activitySummary.getEntityTypes7d()).thenReturn(7L);
        when(dashboardRepository.fetchActivitySummary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(activitySummary);
        when(dashboardRepository.fetchActionBreakdown(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                projectionAction("PRODUCT_UPDATE", 7L)));
        when(dashboardRepository.fetchTopActors(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                projectionActor("ops@example.com", 9L)));
        when(dashboardRepository.fetchRecentAuditEvents()).thenReturn(List.of(
                projectionAuditEvent("ops@example.com", "PRODUCT_UPDATE", "PRODUCT")));

        DashboardService.DashboardSectionResponse response = dashboardService.getActivity();

        assertEquals("activity", response.section());
        assertEquals(4, response.widgets().size());
        assertEquals("PRODUCT_UPDATE", response.widgets().get(1).items().getFirst().label());
        assertEquals("ops@example.com", response.widgets().get(2).items().getFirst().label());
        assertEquals("alert-list", response.widgets().get(3).type());
    }

    private DashboardRepository.TopZoneProjection projectionZone(String name, BigDecimal qty) {
        return new DashboardRepository.TopZoneProjection() {
            @Override
            public UUID getZoneId() {
                return UUID.randomUUID();
            }

            @Override
            public String getZoneName() {
                return name;
            }

            @Override
            public BigDecimal getQtyStock() {
                return qty;
            }
        };
    }

    private DashboardRepository.TopProductProjection projectionProduct(String name, BigDecimal qty) {
        return new DashboardRepository.TopProductProjection() {
            @Override
            public UUID getProductId() {
                return UUID.randomUUID();
            }

            @Override
            public String getProductName() {
                return name;
            }

            @Override
            public BigDecimal getMovementQty() {
                return qty;
            }
        };
    }

    private DashboardRepository.OpenCountSessionProjection projectionSession(String name, BigDecimal ageDays) {
        return new DashboardRepository.OpenCountSessionProjection() {
            @Override
            public UUID getSessionId() {
                return UUID.randomUUID();
            }

            @Override
            public String getSessionName() {
                return name;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-01-01T00:00:00Z");
            }

            @Override
            public BigDecimal getAgeDays() {
                return ageDays;
            }
        };
    }

    private DashboardRepository.MovementVolumeProjection projectionMovement(LocalDate day, String type, BigDecimal qty) {
        return new DashboardRepository.MovementVolumeProjection() {
            @Override
            public LocalDate getBucketDate() {
                return day;
            }

            @Override
            public String getMovementType() {
                return type;
            }

            @Override
            public BigDecimal getMovementQty() {
                return qty;
            }
        };
    }

    private DashboardRepository.CategoryDistributionProjection projectionCategory(String name, long count) {
        return new DashboardRepository.CategoryDistributionProjection() {
            @Override
            public UUID getCategoryId() {
                return UUID.randomUUID();
            }

            @Override
            public String getCategoryName() {
                return name;
            }

            @Override
            public long getProductCount() {
                return count;
            }
        };
    }

    private DashboardRepository.ProductWithoutSupplierProjection projectionProductGap(String name, String sku) {
        return new DashboardRepository.ProductWithoutSupplierProjection() {
            @Override
            public UUID getProductId() {
                return UUID.randomUUID();
            }

            @Override
            public String getProductName() {
                return name;
            }

            @Override
            public String getSku() {
                return sku;
            }
        };
    }

    private DashboardRepository.UnusedUomProjection projectionUomGap(String name, String code) {
        return new DashboardRepository.UnusedUomProjection() {
            @Override
            public UUID getUomId() {
                return UUID.randomUUID();
            }

            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    private DashboardRepository.ActionBreakdownProjection projectionAction(String action, long count) {
        return new DashboardRepository.ActionBreakdownProjection() {
            @Override
            public String getActionName() {
                return action;
            }

            @Override
            public long getEventCount() {
                return count;
            }
        };
    }

    private DashboardRepository.ActorActivityProjection projectionActor(String email, long count) {
        return new DashboardRepository.ActorActivityProjection() {
            @Override
            public String getActorEmail() {
                return email;
            }

            @Override
            public long getEventCount() {
                return count;
            }
        };
    }

    private DashboardRepository.RecentAuditEventProjection projectionAuditEvent(String actor, String action, String entityType) {
        return new DashboardRepository.RecentAuditEventProjection() {
            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public Instant getOccurredAt() {
                return Instant.parse("2026-01-02T10:15:30Z");
            }

            @Override
            public String getActorEmail() {
                return actor;
            }

            @Override
            public String getActionName() {
                return action;
            }

            @Override
            public String getEntityType() {
                return entityType;
            }

            @Override
            public String getEntityId() {
                return UUID.randomUUID().toString();
            }

            @Override
            public String getRequestMethod() {
                return "PUT";
            }

            @Override
            public String getRequestPath() {
                return "/tenant/products";
            }
        };
    }
}
