package com.warehouse.warehouse_platform.tenant.gis.heatmap;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry holding all {@link HeatmapMetricStrategy} beans available in the application context.
 * New metrics are registered automatically by adding a Spring bean that implements the interface —
 * no changes to controllers, routes, or permissions are required.
 */
@Component
public class HeatmapMetricRegistry {

    private final Map<String, HeatmapMetricStrategy> strategies;

    public HeatmapMetricRegistry(List<HeatmapMetricStrategy> all) {
        this.strategies = new LinkedHashMap<>();
        all.forEach(s -> strategies.put(s.key(), s));
    }

    /**
     * Looks up a strategy by its key.
     *
     * @param key the metric key as sent in API requests
     * @return the matching strategy, or empty if the key is unknown
     */
    public Optional<HeatmapMetricStrategy> get(String key) {
        return Optional.ofNullable(strategies.get(key));
    }

    /**
     * Returns all registered strategies in registration order.
     */
    public Collection<HeatmapMetricStrategy> list() {
        return strategies.values();
    }
}
