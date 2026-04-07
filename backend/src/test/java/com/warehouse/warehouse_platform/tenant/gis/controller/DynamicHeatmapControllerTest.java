package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.heatmap.HeatmapMetricRegistry;
import com.warehouse.warehouse_platform.tenant.gis.heatmap.HeatmapMetricStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicHeatmapControllerTest {

    @Mock
    TenantAccessPolicy tenantAccessPolicy;
    @Mock
    HeatmapMetricRegistry registry;

    DynamicHeatmapController controller;
    Authentication auth = mock(Authentication.class);

    static final String TENANT = "tenant1";

    @BeforeEach
    void setUp() {
        controller = new DynamicHeatmapController(tenantAccessPolicy, registry);
    }

    // ─── listMetrics ─────────────────────────────────────────────────────────

    @Test
    void listMetrics_shouldReturnAllRegisteredMetrics() {
        HeatmapMetricStrategy strategy = stubStrategy("quantity_sum", "Total Stock", "Qty sum", null);
        when(registry.list()).thenReturn(List.of(strategy));

        ResponseEntity<List<DynamicHeatmapController.MetricMetadataResponse>> response = controller.listMetrics(TENANT,
                auth);

        assertEquals(200, response.getStatusCode().value());
        List<DynamicHeatmapController.MetricMetadataResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals("quantity_sum", body.get(0).key());
    }

    @Test
    void listMetrics_unitShouldSerializeAsNull_whenAbsent() throws Exception {
        HeatmapMetricStrategy strategy = stubStrategy("quantity_sum", "Total Stock", "Qty sum", null);
        when(registry.list()).thenReturn(List.of(strategy));

        ResponseEntity<List<DynamicHeatmapController.MetricMetadataResponse>> response = controller.listMetrics(TENANT,
                auth);

        var body = response.getBody();
        assertNotNull(body);
        DynamicHeatmapController.MetricMetadataResponse item = body.get(0);
        assertNull(item.unit(), "unit must be null (not omitted) when no unit applies");

        // Verify JSON serialization includes the null field.
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"unit\":null"), "JSON must include unit: null, not omit the field");
    }

    @Test
    void listMetrics_unitShouldSerialize_whenPresent() throws Exception {
        HeatmapMetricStrategy strategy = stubStrategy("weight_sum", "Total Weight", "Weight sum", "kg");
        when(registry.list()).thenReturn(List.of(strategy));

        ResponseEntity<List<DynamicHeatmapController.MetricMetadataResponse>> response = controller.listMetrics(TENANT,
                auth);

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("kg", body.get(0).unit());
    }

    // ─── getPoints ───────────────────────────────────────────────────────────

    @Test
    void getPoints_shouldReturn400_forUnknownMetric() {
        when(registry.get("unknown")).thenReturn(Optional.empty());
        assertThrows(GisException.class, () -> controller.getPoints(TENANT, "unknown", auth));
    }

    @Test
    void getPoints_shouldReturnEmptyFeatureCollection_whenNoPoints() {
        HeatmapMetricStrategy strategy = stubStrategy("quantity_sum", "Label", "Desc", null);
        when(strategy.execute()).thenReturn(Map.of("type", "FeatureCollection", "features", List.of()));
        when(registry.get("quantity_sum")).thenReturn(Optional.of(strategy));

        ResponseEntity<Map<String, Object>> response = controller.getPoints(TENANT, "quantity_sum", auth);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("FeatureCollection", body.get("type"));
        assertTrue(((List<?>) body.get("features")).isEmpty());
    }

    @Test
    void getPoints_shouldReturnPointFeatures_fromStrategy() {
        Map<String, Object> feature = Map.of(
                "type", "Feature",
                "geometry", Map.of("type", "Point", "coordinates", List.of(10.0, 20.0)),
                "properties", Map.of("metricKey", "quantity_sum", "weight", 100.0));
        Map<String, Object> featureCollection = Map.of(
                "type", "FeatureCollection",
                "features", List.of(feature));

        HeatmapMetricStrategy strategy = stubStrategy("quantity_sum", "Label", "Desc", null);
        when(strategy.execute()).thenReturn(featureCollection);
        when(registry.get("quantity_sum")).thenReturn(Optional.of(strategy));

        ResponseEntity<Map<String, Object>> response = controller.getPoints(TENANT, "quantity_sum", auth);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, ((List<?>) body.get("features")).size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private HeatmapMetricStrategy stubStrategy(String key, String label, String desc, String unit) {
        HeatmapMetricStrategy s = mock(HeatmapMetricStrategy.class);
        when(s.key()).thenReturn(key);
        when(s.label()).thenReturn(label);
        when(s.description()).thenReturn(desc);
        when(s.unit()).thenReturn(unit);
        return s;
    }
}
