package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.multi_tenancy.geoserver.GeoServerProperties;
import com.warehouse.warehouse_platform.tenant.gis.GeoServerProvisioningException;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GeoServerProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(GeoServerProvisioningService.class);

    private static final String DATASTORE_NAME  = "warehouse_postgis";
    private static final String LAYER_GROUP_NAME = "floorplan";

    private final RestTemplate geoServerRestTemplate;
    private final GeoServerProperties props;
    private final GisBlockRepository gisBlockRepository;

    public GeoServerProvisioningService(
            @Qualifier("geoServerRestTemplate") RestTemplate geoServerRestTemplate,
            GeoServerProperties props,
            GisBlockRepository gisBlockRepository) {
        this.geoServerRestTemplate = geoServerRestTemplate;
        this.props = props;
        this.gisBlockRepository = gisBlockRepository;
    }

    /**
     * Provisions (or updates) the GeoServer workspace for a tenant:
     * 1. Creates the workspace (idempotent)
     * 2. Creates the PostGIS datastore (idempotent)
     * 3. For each distinct template_name in gis_blocks, publishes a SQL View layer
     *    with its SLD style (idempotent — 409 = already exists, skip/update)
     * 4. Creates or updates a WMS layer group ("floorplan") compositing all layers
     *    ordered by depth so larger areas render beneath more specific ones
     */
    public void provisionTenantWorkspace(String tenantSlug) {
        String workspaceName = "wh_" + tenantSlug;

        createWorkspace(workspaceName);
        createDataStore(workspaceName, tenantSlug);

        List<String> templateNames = gisBlockRepository.findDistinctTemplateNames();
        List<LayerEntry> layerEntries = new ArrayList<>();
        for (String templateName : templateNames) {
            String layerSlug = toLayerSlug(templateName);
            publishSqlViewLayer(workspaceName, tenantSlug, layerSlug, templateName);
            int depth = Optional.ofNullable(gisBlockRepository.findMinDepthByTemplateName(templateName)).orElse(0);
            createOrReplaceLayerStyle(workspaceName, layerSlug, depth);
            assignDefaultStyle(workspaceName, layerSlug);
            layerEntries.add(new LayerEntry(layerSlug, depth));
        }

        // Order layers by depth ascending: lowest depth (biggest areas) renders first
        // so it sits at the bottom of the WMS composite image.
        layerEntries.sort(Comparator.comparingInt(LayerEntry::depth));
        List<String> orderedSlugs = layerEntries.stream().map(LayerEntry::slug).toList();
        createOrReplaceLayerGroup(workspaceName, orderedSlugs);
    }

    private record LayerEntry(String slug, int depth) {}

    // ─── GeoServer workspace ──────────────────────────────────────────────────

    private void createWorkspace(String workspaceName) {
        String url = props.url() + "/rest/workspaces";
        String body = "{\"workspace\":{\"name\":\"%s\"}}".formatted(workspaceName);
        try {
            geoServerRestTemplate.postForEntity(url, body, Void.class);
            log.debug("GeoServer workspace created: {}", workspaceName);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                log.debug("GeoServer workspace already exists, skipping: {}", workspaceName);
            } else {
                log.warn("GeoServer workspace creation failed [{}]: {}", e.getStatusCode(), e.getMessage());
                throw GeoServerProvisioningException.serverError(
                        "Failed to create GeoServer workspace '%s': %s".formatted(workspaceName, e.getMessage()));
            }
        }
    }

    // ─── PostGIS datastore ────────────────────────────────────────────────────

    private void createDataStore(String workspaceName, String tenantSlug) {
        String url = props.url() + "/rest/workspaces/" + workspaceName + "/datastores";
        try {
            geoServerRestTemplate.postForEntity(url, buildDataStoreJson(tenantSlug), Void.class);
            log.debug("GeoServer datastore created: {}/{}", workspaceName, DATASTORE_NAME);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                log.debug("GeoServer datastore already exists, skipping: {}/{}", workspaceName, DATASTORE_NAME);
            } else {
                log.warn("GeoServer datastore creation failed [{}]: {}", e.getStatusCode(), e.getMessage());
                throw GeoServerProvisioningException.serverError(
                        "Failed to create GeoServer datastore '%s': %s".formatted(DATASTORE_NAME, e.getMessage()));
            }
        }
    }

    private String buildDataStoreJson(String tenantSlug) {
        return """
                {
                  "dataStore": {
                    "name": "%s",
                    "type": "PostGIS",
                    "connectionParameters": {
                      "entry": [
                        {"@key": "dbtype",   "$": "postgis"},
                        {"@key": "host",     "$": "%s"},
                        {"@key": "port",     "$": "%d"},
                        {"@key": "database", "$": "%s"},
                        {"@key": "schema",   "$": "%s"},
                        {"@key": "user",     "$": "%s"},
                        {"@key": "passwd",   "$": "%s"}
                      ]
                    }
                  }
                }
                """.formatted(
                DATASTORE_NAME,
                props.dbHost(),
                props.dbPort(),
                props.dbName(),
                tenantSlug,
                props.dbUser(),
                props.dbPassword()
        );
    }

    // ─── SQL View layers (one per distinct template_name) ────────────────────

    private void publishSqlViewLayer(String workspaceName, String tenantSlug, String layerSlug, String templateName) {
        String url = props.url() + "/rest/workspaces/" + workspaceName
                + "/datastores/" + DATASTORE_NAME + "/featuretypes";
        try {
            geoServerRestTemplate.postForEntity(url, buildSqlViewFeatureTypeJson(tenantSlug, layerSlug, templateName), Void.class);
            log.debug("GeoServer SQL view layer published: {}/{}/{}", workspaceName, DATASTORE_NAME, layerSlug);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                log.debug("GeoServer layer already exists, skipping: {}", layerSlug);
            } else {
                log.warn("GeoServer layer publish failed for [{}] [{}]: {}", layerSlug, e.getStatusCode(), e.getMessage());
                throw GeoServerProvisioningException.serverError(
                        "Failed to publish GeoServer layer '%s': %s".formatted(layerSlug, e.getMessage()));
            }
        }
    }

    private String buildSqlViewFeatureTypeJson(String schemaName, String layerSlug, String templateName) {
        String escapedName = templateName.replace("'", "''");
        String sql = ("SELECT id, layout_block_id, label, position_path, geometry, centroid_geom " +
                "FROM " + schemaName + ".gis_blocks WHERE template_name = '" + escapedName + "'")
                .replace("\"", "\\\"");

        return """
                {
                  "featureType": {
                    "name": "%s",
                    "nativeName": "%s",
                    "title": "%s",
                    "srs": "EPSG:4326",
                    "enabled": true,
                    "metadata": {
                      "entry": {
                        "@key": "JDBC_VIRTUAL_TABLE",
                        "virtualTable": {
                          "name": "%s",
                          "sql": "%s",
                          "escapeSql": false,
                          "keyColumn": "id",
                          "geometry": {
                            "name": "geometry",
                            "type": "Polygon",
                            "srid": 4326
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(layerSlug, layerSlug, templateName, layerSlug, sql);
    }

    // ─── SLD style provisioning ───────────────────────────────────────────────

    private void createOrReplaceLayerStyle(String workspaceName, String layerSlug, int depth) {
        String url = props.url() + "/rest/workspaces/" + workspaceName + "/styles?name=" + layerSlug;
        String sld = buildSldXml(layerSlug, depth);
        try {
            RequestEntity<String> req = RequestEntity
                    .post(URI.create(url))
                    .header("Content-Type", "application/vnd.ogc.sld+xml")
                    .body(sld);
            geoServerRestTemplate.exchange(req, Void.class);
            log.debug("GeoServer SLD style created: {}/{}", workspaceName, layerSlug);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                String putUrl = props.url() + "/rest/workspaces/" + workspaceName + "/styles/" + layerSlug;
                RequestEntity<String> putReq = RequestEntity
                        .put(URI.create(putUrl))
                        .header("Content-Type", "application/vnd.ogc.sld+xml")
                        .body(sld);
                geoServerRestTemplate.exchange(putReq, Void.class);
                log.debug("GeoServer SLD style updated: {}/{}", workspaceName, layerSlug);
            } else {
                log.warn("GeoServer SLD style creation failed [{}]: {}", e.getStatusCode(), e.getMessage());
            }
        }
    }

    private void assignDefaultStyle(String workspaceName, String layerSlug) {
        String url = props.url() + "/rest/workspaces/" + workspaceName + "/layers/" + layerSlug;
        String body = "{\"layer\":{\"defaultStyle\":{\"name\":\"%s\",\"workspace\":\"%s\"}}}"
                .formatted(layerSlug, workspaceName);
        try {
            RequestEntity<String> req = RequestEntity.put(URI.create(url)).body(body);
            geoServerRestTemplate.exchange(req, Void.class);
            log.debug("GeoServer default style assigned: {}/{}", workspaceName, layerSlug);
        } catch (HttpClientErrorException e) {
            log.warn("GeoServer style assignment failed [{}]: {}", e.getStatusCode(), e.getMessage());
        }
    }

    /**
     * Builds an outline-only SLD for a given depth level.
     *
     * All fills are transparent so nested child polygons never obscure their parents.
     * Only colored borders are drawn, with width and color varying by depth so the
     * hierarchy is immediately readable:
     *
     *   depth 0 (Zone/top-level): thick blue-grey border, large bold label
     *   depth 1 (Aisle):          medium blue border, normal label
     *   depth 2 (Bay):            thin green border, small label
     *   depth 3+ (Shelf/leaf):    fine orange border, tiny label (hidden at overview zoom)
     */
    private String buildSldXml(String layerSlug, int depth) {
        String stroke = switch (depth) {
            case 0 -> "#455a64";
            case 1 -> "#1565c0";
            case 2 -> "#2e7d32";
            default -> "#e65100";
        };
        double strokeWidth = switch (depth) {
            case 0 -> 3.0;
            case 1 -> 2.0;
            case 2 -> 1.2;
            default -> 0.6;
        };
        int fontSize = switch (depth) {
            case 0 -> 14;
            case 1 -> 11;
            case 2 -> 9;
            default -> 8;
        };
        String fontWeight = depth == 0 ? "bold" : "normal";
        // Hide leaf-level labels at overview zoom to avoid clutter
        String minScaleDenominator = depth >= 3 ? "<MinScaleDenominator>500</MinScaleDenominator>" : "";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <StyledLayerDescriptor version="1.0.0"
                  xmlns="http://www.opengis.net/sld"
                  xmlns:ogc="http://www.opengis.net/ogc"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd">
                  <NamedLayer>
                    <Name>%s</Name>
                    <UserStyle>
                      <FeatureTypeStyle>
                        <Rule>
                          %s
                          <PolygonSymbolizer>
                            <Fill>
                              <CssParameter name="fill">#000000</CssParameter>
                              <CssParameter name="fill-opacity">0</CssParameter>
                            </Fill>
                            <Stroke>
                              <CssParameter name="stroke">%s</CssParameter>
                              <CssParameter name="stroke-width">%s</CssParameter>
                            </Stroke>
                          </PolygonSymbolizer>
                          <TextSymbolizer>
                            <Label><ogc:PropertyName>label</ogc:PropertyName></Label>
                            <Font>
                              <CssParameter name="font-size">%d</CssParameter>
                              <CssParameter name="font-weight">%s</CssParameter>
                            </Font>
                            <LabelPlacement>
                              <PointPlacement>
                                <AnchorPoint>
                                  <AnchorPointX>0.5</AnchorPointX>
                                  <AnchorPointY>0.5</AnchorPointY>
                                </AnchorPoint>
                              </PointPlacement>
                            </LabelPlacement>
                            <Fill><CssParameter name="fill">#000000</CssParameter></Fill>
                          </TextSymbolizer>
                        </Rule>
                      </FeatureTypeStyle>
                    </UserStyle>
                  </NamedLayer>
                </StyledLayerDescriptor>
                """.formatted(layerSlug, minScaleDenominator, stroke, strokeWidth, fontSize, fontWeight);
    }

    // ─── WMS layer group (floor plan composite) ───────────────────────────────

    /**
     * Creates or replaces the "floorplan" layer group in GeoServer.
     *
     * <p>The group composites all individual SQL View layers into a single WMS
     * endpoint. Layers are ordered by depth ascending so the largest areas
     * (e.g. Zone, depth 0) render at the bottom and the most specific areas
     * (e.g. Shelf, depth 3) render on top.
     *
     * <p>Idempotent: 409 on POST triggers a full PUT to update the layer list.
     */
    private void createOrReplaceLayerGroup(String workspaceName, List<String> layerSlugs) {
        if (layerSlugs.isEmpty()) {
            log.debug("No layers to group — skipping layer group creation");
            return;
        }

        String url  = props.url() + "/rest/workspaces/" + workspaceName + "/layergroups";
        String body = buildLayerGroupJson(workspaceName, layerSlugs);

        try {
            geoServerRestTemplate.postForEntity(url, body, Void.class);
            log.debug("GeoServer layer group created: {}/{}", workspaceName, LAYER_GROUP_NAME);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                String putUrl = url + "/" + LAYER_GROUP_NAME;
                RequestEntity<String> putReq = RequestEntity.put(URI.create(putUrl)).body(body);
                geoServerRestTemplate.exchange(putReq, Void.class);
                log.debug("GeoServer layer group updated: {}/{}", workspaceName, LAYER_GROUP_NAME);
            } else {
                log.warn("GeoServer layer group creation failed [{}]: {}", e.getStatusCode(), e.getMessage());
            }
        }
    }

    private String buildLayerGroupJson(String workspaceName, List<String> layerSlugs) {
        StringBuilder publishables = new StringBuilder();
        StringBuilder styles       = new StringBuilder();

        for (int i = 0; i < layerSlugs.size(); i++) {
            String slug = layerSlugs.get(i);
            if (i > 0) { publishables.append(","); styles.append(","); }
            publishables.append("{\"@type\":\"layer\",\"name\":\"%s:%s\"}".formatted(workspaceName, slug));
            styles.append("{\"name\":\"%s:%s\"}".formatted(workspaceName, slug));
        }

        return """
                {
                  "layerGroup": {
                    "name": "%s",
                    "title": "Warehouse Floor Plan",
                    "mode": "SINGLE",
                    "workspace": {"name": "%s"},
                    "publishables": {
                      "published": [%s]
                    },
                    "styles": {
                      "style": [%s]
                    }
                  }
                }
                """.formatted(LAYER_GROUP_NAME, workspaceName, publishables, styles);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /**
     * Converts a block template name to a valid GeoServer layer slug.
     * e.g. "Cold Storage" → "cold_storage", "Zone-A" → "zone_a"
     */
    static String toLayerSlug(String templateName) {
        return templateName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
