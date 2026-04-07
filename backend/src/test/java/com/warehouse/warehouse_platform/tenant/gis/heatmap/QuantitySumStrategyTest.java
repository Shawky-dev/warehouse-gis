package com.warehouse.warehouse_platform.tenant.gis.heatmap;

import com.warehouse.warehouse_platform.tenant.gis.repository.WeightedHeatmapPointRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.WeightedPointProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuantitySumStrategyTest {

    @Mock
    WeightedHeatmapPointRepository pointRepository;

    QuantitySumStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new QuantitySumStrategy(pointRepository);
    }

    @Test
    void key_shouldBeQuantitySum() {
        assertEquals("quantity_sum", strategy.key());
    }

    @Test
    void unit_shouldBeNull() {
        assertNull(strategy.unit(), "quantity_sum has no unit — must return null");
    }

    @Test
    void execute_shouldReturnEmptyFeatureCollection_whenNoPoints() {
        when(pointRepository.findQuantitySumPoints()).thenReturn(List.of());

        Map<String, Object> result = strategy.execute();

        assertEquals("FeatureCollection", result.get("type"));
        assertTrue(((List<?>) result.get("features")).isEmpty());
    }

    @Test
    void execute_shouldEmitOnePointFeaturePerStockedLocation() {
        UUID locationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        WeightedPointProjection point = pointProjection(locationId, "A.01.L1", "A.01.L1", 10.5, 20.3, 150.0);
        when(pointRepository.findQuantitySumPoints()).thenReturn(List.of(point));

        Map<String, Object> result = strategy.execute();

        List<?> features = (List<?>) result.get("features");
        assertEquals(1, features.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> feature = (Map<String, Object>) features.get(0);
        assertEquals("Feature", feature.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
        assertEquals("Point", geometry.get("type"));

        @SuppressWarnings("unchecked")
        List<Double> coords = (List<Double>) geometry.get("coordinates");
        assertEquals(10.5, coords.get(0));
        assertEquals(20.3, coords.get(1));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
        assertEquals(locationId, properties.get("locationId"));
        assertEquals("A.01.L1", properties.get("label"));
        assertEquals("A.01.L1", properties.get("positionPath"));
        assertEquals("quantity_sum", properties.get("metricKey"));
        assertEquals(150.0, properties.get("weight"));
        assertEquals(150.0, properties.get("rawValue"));
    }

    @Test
    void execute_shouldReturnMultipleFeatures() {
        WeightedPointProjection p1 = pointProjection(UUID.randomUUID(), "A1", "A1", 1.0, 2.0, 10.0);
        WeightedPointProjection p2 = pointProjection(UUID.randomUUID(), "B1", "B1", 3.0, 4.0, 25.0);
        when(pointRepository.findQuantitySumPoints()).thenReturn(List.of(p1, p2));

        Map<String, Object> result = strategy.execute();

        List<?> features = (List<?>) result.get("features");
        assertEquals(2, features.size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private WeightedPointProjection pointProjection(
            UUID locationId, String label, String positionPath, double lon, double lat, double weight) {
        return new WeightedPointProjection() {
            @Override
            public UUID getLocationId() {
                return locationId;
            }

            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getPositionPath() {
                return positionPath;
            }

            @Override
            public double getLon() {
                return lon;
            }

            @Override
            public double getLat() {
                return lat;
            }

            @Override
            public double getWeight() {
                return weight;
            }
        };
    }
}
