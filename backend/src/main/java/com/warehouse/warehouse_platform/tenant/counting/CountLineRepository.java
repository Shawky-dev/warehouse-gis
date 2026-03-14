package com.warehouse.warehouse_platform.tenant.counting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountLineRepository extends JpaRepository<CountLine, UUID> {

    List<CountLine> findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(UUID sessionId);

    Optional<CountLine> findBySessionIdAndId(UUID sessionId, UUID id);

    long countBySessionId(UUID sessionId);
}