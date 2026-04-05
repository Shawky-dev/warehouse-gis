**WarehouseGIS**

Technical Implementation Plan

Phase 1: 2D GIS  |  Phase 2: 3D GIS

Spring Boot  ·  React + Vite  ·  PostgreSQL/PostGIS  ·  GeoServer  ·  ArcGIS JS SDK

*March 2026  |  Confidential*

# Table of Contents

# 1. System Architecture

The WarehouseGIS architecture follows a hybrid approach. PostGIS is the single source of truth for all spatial data. Spring Boot is the authority for business data (inventory, tenants, RBAC, employees, products) and serves all operational GIS layers as REST/GeoJSON endpoints during Phase 1. GeoServer is provisioned in parallel — it reads from the same PostGIS tables via JDBC — so that when the GIS team is ready to take over layer management, the migration requires only adding a Spring Boot proxy controller and updating the frontend data source URLs, with no schema or data changes.

This hybrid approach was chosen because the warehouse is an indoor mapping problem, not a traditional outdoor GIS use-case. A single-building floor plan does not need a pre-seeded WMTS tile pyramid. The operational GIS layers are simpler and more debuggable when served as typed GeoJSON from Spring Boot than as OGC Web Feature Service requests through a separate process. GeoServer remains valuable as the future path for GIS team-managed layers and as the only practical option for serving ArcGIS Pro-exported raster data (static heatmaps).

## 1.1 Component Topology

The following diagram illustrates the primary data paths between all system components. Each connection is annotated with its protocol and the type of data it carries.

┌──────────────────────────────────────────────────────────────────────────────────┐

│  CLIENT TIER                                                                        │

│                                                                                     │

│    [ArcGIS Pro]            [Browser / React + Vite Frontend]                        │

│         |                    |                     |                                 │

│    Shapefile/              REST JSON              WMS/WFS/WMTS                       │

│    GeoPackage/             (JWT Bearer +          (OGC standards)                    │

│    GeoTIFF export          X-TENANT-ID)                                             │

│         |                    |                     |                                 │

└─────────|────────────────|─────────────────────|─────────────────────────────────┘

         |                    |                     |

┌─────────|────────────────|─────────────────────|─────────────────────────────────┐

│  SERVICE TIER                                                                       │

│                                                                                     │

│    [GeoServer REST API]     [Spring Boot API]     [GeoServer OGC]                   │

│    (layer publish/admin)    (Port 8080)           (WMS/WFS/WMTS endpoints)           │

│         |                    |                     |                                 │

│         |               JDBC + JTS/Hibernate       |                                 │

│         |               Spatial                    |                                 │

│         |                    |                     |                                 │

│         \___________   ______|______   ____________/                                  │

│                     \ |             | /                                               │

│                   [PostgreSQL 15 + PostGIS 3.4]                                      │

│                    (warehouse DB + spatial tables)                                    │

│                                                                                     │

└──────────────────────────────────────────────────────────────────────────────────┘

## 1.2 Protocol Reference

Each connection between components uses a specific protocol chosen for its strengths. The frontend talks to the warehouse backend over REST/JSON with JWT authentication and the X-TENANT-ID header for multi-tenancy isolation. The frontend talks to GeoServer over OGC standards (WMS for raster tiles, WFS for editable vector features, WMTS for cached tile sets). GeoServer connects to PostGIS via JDBC to read spatial tables directly. The Spring Boot backend also connects to the same PostGIS database via JTS/Hibernate Spatial for server-side spatial queries (e.g., zone containment checks for R1). The GIS team publishes data to GeoServer either by uploading shapefiles/GeoPackages through the GeoServer REST API or admin UI, or by GeoServer reading directly from PostGIS tables they populate.

| **Protocol** | **Between** | **Use Case** | **When to Use** |
| --- | --- | --- | --- |
| **REST/JSON + JWT** | Frontend ↔ Backend | All business data AND all operational GIS vector layers in Phase 1: inventory CRUD, auth, tenants, employees, KPI aggregations, zone GeoJSON, shelf GeoJSON, buffer zone GeoJSON, safety equipment GeoJSON, density-points | Every interaction that involves warehouse business logic, RBAC checks, data mutations, or GIS vector layer data in Phase 1. The JWT bearer token and tenant slug in the URL path enforce tenant isolation. This is the **primary data path** for all GIS feature data during Phase 1. |
| **SVG MediaLayer** | Frontend ↔ Backend | Warehouse floor plan base layer (walls, aisles, doors) | **Phase 1 floor plan approach.** The floor plan is uploaded as an SVG (from LibreCAD or CAD export), stored on disk per tenant, and served by Spring Boot. The ArcGIS JS SDK renders it as a `MediaLayer` georeferenced to the warehouse's EPSG:4326 anchor coordinates using `widthMeters` and `lengthMeters`. Appropriate for indoor single-building mapping. Migrates to a GeoServer WFS `FeatureLayer` when the GIS team delivers a georeferenced GeoPackage. |
| **WMS (Web Map Service)** | Frontend ↔ GeoServer (via proxy) | Raster tile rendering: static heatmap (R4) from ArcGIS Pro-exported GeoTIFF | **Phase 1 (static heatmap only).** GeoServer WMS is the correct path for ArcGIS Pro raster exports because Spring Boot does not serve raster tiles. All WMS requests are proxied through Spring Boot (`GET /{slug}/gis/wms?...`) so GeoServer is never directly reachable by the client. For all other (vector) layers, WMS is a future option only. |
| **WFS (Web Feature Service)** | Frontend ↔ GeoServer (via proxy) | Vector features: zones, shelves, buffer zones, safety equipment | **Future migration path.** GeoServer is populated with all vector data via PostGIS DataStore and is ready to serve WFS. The frontend does not call GeoServer WFS directly in Phase 1. Migration requires adding a Spring Boot proxy controller (`/{slug}/gis/wfs`) and changing frontend `FeatureLayer` source URLs — no data or schema changes needed since PostGIS is the shared source. |
| **WMTS (Cached Tiles)** | Frontend ↔ GeoServer | Pre-cached tile sets for static base layers | **Not used in Phase 1.** The floor plan is served as a georeferenced SVG. GeoWebCache tile seeding can be enabled if the GIS team delivers a GeoPackage floor plan in the future. Zone boundaries could also be cached via WMTS at that point. |
| **JDBC (PostGIS)** | GeoServer ↔ PostGIS, Backend ↔ PostGIS | PostGIS is the single source of truth. GeoServer reads spatial tables directly. Spring Boot writes all spatial data and uses Hibernate Spatial / JTS for server-side spatial queries and GeoJSON responses. | GeoServer is configured with a PostGIS DataStore per tenant workspace so that any data written by Spring Boot is immediately reflected in GeoServer's layers — no data duplication. Spring Boot uses PostGIS for spatial rule validation (`ST_Contains` for R1, `ST_Intersects` for R2) and for serving GeoJSON endpoints (R1–R4, R6). |

## 1.3 Multi-Tenancy and RBAC for GIS Layers

