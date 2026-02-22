package com.warehouse.warehouse_platform.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    Optional<RefreshTokenSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    List<RefreshTokenSession> findAllByTokenFamilyId(UUID tokenFamilyId);

    List<RefreshTokenSession> findAllByUser_IdAndRevokedAtIsNull(UUID userId);
}
