package com.warehouse.warehouse_platform.tenant.gis.layout;

/**
 * Immutable rectangle used during the normalized→EPSG:4326 coordinate-mapping
 * step in {@code LayoutToGisConversionService}.
 *
 * Coordinates can be in any consistent unit (normalized 0-100, degrees, metres).
 */
public record Bounds(double left, double right, double top, double bottom) {

    public double width()  { return right - left; }
    public double height() { return bottom - top; }

    /** Full normalized warehouse space (0–100 in both axes). */
    public static Bounds fullNormalized() {
        return new Bounds(0.0, 100.0, 0.0, 100.0);
    }
}