**Phase 1 (current approach):** Tenant isolation for GIS layers is enforced by the same mechanism as all other backend data — via the JWT bearer token and the tenant slug in the URL path (`/{tenantSlug}/gis/**`). Spring Boot's existing RBAC middleware validates the token and the tenant before any GIS endpoint responds. Since the frontend calls Spring Boot REST GeoJSON endpoints for all vector GIS layers (not GeoServer directly), no separate auth system needs to be synchronised. Each GeoJSON endpoint filters its PostGIS query by `tenant_id`, which is the same isolation model used across inventory, layout, and employee data. RBAC granularity within a tenant (e.g., hiding buffer zones from operators) is enforced by checking role permissions on each Spring Boot endpoint, exactly as with other business endpoints.

GeoServer workspaces are organised by tenant (`wh_{tenantSlug}`) and provisioned automatically when a tenant's floor plan is published. GeoServer runs on the internal Docker network with no host port exposed — only the Spring Boot container can reach it. The GIS team accesses the GeoServer admin UI through a separately bound port restricted to their network or VPN.

**Future migration path (Phase 2 or GIS team handoff):** When the project transitions to serving operational layers from GeoServer WFS/WMS, a proxy controller is added to Spring Boot listening on `/{tenantSlug}/gis/wms` and `/{tenantSlug}/gis/wfs`. This controller validates the JWT, extracts the tenant slug, checks the user's roles, and rewrites the request to GeoServer targeting the correct workspace. Because PostGIS is already the shared source of truth, no data migration is required. RBAC granularity is enforced in the proxy by inspecting role claims before forwarding specific layer names. The frontend migration is limited to changing `FeatureLayer` source URLs from the Spring Boot GeoJSON endpoints to the proxy WFS endpoints.

## 1.4 ArcGIS Pro Export Pipeline

The client's GIS team creates spatial analysis in ArcGIS Pro and needs a clear path to get that data into the web application. Here is the recommended pipeline for each data type they will produce:

**Vector data (zones, buffer polygons, equipment positions, shelf footprints): **Export from ArcGIS Pro as GeoPackage (.gpkg) or Shapefile (.shp). Upload to GeoServer via the REST API or admin console. Alternatively, the GIS team can publish directly to the PostGIS database from ArcGIS Pro using the ArcGIS Data Interoperability extension or QGIS as an intermediary, and GeoServer reads the PostGIS table immediately.

**Raster data (static heatmaps, density analysis, satellite imagery): **Export from ArcGIS Pro as GeoTIFF (.tif). Upload to GeoServer as a GeoTIFF coverage store. GeoServer will serve it via WMS. For large rasters, pre-generate overviews (pyramids) in ArcGIS Pro before export to improve tile rendering speed.

**Styled maps (symbology, color ramps, labels): **ArcGIS Pro styles do not transfer directly to GeoServer. The GIS team should document their intended symbology, and the dev team will recreate it as SLD (Styled Layer Descriptor) files in GeoServer, or the frontend will apply equivalent styling using ArcGIS JS SDK renderers. For simple cases, QGIS can convert ArcGIS Pro .lyrx files to SLD.

# 2. Phase 1 — 2D Feature Roadmap

Phase 1 delivers a fully functional 2D warehouse map integrated with the existing Spring Boot backend. All features listed below are scoped for 2D MapView rendering using the ArcGIS JavaScript SDK. Each row maps directly to one or more client requirements (R1–R6).

