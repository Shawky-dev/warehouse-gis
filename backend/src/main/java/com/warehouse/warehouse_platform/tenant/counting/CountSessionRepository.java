package com.warehouse.warehouse_platform.tenant.counting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface CountSessionRepository
        extends JpaRepository<CountSession, UUID>, JpaSpecificationExecutor<CountSession> {

    Page<CountSession> findByStatus(CountStatus status, Pageable pageable);

    @Query(value = """
            SELECT CASE WHEN EXISTS (
                SELECT 1 FROM count_session_locations csl
                JOIN count_sessions cs ON csl.session_id = cs.id
                WHERE cs.status = 'OPEN'
                  AND csl.location_id IN (:locationIds)
            ) THEN TRUE ELSE FALSE END
            """, nativeQuery = true)
    boolean existsOpenSessionWithAnyLocation(@Param("locationIds") Collection<UUID> locationIds);
}