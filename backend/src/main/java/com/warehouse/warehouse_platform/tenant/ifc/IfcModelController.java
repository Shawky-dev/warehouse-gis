package com.warehouse.warehouse_platform.tenant.ifc;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/ifc/models")
public class IfcModelController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final IfcModelService ifcModelService;

    public IfcModelController(TenantAccessPolicy tenantAccessPolicy, IfcModelService ifcModelService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.ifcModelService = ifcModelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).IFC_VIEW)")
    public ResponseEntity<List<IfcModelService.ModelSummary>> listAll(
            @PathVariable String tenantSlug,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(ifcModelService.listAll());
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).IFC_VIEW)")
    public ResponseEntity<StreamingResponseBody> serveFile(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        IfcModelService.FileRef fileRef = ifcModelService.resolveFile(id, tenantSlug);

        StreamingResponseBody body = outputStream -> {
            try {
                Files.copy(fileRef.path(), outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to stream IFC file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileRef.originalName() + "\"")
                .body(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).IFC_MANAGE)")
    public ResponseEntity<IfcModelService.ModelSummary> upload(
            @PathVariable String tenantSlug,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(ifcModelService.upload(tenantSlug, file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).IFC_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        ifcModelService.delete(id, tenantSlug);
        return ResponseEntity.noContent().build();
    }
}