| **Feature** | **Req** | **Description** | **Tech / Layer** | **Integration Points** | **Priority** |
| --- | --- | --- | --- | --- | --- |
| **Floor Plan Base Layer** | Base | Render the warehouse floor plan (walls, aisles, doors, gates) as the base map layer. This is the canvas on which all other layers are overlaid. The floor plan is uploaded as an SVG (from LibreCAD or CAD export) and georeferenced to the warehouse's real-world coordinates. | SVG file stored on disk per tenant, served by Spring Boot at `GET /{slug}/gis/floorplan/svg`. ArcGIS JS SDK renders it as a `MediaLayer` using `ImageElement` + `ExtentAndRotationGeoreference` with an EPSG:4326 bounding box derived from `anchorLat`, `anchorLon`, `widthMeters`, and `lengthMeters` set in `application.yaml`. This approach is correct for indoor single-building mapping and avoids GeoWebCache complexity. When the GIS team delivers a georeferenced GeoPackage, the floor plan can migrate to a GeoServer WFS `FeatureLayer` without changing anything else in the stack. | Spring Boot manages SVG storage per tenant (`data/floorplans/{tenantSlug}.svg`). Anchor coordinates and warehouse dimensions are configured per tenant in `application.yaml`. The GeoServer workspace is provisioned on publish but the floor plan SVG is not routed through GeoServer in Phase 1. | P0 — Sprint 1 |
| **Zone / Area Management** | R1 | Display warehouse zones (refrigerated, dry storage, hazmat, receiving dock, etc.) as colored polygons on the map. Each zone has storage rules defining which product categories are allowed. When an employee registers a product in a zone that violates rules, the system warns them and highlights the correct zone. | Zone geometries stored in PostGIS (`gis_blocks` table, `template_name = 'Zone'`) with `zone_type VARCHAR` and `allowed_category_ids UUID[]` columns added to the schema. Spring Boot serves `GET /{tenant}/gis/zones/geojson` returning a GeoJSON FeatureCollection. ArcGIS JS SDK loads this as a `FeatureLayer` from the GeoJSON URL and applies a `UniqueValueRenderer` colored by `zone_type`. GeoServer is also configured with a PostGIS DataStore SQL View for the same `gis_blocks` zone rows as layer `wh_{slug}:zone_boundaries`, keeping it ready for the future WFS migration path. Rule validation runs server-side in Spring Boot using JTS `ST_Contains` queries. | Deep backend integration: `POST /{tenant}/inventory/put-away` triggers a spatial check — Spring Boot queries PostGIS to confirm the target location's geometry is within an allowed zone for the product's category. If invalid, return 409 with the correct `zone_id`. Frontend highlights the violation zone in red and the suggested zone in green using `FeatureLayer.effect`. | P0 — Sprint 2 |
| **Hazard Buffer Zones** | R2 | Display buffer polygons around hazardous/flammable material storage areas. These buffers are pre-computed in ArcGIS Pro using buffer analysis. Storing any product inside a buffer boundary is prohibited. The system enforces this spatially. | Buffer geometries stored in PostGIS `gis_buffer_zones` table (columns: `id, tenant_id, geometry POLYGON, material_type, buffer_distance_m`). Populated by importing an ArcGIS Pro GeoPackage export or via an admin API. Spring Boot serves `GET /{tenant}/gis/buffer-zones/geojson`. ArcGIS JS SDK loads as a `FeatureLayer` and renders with a crosshatch `SimpleFillSymbol`. GeoServer is configured to read the same `gis_buffer_zones` table as `wh_{slug}:buffer_hazmat` for the future migration path. Server-side enforcement via `ST_Intersects` in Spring Boot. | When a put-away request is made, Spring Boot checks if the target location's geometry intersects any active buffer zone for the tenant. If it does, the request is rejected with a 403 and an explanation. The frontend also performs a client-side pre-check using `geometryEngine.intersects()` against the buffer features already loaded in the local `FeatureLayer`, giving immediate visual feedback before the API call. | P0 — Sprint 2 |
| **Product Density Heatmap (Static)** | R4 | Display a static heatmap of product density as prepared by the client's GIS team in ArcGIS Pro. This is a raster layer showing density analysis across warehouse areas using a red-to-blue color ramp. | Client exports a GeoTIFF from ArcGIS Pro. Uploaded to GeoServer as a GeoTIFF CoverageStore via the GeoServer admin UI or REST API. Served via WMS. ArcGIS JS SDK consumes as a `WMSLayer`. No interactivity needed — purely visual overlay. This is the one Phase 1 case where GeoServer is in the live request path, since Spring Boot does not serve rasters. The WMS request must be proxied through Spring Boot (`GET /{slug}/gis/wms?SERVICE=WMS&...`) so GeoServer remains unreachable by the browser directly. | The Spring Boot WMS proxy endpoint for this feature is implemented in Sprint 3, specifically for WMS raster pass-through. It validates the JWT and tenant, then forwards the `GetMap` request to the correct GeoServer workspace. The frontend provides a layer toggle to show/hide the heatmap. The GIS team updates the layer periodically by re-uploading the GeoTIFF to GeoServer. | P1 — Sprint 3 |
| **Product Density Heatmap (Dynamic)** | R4 | Generate a live heatmap from current inventory data. Each storable location (leaf node) becomes a weighted point based on its fill percentage. The heatmap updates as inventory changes. | Spring Boot exposes a REST endpoint GET /{tenant}/gis/density-points returning GeoJSON of leaf-location centroids with a quantity_weight attribute. ArcGIS JS SDK renders this as a FeatureLayer with HeatmapRenderer, configuring field: 'quantity_weight', colorStops from blue (low) to red (high), radius and minDensity tuned to warehouse scale. | Directly queries the inventory on-hand data from the warehouse backend. The density-points endpoint joins leaf-location `gis_blocks` rows (PostGIS) with the current on_hand_quantity from the inventory table, then returns GeoJSON. Frontend refreshes on a configurable interval (e.g., 60s) or on inventory mutation events. | P1 — Sprint 3 |
| **Location Click-to-Inspect** | R6 | Clicking a storable location (leaf node) on the map opens a popup showing: location label/code, product name(s), current quantity, zone, and category. This bridges the GIS layer (location geometry) with warehouse business data (inventory records). | Leaf-location footprints stored in PostGIS (`gis_blocks` table). The endpoint identifies leaf locations by querying `gis_blocks` rows whose `layout_block_id` has no children in `layout_blocks` — these are the blocks a warehouse operator can actually store stock in, regardless of what template name the tenant chose (e.g. "Shelf", "Bin", "Rack Position"). The `layout_block_id` column already serves as `location_id`; no extra FK column is needed. Spring Boot serves `GET /{tenant}/gis/locations/geojson` returning leaf-location polygons with `location_id` (= `layout_block_id`) in the attributes. ArcGIS JS SDK loads as a `FeatureLayer` with a `PopupTemplate` using a custom async content function. On click (via `hitTest()` or `FeatureLayer` click event), the function extracts `location_id` from the feature attributes and calls `GET /{tenant}/inventory/stock/by-location/{locationId}` to fetch live inventory data. GeoServer is also configured with the leaf-location layer as `wh_{slug}:location_footprints` for the future WFS migration. | The popup content function receives the clicked feature's `location_id` attribute and makes a REST call to the existing inventory stock by-location endpoint. The response is formatted into popup HTML showing: location code, product list with quantities, zone name, category, and fill percentage. The `FeatureLayer`'s `outFields` must include `location_id`. | P0 — Sprint 2 |
| **Safety / Monitoring Overlay** | R3 | Display fire extinguisher positions as point symbols, camera positions as point symbols with coverage-area polygons (viewsheds). Conceptual alerts: if an extinguisher is "removed" (status toggled in DB), its icon turns red. If a camera's coverage polygon overlaps with an uncovered zone, highlight the blind spot. | Equipment positions and coverage polygons stored in PostGIS `gis_safety_equipment` table (columns: `id, tenant_id, equipment_type ENUM, status ENUM(active/removed/offline), geometry POINT, coverage_geom POLYGON`). Spring Boot serves `GET /{tenant}/gis/safety-equipment/geojson`. ArcGIS JS SDK loads as a `FeatureLayer` and applies a `UniqueValueRenderer` switching symbol based on `status`: active = green icon, removed = red icon, offline = gray icon. GeoServer is configured with the same `gis_safety_equipment` table as `wh_{slug}:safety_equipment` for the future migration path. Blind spot detection uses `geometryEngine.difference`. | Spring Boot provides `GET /{tenant}/gis/safety-equipment/geojson` and `PATCH /{tenant}/gis/safety-equipment/{id}/status`. Since this is conceptual (no real sensors), the status is toggled via an admin API call. The frontend re-fetches the GeoJSON or listens for status changes and refreshes the `FeatureLayer` to update icon rendering dynamically. | P1 — Sprint 4 |
| **Layer Toggle + Tenant Filtering** | Multi-tenant | A layer control panel lets users toggle visibility of each GIS layer (zones, heatmap, buffers, safety, locations). Tenant filtering ensures each user only sees layers belonging to their tenant. The map initializes with the correct layers for the logged-in tenant. | Custom React sidebar panel with per-layer visibility toggles bound to each `FeatureLayer`'s `visible` property. The frontend reads the tenant slug from the auth context and constructs all Spring Boot GeoJSON endpoint URLs using that slug (`/{tenantSlug}/gis/**`). Each `FeatureLayer` is conditionally added to the `MapView` based on the user's RBAC permissions read from the JWT claims (e.g., a warehouse operator does not see the buffer hazmat layer). Layer toggle state is local React state. | Tenant isolation is enforced server-side on every GeoJSON endpoint. RBAC layer filtering happens in the frontend by checking permission claims before adding each `FeatureLayer` to the map. No `LayerList` widget is used — the custom sidebar provides finer control and matches the existing UI pattern already implemented in `ViewerLayerPanel`. | P0 — Sprint 1 |
| **Measurement + Annotation** | Utility | Built-in tools for measuring distances and areas on the warehouse map. Useful for the GIS team to verify spatial accuracy and for managers to assess space utilization. | ArcGIS JS SDK Measurement widget (DistanceMeasurement2D, AreaMeasurement2D). These are out-of-the-box widgets requiring minimal configuration. Coordinate system must match the map's spatial reference (projected CRS, not geographic, for accurate area/distance in meters). | No backend integration. These are client-side utility tools. Ensure the map's spatial reference is set to a projected CRS appropriate for the warehouse's location (e.g., a UTM zone) for accurate metric measurements. | P2 — Sprint 4 |
| **Dashboard with Spatial KPIs** | R5 | A dashboard panel (sidebar or overlay) showing: warehouse occupancy rate (%), remaining free space, top 5 areas by storage pressure (bar chart), logged-in employee count, and full warehouse/shelf warnings with percentages. | Spring Boot exposes a dedicated GET /{tenant}/dashboard/spatial-kpis endpoint that aggregates inventory data. The frontend renders charts using a charting library (Recharts or the ArcGIS JS SDK Charts module). The dashboard is a React component overlaid on the map, not a separate page. | The KPI endpoint joins inventory on-hand aggregates with zone/shelf metadata. Occupancy = total_quantity / total_capacity per zone. Top 5 pressure areas = zones sorted by occupancy descending. Employee count comes from the existing session/login tracking. Full warehouse/shelf warnings check capacity thresholds. | P1 — Sprint 5 |
| **GIS Data Upload Workflow** | Admin | A documented workflow for the GIS team to publish ArcGIS Pro exports into GeoServer. This is not a web UI feature but a process: the GIS team exports data, uploads to GeoServer, and verifies on the web app. | GeoServer REST API for programmatic uploads; GeoServer admin UI (port 8080/geoserver) for manual uploads. A shell script or QGIS plugin can automate the export-to-GeoServer pipeline. Documentation is the primary deliverable here. | After upload, GeoServer reads the new layer from PostGIS or file store. The frontend picks up new layers on next load if they follow the naming convention. No backend code changes needed — this is infrastructure and process documentation for the GIS team. | P0 — Sprint 1 |

