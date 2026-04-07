package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.multi_tenancy.geoserver.GeoServerProperties;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.PublishStatus;
import com.warehouse.warehouse_platform.tenant.gis.model.StaticHeatmap;
import com.warehouse.warehouse_platform.tenant.gis.repository.StaticHeatmapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class WmsProxyControllerTest {
    private static Class<byte[]> byteArrayType() {
        return byte[].class;
    }

    @Mock
    TenantAccessPolicy tenantAccessPolicy;
    @Mock
    StaticHeatmapRepository heatmapRepository;
    @Mock
    RestTemplate geoServerRestTemplate;

    // GeoServerProperties is a record (final) – instantiate directly.
    GeoServerProperties geoServerProperties = new GeoServerProperties(
            "http://geoserver:8080/geoserver", "admin", "geoserver",
            "db", 5432, "warehouse", "user", "pass");

    WmsProxyController controller;

    Authentication auth = mock(Authentication.class);

    static final String TENANT = "tenant1";
    static final String ACTIVE_LAYER = "heatmap_static_abc12345";
    static final UUID HEATMAP_ID = UUID.fromString("abc12345-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        controller = new WmsProxyController(
                tenantAccessPolicy, heatmapRepository, geoServerProperties, geoServerRestTemplate);
    }

    // ─── SERVICE validation ───────────────────────────────────────────────────

    @Test
    void proxy_shouldReject_nonWmsService() {
        Map<String, String> params = Map.of("SERVICE", "WFS", "REQUEST", "GetCapabilities");
        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    @Test
    void proxy_shouldReject_missingService() {
        Map<String, String> params = Map.of("REQUEST", "GetCapabilities");
        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    // ─── REQUEST validation ───────────────────────────────────────────────────

    @Test
    void proxy_shouldReject_disallowedRequest() {
        Map<String, String> params = Map.of("SERVICE", "WMS", "REQUEST", "GetFeatureInfo");
        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    // ─── Layer validation for GetMap ──────────────────────────────────────────

    @Test
    void proxy_shouldReject_unknownLayer() {
        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetMap",
                "LAYERS", "heatmap_static_xxxunknown");

        when(heatmapRepository.findByGeoserverLayerNameAndPublishStatus("heatmap_static_xxxunknown",
                PublishStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    @Test
    void proxy_shouldReject_crossTenantLayer() {
        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetMap",
                "LAYERS", "wh_othertenant:heatmap_static_abc12345");

        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    @Test
    void proxy_shouldAccept_workspacePrefixedActiveLayer() {
        StaticHeatmap heatmap = buildActiveHeatmap();
        when(heatmapRepository.findByGeoserverLayerNameAndPublishStatus(ACTIVE_LAYER, PublishStatus.ACTIVE))
                .thenReturn(Optional.of(heatmap));

        // Mock GeoServer response
        var mockResponse = ResponseEntity.ok().body(new byte[] { 1, 2, 3 });
        when(geoServerRestTemplate.getForEntity(anyString(), eq(byteArrayType()))).thenReturn(mockResponse);

        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetMap",
                "LAYERS", "wh_tenant1:" + ACTIVE_LAYER);

        ResponseEntity<byte[]> response = controller.proxy(TENANT, params, auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void proxy_shouldForwardGetCapabilities_withoutLayerCheck() {
        var mockResponse = ResponseEntity.ok().body("<Capabilities/>".getBytes());
        when(geoServerRestTemplate.getForEntity(anyString(), eq(byteArrayType()))).thenReturn(mockResponse);

        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetCapabilities");

        ResponseEntity<byte[]> response = controller.proxy(TENANT, params, auth);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void proxy_shouldReturnBadGateway_whenGeoServerIsUnreachable() {
        when(geoServerRestTemplate.getForEntity(anyString(), eq(byteArrayType())))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetCapabilities");

        GisException exception = assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
        assertEquals(502, exception.getStatus().value());
        assertSame("BAD_GATEWAY", exception.getCode());
    }

    @Test
    void proxy_shouldAccept_caseInsensitiveServiceParam() {
        var mockResponse = ResponseEntity.ok().body(new byte[0]);
        when(geoServerRestTemplate.getForEntity(anyString(), eq(byteArrayType()))).thenReturn(mockResponse);

        Map<String, String> params = Map.of(
                "SERVICE", "wms",
                "REQUEST", "GetCapabilities");

        ResponseEntity<byte[]> response = controller.proxy(TENANT, params, auth);
        assertNotNull(response);
    }

    // ─── GetLegendGraphic ─────────────────────────────────────────────────────

    @Test
    void proxy_shouldValidateSingleLayer_forGetLegendGraphic() {
        Map<String, String> params = Map.of(
                "SERVICE", "WMS",
                "REQUEST", "GetLegendGraphic",
                "LAYER", "heatmap_static_notfound");

        when(heatmapRepository.findByGeoserverLayerNameAndPublishStatus("heatmap_static_notfound",
                PublishStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(GisException.class, () -> controller.proxy(TENANT, params, auth));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private StaticHeatmap buildActiveHeatmap() {
        return StaticHeatmap.builder()
                .id(HEATMAP_ID)
                .name("Test")
                .sourceFilename("test.tiff")
                .contentType("image/tiff")
                .geoserverCoverageStore(ACTIVE_LAYER)
                .geoserverLayerName(ACTIVE_LAYER)
                .publishStatus(PublishStatus.ACTIVE)
                .isDefault(true)
                .uploadedBy("user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
