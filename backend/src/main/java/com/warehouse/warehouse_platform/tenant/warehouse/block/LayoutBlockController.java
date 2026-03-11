package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/warehouse-layouts/{layoutId}/blocks")
@Validated
public class LayoutBlockController {

        private final TenantAccessPolicy tenantAccessPolicy;
        private final LayoutBlockService layoutBlockService;

        public LayoutBlockController(
                        TenantAccessPolicy tenantAccessPolicy,
                        LayoutBlockService layoutBlockService) {
                this.tenantAccessPolicy = tenantAccessPolicy;
                this.layoutBlockService = layoutBlockService;
        }

        @GetMapping
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
        public ResponseEntity<List<LayoutBlockService.BlockNode>> getTree(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.getTree(layoutId));
        }

        @GetMapping("/{blockId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)")
        public ResponseEntity<LayoutBlockService.BlockResult> getBlock(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @PathVariable UUID blockId,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.getBlock(layoutId, blockId));
        }

        @PostMapping
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BlockResult> addBlock(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @Valid @RequestBody AddBlockRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.addBlock(
                                layoutId, request.blockTemplateId(), request.parentId(), request.position(),
                                request.side()));
        }

        @PostMapping("/batch")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BatchBlockResult> addBlocks(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @Valid @RequestBody AddBlocksRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.addBlocks(
                                layoutId,
                                request.blockTemplateId(),
                                request.parentId(),
                                request.position(),
                                request.count(),
                                request.side()));
        }

        @PostMapping("/copy-subtree")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BatchBlockResult> copySubtree(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @Valid @RequestBody CopySubtreeRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.copySubtree(
                                layoutId,
                                request.sourceBlockId(),
                                request.targetParentId(),
                                request.position(),
                                request.copies()));
        }

        @PutMapping("/{blockId}/move")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BlockResult> moveBlock(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @PathVariable UUID blockId,
                        @Valid @RequestBody MoveBlockRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.moveBlock(
                                layoutId, blockId, request.parentId(), request.position()));
        }

        @PutMapping("/{blockId}/template")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BlockResult> reassignTemplate(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @PathVariable UUID blockId,
                        @Valid @RequestBody ReassignTemplateRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity
                                .ok(layoutBlockService.reassignTemplate(layoutId, blockId, request.blockTemplateId()));
        }

        @PutMapping("/{blockId}/metadata")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)")
        public ResponseEntity<LayoutBlockService.BlockResult> updateMetadata(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @PathVariable UUID blockId,
                        @Valid @RequestBody UpdateBlockMetadataRequest request,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                return ResponseEntity.ok(layoutBlockService.updateMetadata(layoutId, blockId, request.side()));
        }

        @DeleteMapping("/{blockId}")
        @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)")
        public ResponseEntity<Void> removeBlock(
                        @PathVariable String tenantSlug,
                        @PathVariable UUID layoutId,
                        @PathVariable UUID blockId,
                        Authentication authentication) {
                tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
                layoutBlockService.removeBlock(layoutId, blockId);
                return ResponseEntity.noContent().build();
        }

        public record AddBlockRequest(
                        @NotNull UUID blockTemplateId,
                        UUID parentId,
                        @Min(0) Integer position,
                        @Size(max = 50) String side) {
        }

        public record AddBlocksRequest(
                        @NotNull UUID blockTemplateId,
                        UUID parentId,
                        @Min(0) Integer position,
                        @Min(1) int count,
                        @Size(max = 50) String side) {
        }

        public record CopySubtreeRequest(
                        @NotNull UUID sourceBlockId,
                        UUID targetParentId,
                        @Min(0) Integer position,
                        @Min(1) int copies) {
        }

        public record MoveBlockRequest(
                        UUID parentId,
                        @Min(0) int position) {
        }

        public record ReassignTemplateRequest(
                        @NotNull UUID blockTemplateId) {
        }

        public record UpdateBlockMetadataRequest(
                        @Size(max = 50) String side) {
        }
}