# 3. Phase 2 — 3D Feature Roadmap

Phase 2 extends the validated 2D system into a 3D SceneView. The primary goal is to add vertical dimension awareness: rack heights, multi-floor navigation, and volumetric capacity visualization. All existing 2D features must continue to function in the 3D context.

## 3.1 Feature Transition: 2D to 3D

When switching from MapView to SceneView in the ArcGIS JS SDK, certain classes and configurations change. The following table outlines the specific transitions for each Phase 1 feature.

| **Phase 1 Feature** | **2D Implementation** | **3D Implementation** | **Migration Notes** |
| --- | --- | --- | --- |
| **Map Container** | MapView with esri/views/MapView | SceneView with esri/views/SceneView | Replace the view constructor. Most layers, widgets, and event handlers work in both views. Design the React component to accept a viewType prop from the start so switching is seamless. |
| **Zone Polygons** | FeatureLayer with 2D SimpleFillSymbol | Same FeatureLayer, add ExtrudeSymbol3DLayer to give zones height (e.g., 0.1m extrusion for ground markings, or full wall height for physical zone barriers) | FeatureLayer works in both views. Add a 3D-specific renderer using PolygonSymbol3D with ExtrudeSymbol3DLayer. The elevationInfo property must be set to 'on-the-ground' or 'relative-to-ground'. |
| **Shelf Features** | FeatureLayer with polygon footprints | SceneLayer or FeatureLayer with 3D Object symbols (glTF models for racks) | For simple rack representation, use FeatureLayer with ObjectSymbol3DLayer (box primitives). For realistic racks, use point-placed glTF models via a SceneLayer or WebStyleSymbol. The shelf geometry transitions from POLYGON to POINT with model placement. |
| **Heatmap** | HeatmapRenderer on FeatureLayer | HeatmapRenderer does NOT work in SceneView. Use a draped WMS raster layer or PointCloudLayer with colored points at varying heights to represent density. | This is the most significant migration challenge. The dynamic heatmap must switch from client-side HeatmapRenderer to a server-rendered WMS approach (GeoServer SLD with interpolation), or use 3D-specific visualization like vertical bar charts at each point. |
| **Safety Overlay** | PictureMarkerSymbol for icons, polygon coverage areas | PointSymbol3D with IconSymbol3DLayer for equipment. Coverage volumes become 3D meshes or extruded polygons showing camera frustum/cone geometry. | Equipment icons can remain flat (callout mode in PointSymbol3D). Camera coverage volumes should be extruded using the camera's tilt angle and range to show true 3D coverage frustums. Blind spot analysis becomes volumetric. |
| **Popups** | PopupTemplate with custom content | Identical — PopupTemplate works in SceneView unchanged | No migration needed. Popups anchor to the clicked 3D feature automatically. |
| **Measurement** | DistanceMeasurement2D, AreaMeasurement2D | DirectLineMeasurement3D, AreaMeasurement3D. Add ElevationProfile widget for vertical measurement. | Swap the widget classes. The 3D versions support vertical distance and sloped measurements. |

## 3.2 New 3D-Only Features

| **Feature** | **Description** | **Tech / Layer** | **Data Requirements** |
| --- | --- | --- | --- |
| **Rack Height Visualization** | Shelving racks rendered at their actual height with individual shelf levels visible. Shelves can be color-coded by occupancy at each level. Users can visually see which levels are full vs. empty. | FeatureLayer with ObjectSymbol3DLayer using parameterized box primitives (width, depth, height per rack). Each shelf level is a separate feature with a z-value offset. Color-coded via a ClassBreaksRenderer based on occupancy percentage. | Rack dimensions (width, depth, height) and individual shelf level z-offsets must be added to the PostGIS schema. The existing layout_blocks hierarchy (aisle > side > bay > level > shelf) provides the logical structure; add physical dimensions and z_offset_meters fields. |
| **Multi-Floor Navigation** | For multi-story warehouses: toggle between floor levels, with each floor rendered as a horizontal slab at its correct elevation. A floor selector widget lets users isolate one floor. | Each floor is a separate FeatureLayer with its elevationInfo.offset set to the floor's altitude. A custom React widget (floor selector) toggles layer visibility per floor. SceneView.goTo() animates the camera to the selected floor's elevation. | Floor elevation data: each floor's altitude in meters above ground. Floor plan polygons per level. If the warehouse is single-story, this feature simplifies to a ground-level slab with rack heights above it. |
| **Volumetric Capacity** | Show the 3D volume consumed by products on each shelf as a filled box proportional to occupancy. A shelf at 80% capacity shows a box filling 80% of the shelf's volume, color-coded from green (low) to red (high). | Custom 3D graphics rendered using ArcGIS JS SDK's Graphic class with ObjectSymbol3DLayer. For each shelf, compute a box mesh whose height = shelf_height * (quantity / capacity). Update dynamically from inventory data. | Per-shelf capacity and current quantity. The existing inventory on-hand endpoint provides quantity. Capacity must be defined per shelf location in the DB. Shelf physical dimensions (width, depth, height) needed for mesh sizing. |
| **3D Building Shell** | A 3D model of the warehouse building exterior (walls, roof, loading docks) provides spatial context. The model can be made semi-transparent or sectioned to allow interior viewing. | Load a 3D model via BuildingSceneLayer (if I3S format) or as a glTF mesh placed as a Graphic in the SceneView. Use SceneView.environment.lighting for realistic shadows. Transparency controlled via MeshSymbol3D with opacity. | The GIS team or an architect provides a 3D model in one of: I3S (Indexed 3D Scene Layer) for BuildingSceneLayer, glTF/GLB for mesh placement, or COLLADA (.dae). I3S is preferred for ArcGIS SDK integration. The model can be created from CAD/BIM exports. |
| **Fly-Through Navigation** | A guided camera path through the warehouse for presentations or spatial orientation. The user can trigger a fly-through that moves the camera along aisles. | SceneView.goTo() with animation options. Pre-define an array of camera positions (heading, tilt, position) representing waypoints. Animate between them using SceneView.goTo with a duration. Alternatively, use the Slides class to save named viewpoints. | Waypoint coordinates defining the camera path. These can be configured in the admin UI or hardcoded for a standard warehouse layout. No special data from the GIS team needed. |

