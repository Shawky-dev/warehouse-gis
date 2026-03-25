package com.warehouse.warehouse_platform.tenant.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchLineRepository extends JpaRepository<DispatchLine, UUID> {

    List<DispatchLine> findByDispatchIdOrderByPosition(UUID dispatchId);

    Optional<DispatchLine> findByDispatchIdAndId(UUID dispatchId, UUID id);

    void deleteByDispatchId(UUID dispatchId);

    @Query("select coalesce(max(dl.position), -1) from DispatchLine dl where dl.dispatchId = :dispatchId")
    int findMaxPositionByDispatchId(@Param("dispatchId") UUID dispatchId);

    boolean existsBySourceLocationIdIn(Collection<UUID> locationIds);
}
