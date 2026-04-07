package com.warehouse.warehouse_platform.tenant.gis.repository;

import java.util.UUID;

public interface WeightedPointProjection {
    UUID getLocationId();

    String getLabel();

    String getPositionPath();

    double getLon();

    double getLat();

    double getWeight();
}