## 3.3 3D Data Format Requirements

The client's GIS team needs to prepare data in specific formats for 3D features. Here are the required formats and how the team should produce them:

**I3S (Indexed 3D Scene Layers): **This is the native 3D format for ArcGIS. The team can create I3S scene layer packages (.slpk) from ArcGIS Pro by converting their 3D building models using the Create 3D Object Scene Layer Package tool. I3S is the preferred format for the warehouse building shell and any complex 3D geometry.

**glTF / GLB: **For individual rack models, equipment models, or decorative 3D objects. These can be created in Blender, SketchUp, or exported from CAD software (Revit, AutoCAD 3D). The ArcGIS JS SDK can place glTF models at specific coordinates using ObjectSymbol3DLayer with href pointing to the model URL.

**Multipatch (Esri): **ArcGIS Pro's native 3D geometry type. Can be exported to I3S via scene layer packages. If the team already has multipatch features in their geodatabase, this is the easiest path to 3D.

## 3.4 Performance Considerations

Rendering a warehouse with thousands of shelves in 3D requires careful performance management. The key strategies are: level-of-detail (LOD) scaling where distant shelves render as simple boxes while nearby shelves show full model detail, which I3S handles natively; frustum culling (built into SceneView) to avoid rendering off-screen objects; batching shelf features by zone so that only the visible zone's shelves are loaded in full detail; limiting the dynamic heatmap to a server-rendered WMS layer rather than thousands of client-side points; and using WebGL instancing (implicit in SceneLayer) for repeated rack geometry. For a warehouse with 5,000+ shelf locations, keep the FeatureLayer's maxScale and minScale properties configured so that individual shelves only resolve when zoomed in past a threshold — at overview zoom, show only zone-level aggregated data.

# 4. GeoServer Setup & Integration Plan

## 4.1 Workspace and Layer Organization

GeoServer workspaces should be organized by tenant to enforce data isolation at the infrastructure level. The recommended naming convention is:

wh_{tenantSlug} — One workspace per tenant. Example: wh_acme, wh_globex. This maps directly to your existing tenant slug convention.

Within each workspace, layers follow a consistent naming pattern:

{workspace}:{layer_type}_{feature_name} — Examples: wh_acme:base_floorplan, wh_acme:zone_boundaries, wh_acme:safety_extinguishers, wh_acme:shelf_footprints, wh_acme:buffer_hazmat, wh_acme:heatmap_static.

For layer groups, create a default group per tenant that includes the base map and core layers, so the frontend can load the group in a single request.

## 4.2 Supported Publishing Formats

| **Format** | **GeoServer Store Type** | **Use Case** | **Recommendation** |
| --- | --- | --- | --- |
| **PostGIS Direct** | PostGIS DataStore | Any vector data that changes frequently or is managed by the web app (shelves, zones, equipment) | PREFERRED for all dynamic data. GeoServer reads directly from PostGIS tables. Changes in the database (e.g., new shelf added via Spring Boot) are immediately visible on the map. Configure one PostGIS store per tenant workspace. |
| **GeoPackage (.gpkg)** | GeoPackage DataStore | Vector data exports from ArcGIS Pro (buffer analysis, zone overlays, one-time imports) | PREFERRED for ArcGIS Pro exports over Shapefile. No 10-char field name limit, supports multiple layers in one file, better UTF-8 support. Upload via GeoServer REST API or admin console. |
| **Shapefile (.shp)** | Shapefile DataStore | Legacy vector data or simple exports | Acceptable fallback. Note limitations: 10-char field names, single geometry type per file, .dbf encoding issues. Prefer GeoPackage when possible. |
| **GeoTIFF (.tif)** | GeoTIFF CoverageStore | Raster data: static heatmaps, density analysis, satellite/aerial imagery | PREFERRED for all raster data. Ensure the GIS team generates overviews (pyramids) in ArcGIS Pro before export for performance. Use cloud-optimized GeoTIFF (COG) format if files exceed 100 MB. |

## 4.3 Tile Caching Strategy (GeoWebCache)

GeoWebCache (GWC) tile pre-seeding is **not used in Phase 1**. The warehouse floor plan is served as a georeferenced SVG via `MediaLayer`, which renders immediately as a single image and requires no tile pyramid. The operational vector layers (zones, shelves, buffers, safety equipment) are served as GeoJSON from Spring Boot, not as tiled images.

GWC becomes relevant in two future scenarios: (1) if the GIS team delivers a georeferenced GeoPackage floor plan and the project migrates to a GeoServer WFS/WMTS floor plan layer, or (2) in Phase 2 when the static 3D heatmap needs a draped WMS raster layer. At that point, configure GWC with the EPSG:3857 (Web Mercator) gridset and seed tiles from zoom levels 15–22 for the warehouse's spatial extent. Zone boundary layers can be cached with a short expiry (e.g., 24 hours) since zones change infrequently. Dynamic layers (live heatmap, safety equipment) must never be cached — configure them as GWC pass-through.

## 4.4 Securing GeoServer Behind RBAC

GeoServer is **not exposed directly to the internet or to the host machine**. In `docker-compose.yml` the GeoServer service has no `ports:` mapping — it runs on the internal Docker network only. Only the Spring Boot container can reach it at `http://geoserver:8080/geoserver`. The GeoServer admin UI is bound to a separate port accessible only within the Docker network, which the GIS team reaches via a VPN or SSH tunnel.

In Phase 1, GeoServer is not in the live request path for vector layers, so no proxy controller is required for those layers. The one exception is the static raster heatmap (R4 static, Sprint 3): a minimal Spring Boot WMS proxy controller is implemented at `GET /{tenantSlug}/gis/wms` that validates the JWT, checks the tenant, and forwards `GetMap` requests to the correct GeoServer workspace. This controller is intentionally narrow — it only proxies `SERVICE=WMS` requests and rejects anything else.

**Future full proxy (Phase 2 or GIS team handoff):** When vector layers migrate to GeoServer WFS, the proxy controller is extended to handle `/{tenantSlug}/gis/wfs` with the same JWT + tenant + role validation pattern described in Section 1.3. At that point GeoServer's own security can be set to allow-all from the internal Docker network since the Spring Boot proxy is the enforcing perimeter.

