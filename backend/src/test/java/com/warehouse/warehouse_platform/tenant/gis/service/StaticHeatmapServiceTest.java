package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GeoServerProvisioningException;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.PublishStatus;
import com.warehouse.warehouse_platform.tenant.gis.model.StaticHeatmap;
import com.warehouse.warehouse_platform.tenant.gis.repository.StaticHeatmapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaticHeatmapServiceTest {

    @Mock
    StaticHeatmapRepository repo;
    @Mock
    GeoServerProvisioningService geoServer;
    @Mock
    PlatformTransactionManager txManager;

    StaticHeatmapService service;

    static final String TENANT = "tenant1";
    static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        // Make TransactionTemplate execute callbacks immediately without real
        // transactions.
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);
        doAnswer(inv -> null).when(txManager).commit(txStatus);

        service = new StaticHeatmapService(repo, geoServer, txManager);
    }

    // ─── upload: validation ───────────────────────────────────────────────────

    @Test
    void upload_shouldReject_nonTiffExtension() {
        var file = new MockMultipartFile("file", "floor.png", "image/png", new byte[] { 1, 2, 3 });
        assertThrows(GisException.class, () -> service.upload(TENANT, "Test", file, "user1"));
    }

    @Test
    void upload_shouldReject_disallowedContentType() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/png", new byte[] { 1, 2, 3 });
        assertThrows(GisException.class, () -> service.upload(TENANT, "Test", file, "user1"));
    }

    @Test
    void upload_shouldReject_emptyFile() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/tiff", new byte[0]);
        assertThrows(GisException.class, () -> service.upload(TENANT, "Test", file, "user1"));
    }

    @Test
    void upload_shouldSetDefault_forFirstHeatmap() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/tiff", new byte[] { 1, 2, 3 });
        when(repo.countByPublishStatus(PublishStatus.ACTIVE)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StaticHeatmap saved = service.upload(TENANT, "Test", file, "user1");

        assertTrue(saved.isDefault(), "First upload must be set as default");
        verify(geoServer).ensureTenantWorkspace(TENANT);
        verify(geoServer).uploadGeoTiffCoverageStore(any(), any(), any());
    }

    @Test
    void upload_shouldNotSetDefault_whenActiveHeatmapsAlreadyExist() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/tiff", new byte[] { 1, 2, 3 });
        when(repo.countByPublishStatus(PublishStatus.ACTIVE)).thenReturn(2L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StaticHeatmap saved = service.upload(TENANT, "Test", file, "user1");

        assertFalse(saved.isDefault(), "Subsequent upload must not be set as default");
    }

    @Test
    void upload_shouldAttemptGeoServerCleanup_whenDbFails() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/tiff", new byte[] { 1, 2, 3 });
        when(repo.countByPublishStatus(any())).thenReturn(0L);
        when(repo.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(GisException.class, () -> service.upload(TENANT, "Test", file, "user1"));

        verify(geoServer).uploadGeoTiffCoverageStore(any(), any(), any());
        verify(geoServer).deleteRasterCoverageStore(any(), any());
    }

    @Test
    void upload_shouldRejectGeoServerFailure_withoutDbSave() {
        var file = new MockMultipartFile("file", "floor.tiff", "image/tiff", new byte[] { 1, 2, 3 });
        doThrow(GeoServerProvisioningException.serverError("GeoServer unavailable"))
                .when(geoServer).uploadGeoTiffCoverageStore(any(), any(), any());

        assertThrows(GeoServerProvisioningException.class,
                () -> service.upload(TENANT, "Test", file, "user1"));

        verify(repo, never()).save(any());
    }

    // ─── setDefault ───────────────────────────────────────────────────────────

    @Test
    void setDefault_shouldThrow_whenHeatmapNotFound() {
        when(repo.findById(ID)).thenReturn(Optional.empty());
        assertThrows(GisException.class, () -> service.setDefault(ID));
    }

    @Test
    void setDefault_shouldThrow_whenHeatmapIsOrphaned() {
        StaticHeatmap orphaned = activeHeatmap(ID, false);
        orphaned.setPublishStatus(PublishStatus.ORPHANED);
        when(repo.findById(ID)).thenReturn(Optional.of(orphaned));
        assertThrows(GisException.class, () -> service.setDefault(ID));
    }

    @Test
    void setDefault_shouldClearPreviousDefaultAndSetNew() {
        StaticHeatmap heatmap = activeHeatmap(ID, false);
        when(repo.findById(ID)).thenReturn(Optional.of(heatmap));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StaticHeatmap result = service.setDefault(ID);

        verify(repo).clearAllDefaults(PublishStatus.ACTIVE);
        assertTrue(result.isDefault());
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_shouldThrow_whenHeatmapNotFound() {
        when(repo.findById(ID)).thenReturn(Optional.empty());
        assertThrows(GisException.class, () -> service.delete(TENANT, ID));
    }

    @Test
    void delete_shouldThrow_whenHeatmapIsOrphaned() {
        StaticHeatmap orphaned = activeHeatmap(ID, false);
        orphaned.setPublishStatus(PublishStatus.ORPHANED);
        when(repo.findById(ID)).thenReturn(Optional.of(orphaned));
        assertThrows(GisException.class, () -> service.delete(TENANT, ID));
    }

    @Test
    void delete_shouldPromoteNewestActive_whenDeletingDefault() {
        UUID replacementId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        StaticHeatmap heatmap = activeHeatmap(ID, true);
        StaticHeatmap replacement = activeHeatmap(replacementId, false);
        when(repo.findById(ID)).thenReturn(Optional.of(heatmap));
        when(repo.findTop1ByPublishStatusAndIdNotOrderByCreatedAtDesc(PublishStatus.ACTIVE, ID))
                .thenReturn(Optional.of(replacement));
        when(repo.findById(replacementId)).thenReturn(Optional.of(replacement));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(TENANT, ID);

        verify(geoServer).deleteRasterCoverageStore(TENANT, heatmap.getGeoserverCoverageStore());
        verify(repo).deleteById(ID);
        ArgumentCaptor<StaticHeatmap> captor = ArgumentCaptor.forClass(StaticHeatmap.class);
        verify(repo).save(captor.capture());
        assertTrue(captor.getValue().isDefault(), "Replacement must be promoted to default");
    }

    @Test
    void delete_shouldNotPromote_whenDeletedWasNotDefault() {
        StaticHeatmap heatmap = activeHeatmap(ID, false);
        when(repo.findById(ID)).thenReturn(Optional.of(heatmap));

        service.delete(TENANT, ID);

        verify(repo).deleteById(ID);
        verify(repo, never()).findTop1ByPublishStatusAndIdNotOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void delete_shouldMarkOrphaned_whenGeoServerSucceedsButDbFails() {
        StaticHeatmap heatmap = activeHeatmap(ID, false);
        when(repo.findById(ID)).thenReturn(Optional.of(heatmap));
        doThrow(new RuntimeException("DB delete failed")).when(repo).deleteById(ID);

        // Second findById for compensation
        when(repo.findById(ID)).thenReturn(Optional.of(heatmap));

        assertThrows(GisException.class, () -> service.delete(TENANT, ID));

        verify(geoServer).deleteRasterCoverageStore(TENANT, heatmap.getGeoserverCoverageStore());
        // Compensation: row should be marked ORPHANED
        ArgumentCaptor<StaticHeatmap> captor = ArgumentCaptor.forClass(StaticHeatmap.class);
        verify(repo).save(captor.capture());
        assertEquals(PublishStatus.ORPHANED, captor.getValue().getPublishStatus());
        assertFalse(captor.getValue().isDefault());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private StaticHeatmap activeHeatmap(UUID id, boolean isDefault) {
        return StaticHeatmap.builder()
                .id(id)
                .name("Test Heatmap")
                .sourceFilename("test.tiff")
                .contentType("image/tiff")
                .geoserverCoverageStore("heatmap_static_" + id.toString().substring(0, 8))
                .geoserverLayerName("heatmap_static_" + id.toString().substring(0, 8))
                .publishStatus(PublishStatus.ACTIVE)
                .isDefault(isDefault)
                .uploadedBy("testuser")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
