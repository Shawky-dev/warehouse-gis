package com.warehouse.warehouse_platform.tenant.dashboard;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/{tenantSlug}/dashboard")
@Validated
public class DashboardController {

    private static final String SPATIAL_ANY = "hasAnyAuthority("
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_VIEW"
            + ")";

    private static final String INVENTORY_OPS_ANY = "hasAnyAuthority("
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VIEW"
            + ")";

    private static final String WARNINGS_ANY = "hasAnyAuthority("
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW"
            + ")";

    private static final String MASTER_DATA_ANY = "hasAnyAuthority("
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_VIEW"
            + ")";

    private static final String STOCKTAKE_ANY = "hasAnyAuthority("
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VIEW,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_CREATE,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_POST,"
            + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).COUNTING_VOID"
            + ")";

    private final TenantAccessPolicy tenantAccessPolicy;
    private final DashboardService dashboardService;

    public DashboardController(
            TenantAccessPolicy tenantAccessPolicy,
            DashboardService dashboardService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/spatial-kpis")
    @PreAuthorize(SPATIAL_ANY)
    public ResponseEntity<DashboardService.DashboardSectionResponse> getSpatialKpis(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getSpatialKpis());
    }

    @GetMapping("/inventory-ops")
    @PreAuthorize(INVENTORY_OPS_ANY)
    public ResponseEntity<DashboardService.DashboardSectionResponse> getInventoryOps(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getInventoryOps());
    }

    @GetMapping("/warnings")
    @PreAuthorize(WARNINGS_ANY)
    public ResponseEntity<DashboardService.DashboardSectionResponse> getWarnings(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getWarnings());
    }

    @GetMapping("/master-data")
    @PreAuthorize(MASTER_DATA_ANY)
    public ResponseEntity<DashboardService.DashboardSectionResponse> getMasterData(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getMasterData());
    }

    @GetMapping("/stocktake")
    @PreAuthorize(STOCKTAKE_ANY)
    public ResponseEntity<DashboardService.DashboardSectionResponse> getStocktake(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getStocktake());
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).AUDIT_VIEW)")
    public ResponseEntity<DashboardService.DashboardSectionResponse> getActivity(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return ResponseEntity.ok(dashboardService.getActivity());
    }
}