# 5. Data Flow for Each Client Requirement

For each requirement (R1–R6), this section traces the complete data path from origin to user interaction.

## 5.1 R1 — Storage Location Organization (Spatial Rules Engine)

### Data Origin

Zone geometries: either auto-generated from the warehouse layout tree via `LayoutToGisConversionService` into the `gis_blocks` table, or manually drawn in the `FloorPlansPage` editor and saved as `gis_blocks` records. Zone business attributes (`zone_type`, `allowed_category_ids[]`) are added to the `gis_blocks` schema for zone-depth rows and configured via an admin API. The GIS team can also import polygons from ArcGIS Pro via GeoPackage — these are loaded into the same `gis_blocks` table.

### Data Flow

Zone polygons written to `gis_blocks` (template_name='Zone') with `zone_type` and `allowed_category_ids`

GeoServer reads same `gis_blocks` rows via PostGIS DataStore SQL View → available as `wh_{slug}:zone_boundaries` (WFS, future path)

Frontend map viewer requests `GET /{tenant}/gis/zones/geojson` → Spring Boot queries `gis_blocks WHERE template_name='Zone'` → returns GeoJSON FeatureCollection

Frontend loads as `FeatureLayer` from GeoJSON URL → renders with `UniqueValueRenderer` colored by `zone_type`

Employee registers product → `POST /{tenant}/inventory/put-away` → Spring Boot queries:

  SELECT z.zone_type, z.allowed_category_ids FROM gis_blocks z

  WHERE z.template_name = 'Zone' AND ST_Contains(z.geometry, (SELECT geometry FROM gis_blocks WHERE id = :locationGisBlockId))

  → If product category NOT IN allowed_category_ids → return 409 + correct zone name

Frontend receives 409 → highlights violation zone red, correct zone green using `FeatureLayer.effect`

## 5.2 R2 — Buffer Zones & Hazard Analysis

### Data Origin

Buffer polygons are pre-computed in ArcGIS Pro using the Buffer geoprocessing tool around hazardous material storage points. The output polygon feature class is exported as a GeoPackage and imported into the PostGIS `gis_buffer_zones` table by the GIS team. Alternatively, buffer polygons can be drawn manually via the `FloorPlansPage` editor if a dedicated buffer template is configured.

### Data Flow

ArcGIS Pro Buffer Analysis → GeoPackage export → Import to PostGIS `gis_buffer_zones`

GeoServer reads `gis_buffer_zones` via PostGIS DataStore → available as `wh_{slug}:buffer_hazmat` (WFS, future path)

Frontend requests `GET /{tenant}/gis/buffer-zones/geojson` → Spring Boot returns GeoJSON FeatureCollection

Frontend loads as `FeatureLayer` → renders as crosshatched red polygons using `SimpleFillSymbol`

Employee attempts put-away → `POST /{tenant}/inventory/put-away` → Spring Boot queries:

  SELECT COUNT(*) FROM gis_buffer_zones b

  WHERE b.tenant_id = :tenantId AND ST_Intersects(b.geometry, :shelfGeometry)

  → If count > 0 → return 403 "Location is within a hazardous material buffer zone"

Frontend also performs client-side pre-check using `geometryEngine.intersects()` against the buffer `FeatureLayer`'s already-loaded features, giving immediate visual feedback before the API call

  → If count > 0 → return 403 "Location is within a hazardous material buffer zone"

Frontend also performs client-side pre-check using geometryEngine.intersects()

## 5.3 R3 — Conceptual Safety / Monitoring Layer

### Data Origin

Equipment positions: GIS team places fire extinguisher and camera points in ArcGIS Pro, exports as GeoPackage. Camera coverage polygons are created using viewshed analysis or manual polygon drawing. These are imported into the PostGIS `gis_safety_equipment` table. Status data is conceptual — managed via a Spring Boot admin API.

### Data Flow

ArcGIS Pro → GeoPackage (equipment points + coverage polygons) → PostGIS `gis_safety_equipment`

GeoServer reads `gis_safety_equipment` via PostGIS DataStore → available as `wh_{slug}:safety_equipment` (WFS, future path)

Frontend requests `GET /{tenant}/gis/safety-equipment/geojson` → Spring Boot returns GeoJSON

Frontend loads as `FeatureLayer` → `UniqueValueRenderer`: active = green icon, removed = red icon, offline = gray icon

Admin toggles status via `PATCH /{tenant}/gis/safety-equipment/{id}/status` → frontend re-fetches or refreshes `FeatureLayer`

Frontend computes blind spots: `geometryEngine.difference(warehousePolygon, unionOfCoveragePolygons)`

  → Remaining geometry = blind spots → rendered as orange translucent polygons

## 5.4 R4 — Heat Map (Density Visualization)

### Static Heatmap

ArcGIS Pro density analysis → GeoTIFF export → GeoServer CoverageStore (uploaded by GIS team via GeoServer admin UI)

Frontend requests `GET /{slug}/gis/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=wh_{slug}:heatmap_static&...`

→ Spring Boot WMS proxy (implemented Sprint 3) validates JWT + tenant → forwards to `http://geoserver:8080/geoserver/wh_{slug}/wms`

Frontend loads as `WMSLayer` → togglable overlay, no interactivity

### Dynamic Heatmap

Inventory changes → Spring Boot DB updates on_hand_quantity per location

Frontend requests GET /{tenant}/gis/density-points → Spring Boot queries:

  SELECT s.centroid_geom, SUM(i.quantity) as weight

  FROM gis_shelves s JOIN inventory_on_hand i ON s.location_id = i.location_id

  GROUP BY s.id → returns GeoJSON FeatureCollection

Frontend renders as FeatureLayer + HeatmapRenderer (2D) or WMS fallback (3D)

## 5.5 R5 — Dashboard with Spatial KPIs

### Data Flow

Frontend dashboard component → GET /{tenant}/dashboard/spatial-kpis

Spring Boot aggregates from PostGIS + inventory tables:

  • Occupancy = SUM(on_hand_qty) / SUM(shelf_capacity) per zone → overall %

  • Top 5 pressure zones = zones ORDER BY occupancy_pct DESC LIMIT 5

  • Employee count = COUNT from active_sessions table

  • Full shelf warnings = shelves WHERE on_hand_qty >= capacity → count + pct

Returns JSON → Frontend renders with React chart components (Recharts or similar)

## 5.6 R6 — Shelf Interaction on Map

### Data Flow

Leaf-location footprints exist in `gis_blocks` — rows whose `layout_block_id` has no children in `layout_blocks`. The `layout_block_id` serves directly as `location_id`; no extra FK column is required.

GeoServer reads same leaf `gis_blocks` rows via PostGIS DataStore → available as `wh_{slug}:location_footprints` (WFS, future path)

Frontend requests `GET /{tenant}/gis/locations/geojson` → Spring Boot returns GeoJSON with `location_id` (= `layout_block_id`) in feature properties

Frontend loads as `FeatureLayer` with `outFields: ['location_id', 'label', 'position_path']`

