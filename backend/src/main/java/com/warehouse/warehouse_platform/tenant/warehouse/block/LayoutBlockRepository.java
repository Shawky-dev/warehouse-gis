package com.warehouse.warehouse_platform.tenant.warehouse.block;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LayoutBlockRepository extends JpaRepository<LayoutBlock, UUID> {

    List<LayoutBlock> findByLayoutIdOrderByParentIdAscPositionAsc(UUID layoutId);

    List<LayoutBlock> findByLayoutIdAndParentIdIsNullOrderByPositionAsc(UUID layoutId);

    List<LayoutBlock> findByLayoutIdAndParentIdOrderByPositionAsc(UUID layoutId, UUID parentId);

    long countByLayoutId(UUID layoutId);

    long countByBlockTemplateId(UUID blockTemplateId);

    boolean existsByLayoutIdAndParentIdAndPosition(UUID layoutId, UUID parentId, int position);

    boolean existsByLayoutIdAndParentIdIsNullAndPosition(UUID layoutId, int position);

    @Query("SELECT MAX(lb.position) FROM LayoutBlock lb WHERE lb.layoutId = :layoutId AND lb.parentId IS NULL")
    Integer findMaxRootPosition(@Param("layoutId") UUID layoutId);

    @Query("SELECT MAX(lb.position) FROM LayoutBlock lb WHERE lb.layoutId = :layoutId AND lb.parentId = :parentId")
    Integer findMaxChildPosition(@Param("layoutId") UUID layoutId, @Param("parentId") UUID parentId);

    @Modifying
    @Query("UPDATE LayoutBlock lb SET lb.position = lb.position + :delta " +
           "WHERE lb.layoutId = :layoutId AND lb.parentId IS NULL " +
           "AND lb.position >= :fromPosition AND lb.id <> :excludeId")
    void shiftRootPositions(@Param("layoutId") UUID layoutId,
                            @Param("fromPosition") int fromPosition,
                            @Param("delta") int delta,
                            @Param("excludeId") UUID excludeId);

    @Modifying
    @Query("UPDATE LayoutBlock lb SET lb.position = lb.position + :delta " +
           "WHERE lb.layoutId = :layoutId AND lb.parentId = :parentId " +
           "AND lb.position >= :fromPosition AND lb.id <> :excludeId")
    void shiftChildPositions(@Param("layoutId") UUID layoutId,
                             @Param("parentId") UUID parentId,
                             @Param("fromPosition") int fromPosition,
                             @Param("delta") int delta,
                             @Param("excludeId") UUID excludeId);
}
