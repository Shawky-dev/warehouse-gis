package com.warehouse.warehouse_platform.tenant.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptLineRepository extends JpaRepository<ReceiptLine, UUID> {

    List<ReceiptLine> findByReceiptIdOrderByPosition(UUID receiptId);

    Optional<ReceiptLine> findByReceiptIdAndId(UUID receiptId, UUID id);

    void deleteByReceiptId(UUID receiptId);

    @Query("select coalesce(max(rl.position), -1) from ReceiptLine rl where rl.receiptId = :receiptId")
    int findMaxPositionByReceiptId(@Param("receiptId") UUID receiptId);
}