User clicks location on map → ArcGIS JS SDK `hitTest()` → gets feature attributes including `location_id`

`PopupTemplate` custom async content function extracts `location_id` → calls:

  GET /{tenant}/inventory/on-hand/by-location/{locationId}

  → Returns product names, quantities, shelf metadata

Popup renders: shelf code, product list with quantities, zone name, category, fill %

# 6. Risk Register & Open Questions

## 6.1 Technical Risks

| **#** | **Risk** | **Impact** | **Mitigation** | **Likelihood** | **Owner** |
| --- | --- | --- | --- | --- | --- |
| **1** | ArcGIS Pro style-to-web gap: ArcGIS Pro .lyrx symbology files cannot be directly consumed by GeoServer or the ArcGIS JS SDK. The GIS team's carefully designed map styles will need manual recreation. | Medium — delays layer styling, potential visual mismatch between desktop and web maps. | Spike in Sprint 1: have the GIS team export one sample styled layer. Attempt QGIS .lyrx-to-SLD conversion. If that fails, document the manual SLD recreation workflow. Budget 2–4 hours per layer for style recreation. | High | Dev Team |
| **2** | Spatial reference mismatch: The GIS team may use a local projected CRS in ArcGIS Pro while the web app uses EPSG:3857 (Web Mercator) or EPSG:4326. Misaligned data will cause features to render in the wrong location. | High — all spatial data is misplaced; features don't align with the floor plan. | Establish a project-wide CRS standard in Sprint 0. Recommended: store all data in EPSG:4326 in PostGIS, let GeoServer reproject on the fly. Require the GIS team to export in EPSG:4326 or a documented UTM zone. Validate with a test overlay in Sprint 1. | Medium | GIS Team + Dev |
| **3** | GeoServer Docker resource contention: GeoServer is Java-based and memory-hungry. Running it alongside Spring Boot and PostGIS in Docker may cause OOM kills on resource-constrained servers. | High — GeoServer crashes under load, map tiles fail to render. | Allocate explicit memory limits in docker-compose.yml (JAVA_OPTS: -Xmx1g for GeoServer). Monitor memory usage in dev. If contention persists, separate GeoServer to its own host or use a managed GIS server. | Medium | Dev Team |
| **4** | 3D HeatmapRenderer incompatibility: The ArcGIS JS SDK's HeatmapRenderer does not work in SceneView. The Phase 1 dynamic heatmap will break when migrating to Phase 2 3D. | Medium — Phase 2 is blocked on finding an alternative visualization for density in 3D. | Plan the fallback during Phase 1: generate heatmap tiles server-side using GeoServer SLD interpolation and serve as a draped WMS layer in SceneView. Alternatively, use 3D vertical bars (extruded points) to represent density. | Certain | Dev Team |
| **5** | Floor plan georeferencing accuracy: The warehouse floor plan (from CAD or ArcGIS Pro) may not be accurately georeferenced, causing shelves and zones to misalign. | High — all spatial interactions (click, zone containment, buffer intersection) produce wrong results. | Require the GIS team to georeference the floor plan using at least 4 ground control points (GPS coordinates at warehouse corners). Verify in Sprint 1 by overlaying on satellite imagery. If GPS is unavailable, use a local coordinate system with a documented origin point. | Medium | GIS Team |
| **6** | Client data readiness: The GIS team may not have floor plan shapefiles, zone polygons, equipment positions, or buffer analysis ready when development needs them. This blocks frontend implementation. | High — development stalls waiting for GIS data; features cannot be tested or demoed. | Provide the GIS team with a data requirements document in Sprint 0 listing every dataset, its schema, and its deadline. Create synthetic test data (mock floor plan, fake zones) for development. Gate sprint milestones on data delivery dates. | High | Project Manager |
| **7** | GeoServer-to-PostGIS connection pool exhaustion: Both GeoServer and Spring Boot connect to the same PostGIS instance. Under load, connection pool limits may be reached, causing timeouts. | Medium — map tiles or API requests intermittently fail. | Configure separate connection pools: Spring Boot (HikariCP max-pool-size: 10) and GeoServer (store connection pool: max 10). Total must not exceed PostgreSQL's max_connections (default 100). Monitor with pg_stat_activity. | Low | Dev Team |
| **8** | ArcGIS JS SDK licensing: The ArcGIS JS SDK requires an API key or OAuth token for certain services (geocoding, routing, basemaps). Self-hosted data via GeoServer does not require licensing, but some widgets or basemap layers may. | Low — certain features are unavailable without a paid ArcGIS Developer account. | Verify in Sprint 0: test all planned SDK features with a free developer account. Since the warehouse map uses a custom floor plan (not Esri basemaps) and self-hosted data, most features should work without licensing. Document which features require an API key. | Medium | Dev Team |

## 6.2 Open Questions for Client Clarification

**Q1: **What coordinate reference system does the GIS team currently use in ArcGIS Pro for warehouse data? We need to establish a shared CRS before any data exchange.

**Q2: **Does the warehouse have multiple floors? If so, how many, and are floor elevations known? This determines whether multi-floor navigation in Phase 2 is needed.

**Q3: **For buffer zones (R2), what buffer distance does the GIS team use around hazardous materials? Is this a fixed radius (e.g., 5 meters) or does it vary by material type?

**Q4: **Does the GIS team have access to an ArcGIS Server or ArcGIS Online account? If so, we could optionally use ArcGIS Feature Services as an alternative to GeoServer for certain layers, though GeoServer remains the recommended approach for cost and control reasons.

**Q5: **For the safety overlay (R3), how should the "removed" status be triggered? Is there an admin UI, or should the API support direct status updates? What is the expected workflow?

**Q6: **What are the physical dimensions of the warehouse? We need approximate length, width, and height to calibrate map zoom levels, tile caching bounds, and 3D scene scale.

# 7. Milestones & Sprint Plan

## 7.1 Phase 1 — 2D GIS (Sprints 1–6, 12 weeks)

