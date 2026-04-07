package com.warehouse.warehouse_platform.tenant.gis.heatmap;

import java.util.Map;

/**
 * Strategy interface for computing dynamic heatmap points from warehouse data.
 * Implement this interface and register the class as a Spring bean to add a new
 * metric.
 * The {@link HeatmapMetricRegistry} auto-registers all beans of this type at
 * startup.
 */
public interface HeatmapMetricStrategy {

    /**
     * Short machine-readable key used in API requests, e.g. {@code "quantity_sum"}.
     */
    String key();

    /** Human-readable display name for the metric. */
    String label();

    /** Longer description explaining what the metric measures. */
    String description();

    /**
     * Optional unit label (e.g. {@code "kg"}, {@code "units"}).
     * Returns {@code null} when no unit is applicable; this value is always
     * serialized
     * in responses rather than omitted so clients have a stable contract.
     */
    String unit();

    /**
     * Executes the metric computation for the current tenant context (set by the
     * tenant interceptor on the current thread before this method is called).
     *
     * @return a GeoJSON {@code FeatureCollection} as a nested {@code Map}
     */
    Map<String, Object> execute();
}
