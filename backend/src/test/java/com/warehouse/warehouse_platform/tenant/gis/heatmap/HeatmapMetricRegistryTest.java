package com.warehouse.warehouse_platform.tenant.gis.heatmap;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatmapMetricRegistryTest {

    @Test
    void registry_shouldListAllRegisteredStrategies() {
        HeatmapMetricStrategy s1 = stubStrategy("metric_a", "A", "Metric A", null);
        HeatmapMetricStrategy s2 = stubStrategy("metric_b", "B", "Metric B", "kg");
        HeatmapMetricRegistry registry = new HeatmapMetricRegistry(List.of(s1, s2));

        Collection<HeatmapMetricStrategy> all = registry.list();
        assertEquals(2, all.size());
    }

    @Test
    void registry_shouldResolveStrategyByKey() {
        HeatmapMetricStrategy strategy = stubStrategy("quantity_sum", "Total Stock", "Qty sum", null);
        HeatmapMetricRegistry registry = new HeatmapMetricRegistry(List.of(strategy));

        Optional<HeatmapMetricStrategy> found = registry.get("quantity_sum");
        assertTrue(found.isPresent());
        assertEquals("quantity_sum", found.get().key());
    }

    @Test
    void registry_shouldReturnEmpty_forUnknownKey() {
        HeatmapMetricRegistry registry = new HeatmapMetricRegistry(List.of());

        Optional<HeatmapMetricStrategy> found = registry.get("unknown_metric");
        assertFalse(found.isPresent());
    }

    @Test
    void quantitySumStrategy_shouldBeRegistered() {
        HeatmapMetricRegistry registry = new HeatmapMetricRegistry(
                List.of(new QuantitySumStrategy(null)));

        Optional<HeatmapMetricStrategy> found = registry.get(QuantitySumStrategy.KEY);
        assertTrue(found.isPresent());
    }

    @Test
    void quantitySumStrategy_unitShouldBeNull() {
        QuantitySumStrategy strategy = new QuantitySumStrategy(null);
        assertNull(strategy.unit(), "unit() must return null for quantity_sum (no unit applicable)");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private HeatmapMetricStrategy stubStrategy(String key, String label, String desc, String unit) {
        return new HeatmapMetricStrategy() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String description() {
                return desc;
            }

            @Override
            public String unit() {
                return unit;
            }

            @Override
            public java.util.Map<String, Object> execute() {
                return java.util.Map.of();
            }
        };
    }
}
