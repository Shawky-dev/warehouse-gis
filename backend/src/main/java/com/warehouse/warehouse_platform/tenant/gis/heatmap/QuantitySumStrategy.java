package com.warehouse.warehouse_platform.tenant.gis.heatmap;

import com.warehouse.warehouse_platform.tenant.gis.repository.WeightedHeatmapPointRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.WeightedPointProjection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Heatmap metric strategy that aggregates {@code SUM(qty_stock)} per stocked
 * leaf location.
 * Sources locations from leaf {@code gis_blocks} (blocks that are not parents
 * of other blocks),
 * requires a non-null {@code centroid_geom}, joins {@code v_stock} on
 * {@code layout_block_id = location_id}, and excludes non-positive aggregate
 * stock.
 */
@Component
public class QuantitySumStrategy implements HeatmapMetricStrategy {

    static final String KEY = "quantity_sum";

    private final WeightedHeatmapPointRepository pointRepository;

    public QuantitySumStrategy(WeightedHeatmapPointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String label() {
        return "Total Stock Quantity";
    }

    @Override
    public String description() {
        return "Sum of stock quantities at each leaf warehouse location";
    }

    @Override
    public String unit() {
        return null;
    }

    @Override
    public Map<String, Object> execute() {
        List<WeightedPointProjection> points = pointRepository.findQuantitySumPoints();
        List<Map<String, Object>> features = points.stream()
                .map(this::toFeature)
                .toList();
        return Map.of("type", "FeatureCollection", "features", features);
    }

    private Map<String, Object> toFeature(WeightedPointProjection p) {
        Map<String, Object> geometry = Map.of(
                "type", "Point",
                "coordinates", List.of(p.getLon(), p.getLat()));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("locationId", p.getLocationId());
        properties.put("label", p.getLabel());
        properties.put("positionPath", p.getPositionPath());
        properties.put("metricKey", KEY);
        properties.put("weight", p.getWeight());
        properties.put("rawValue", p.getWeight());

        return Map.of("type", "Feature", "geometry", geometry, "properties", properties);
    }
}