| **Sprint** | **Milestone** | **Deliverables** | **Dependencies** | **Client Deliverables** |
| --- | --- | --- | --- | --- |
| **Sprint 0 (Wk 0)** | Setup & CRS Agreement | GeoServer already added to `docker-compose.yml`. Remove GeoServer host port exposure (security fix — move to internal Docker network only). Create tenant workspaces. Establish project CRS (EPSG:4326 for storage, consistent anchor coordinates). Verify ArcGIS JS SDK licensing. Produce data requirements doc for GIS team. Create synthetic test data (mock zones, leaf locations, buffers) for development. | None | GIS team confirms CRS. Provides warehouse dimensions. Answers Q1–Q6. |
| **Sprint 1 (Wk 1–2)** | Base Map + Layer Infrastructure | Floor plan SVG `MediaLayer` rendering is functional (already implemented). Extend viewer (`WarehouseMapPage`) to load Spring Boot GeoJSON endpoints as `FeatureLayer` objects for zones, leaf locations, buffers, safety layers (initially empty data). Build complete layer toggle sidebar (extend existing `ViewerLayerPanel`). Schema migration: add `zone_type`, `allowed_category_ids` to `gis_blocks`; no extra column needed for leaf locations — `layout_block_id` already serves as `location_id`; create `gis_buffer_zones` and `gis_safety_equipment` tables. Implement Spring Boot GeoJSON endpoints: `GET /{slug}/gis/zones/geojson`, `GET /{slug}/gis/locations/geojson`, `GET /{slug}/gis/buffer-zones/geojson`, `GET /{slug}/gis/safety-equipment/geojson`. Configure GeoServer PostGIS DataStore SQL Views for all new tables (parallel to REST path). | Sprint 0 complete. GeoServer internal-only. | GIS team provides one sample styled layer for style-transfer validation. |
| **Sprint 2 (Wk 3–4)** | Zones + Shelves + Buffer Enforcement | Zone rendering with `UniqueValueRenderer` by `zone_type`. Location `FeatureLayer` with `PopupTemplate` click-to-inspect (R6). Server-side zone rule validation (`ST_Contains`) on put-away (R1). Buffer zone rendering with crosshatch symbol. Server-side buffer intersection check (`ST_Intersects`) on put-away (R2). Client-side `geometryEngine` pre-check. | Sprint 1 complete. Schema migrations and GeoJSON endpoints deployed. | GIS team provides zone polygons with `zone_type` attributes, leaf-location footprints mapped to `layout_block_id`, and buffer zone polygons from ArcGIS Pro analysis. |
| **Sprint 3 (Wk 5–6)** | Heatmaps (Static + Dynamic) | Static heatmap: implement Spring Boot WMS proxy endpoint (`GET /{slug}/gis/wms`), configure GeoServer GeoTIFF CoverageStore, load as `WMSLayer` in frontend (R4). Dynamic heatmap: build `GET /{tenant}/gis/density-points` REST endpoint (joins leaf-location `gis_blocks` centroids with `on_hand_quantity`), render with `HeatmapRenderer` on frontend (R4). Layer toggling for both heatmap modes. Tune heatmap color stops and radius for warehouse scale. | Sprint 2 complete. Leaf-location `layout_block_id` linkage in place. | GIS team provides static heatmap GeoTIFF from ArcGIS Pro density analysis. |
| **Sprint 4 (Wk 7–8)** | Safety Overlay + Tools | Fire extinguisher and camera point rendering (R3). Camera coverage polygon layer. Blind spot computation using geometryEngine.difference. Status toggle API + dynamic icon rendering. Measurement tools (DistanceMeasurement2D, AreaMeasurement2D). | Sprint 2 complete. Zone data available for blind spot computation. | GIS team provides equipment positions and camera coverage polygons. |
| **Sprint 5 (Wk 9–10)** | Dashboard + KPIs | Build spatial KPI endpoint in Spring Boot (R5). React dashboard component: occupancy gauge, top-5 pressure bar chart, employee count, full-shelf warnings. Integrate dashboard as a map sidebar panel. Real-time refresh on inventory changes. | Sprints 2–3 complete. Inventory + spatial data integrated. | None — all data comes from existing backend. |
| **Sprint 6 (Wk 11–12)** | Integration Testing + Polish | End-to-end testing: all R1–R6 flows. Multi-tenant isolation testing (tenant A cannot see tenant B's layers). Performance testing with realistic data volumes. Bug fixes and UX polish. Documentation for GIS team workflow. | All prior sprints complete. | GIS team provides final production-quality data for one tenant. |

## 7.2 Phase 2 — 3D GIS (Sprints 7–10, 8 weeks)

| **Sprint** | **Milestone** | **Deliverables** | **Dependencies** | **Client Deliverables** |
| --- | --- | --- | --- | --- |
| **Sprint 7 (Wk 13–14)** | SceneView Migration | Replace MapView with SceneView (or add view toggle). Migrate zone polygons to 3D symbology (ExtrudeSymbol3DLayer). Migrate leaf-location features to 3D (ObjectSymbol3DLayer). Verify popup functionality in 3D. Switch measurement widgets to 3D variants. | Phase 1 complete and stable. | None — uses existing Phase 1 data. |
| **Sprint 8 (Wk 15–16)** | Rack Heights + Volumetric Capacity | Add rack dimensions to PostGIS schema. Render racks at actual height with location-level features. Implement volumetric capacity visualization (filled boxes per storable location). Color-code by occupancy using ClassBreaksRenderer. | Sprint 7 complete. 3D rendering working. | GIS team provides rack dimension data (width, depth, height per rack type). Or: dimensions are measured on-site and entered into the system. |
| **Sprint 9 (Wk 17–18)** | Building Shell + Floor Nav | Load 3D building model (I3S or glTF). Implement transparency/section cutting for interior view. Multi-floor navigation widget (if applicable). Camera flythrough with waypoints. | Sprint 8 complete. | GIS team or architect provides 3D building model in I3S (.slpk) or glTF format. |
| **Sprint 10 (Wk 19–20)** | 3D Polish + Performance | Heatmap alternative for 3D (server-rendered WMS or vertical bars). LOD optimization for large location counts. Safety overlay 3D upgrade (camera frustum volumes). Performance profiling and optimization. Full regression testing. | Sprints 7–9 complete. | None. |

## 7.3 Dependency Map

The following dependencies represent hard blockers between sprints. If a dependency is not met, the dependent sprint cannot start.

**Sprint 1 → Sprint 2: **Floor plan must be rendering correctly before zone and leaf-location layers can be overlaid and visually verified.

**Sprint 2 → Sprint 3: **Leaf-location data must be in PostGIS with `layout_block_id` linkage before the dynamic heatmap endpoint can join inventory data to storable locations.

**Sprint 2 → Sprint 4: **Zone boundaries must exist for blind spot computation (camera coverage vs. warehouse footprint).

**Sprints 2–3 → Sprint 5: **The dashboard's spatial KPIs depend on zones, leaf locations, and inventory data all being integrated.

**Phase 1 complete → Sprint 7: **Phase 2 must not begin until Phase 1 is stable and tested. Any Phase 1 instability will compound in 3D.

**Sprint 8 → Sprint 9: **Rack height rendering must work before the building shell is added, since the shell's transparency/sectioning depends on understanding rack placement.

## 7.4 Visual Timeline

Week  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20

      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤

S1    [█████]                                                      Base Map

S2          [█████]                                                Zones+Shelves

S3                [█████]                                          Heatmaps

S4                      [█████]                                    Safety

S5                            [█████]                              Dashboard

S6                                  [█████]                        Integration

      ─────────────────────────────── PHASE 2 ─────────────────────

S7                                        [█████]                  3D Migration

S8                                              [█████]            Racks+Volume

S9                                                    [█████]      Building+Nav

S10                                                         [█████] 3D Polish

Total timeline: approximately 20 weeks (5 months) for both phases with a 2–3 developer team. Phase 1 delivers a fully functional 2D system at week 12. Phase 2 adds 3D capabilities by week 20.