/**
 * Serialises a GeoJSON object and triggers a browser download.
 */
export function downloadGeoJson(data: object, filename: string): void {
    const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: "application/geo+json",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename.endsWith(".geojson") ? filename : `${filename}.geojson`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
