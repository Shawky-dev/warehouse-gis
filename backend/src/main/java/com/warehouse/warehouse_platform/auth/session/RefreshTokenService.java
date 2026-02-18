package com.warehouse.warehouse_platform.auth.session;

import com.warehouse.warehouse_platform.security.jwt.JwtProperties;
import com.warehouse.warehouse_platform.security.jwt.JwtTokenService;
import com.warehouse.warehouse_platform.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String REASON_ROTATED = "ROTATED";
    private static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    private static final String REASON_EXPIRED = "EXPIRED";

    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenSessionRepository refreshTokenSessionRepository,
            RefreshTokenHasher refreshTokenHasher,
            JwtTokenService jwtTokenService,
            JwtProperties jwtProperties) {
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.refreshTokenHasher = refreshTokenHasher;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public RefreshTokenIssueResult issueForLogin(User user, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        String rawToken = jwtTokenService.generateRefreshTokenValue();
        String tokenHash = refreshTokenHasher.hash(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshTokenSession session = RefreshTokenSession.builder()
                .user(user)
                .tokenHash(tokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(now.plus(jwtProperties.refreshTokenTtl()))
                .createdByIp(ipAddress)
                .createdByUserAgent(userAgent)
                .build();

        refreshTokenSessionRepository.save(session);
        return new RefreshTokenIssueResult(
                rawToken,
                session.getExpiresAt(),
                familyId,
                session.getId(),
                user.getId(),
                user.getEmail(),
                user.getRole());
    }

    @Transactional
    public RefreshTokenIssueResult rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);

        RefreshTokenSession current = refreshTokenSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        if (current.isExpired(now)) {
            revokeToken(current, REASON_EXPIRED, now);
            throw new RefreshTokenException("Refresh token expired");
        }

        if (current.isRevoked()) {
            if (current.getReplacedByTokenId() != null) {
                revokeFamily(current.getTokenFamilyId(), REASON_REUSE_DETECTED, now);
                markReuseDetected(current, now);
                throw new RefreshTokenException("Refresh token reuse detected");
            }
            throw new RefreshTokenException("Refresh token revoked");
        }

        String nextRawToken = jwtTokenService.generateRefreshTokenValue();
        String nextTokenHash = refreshTokenHasher.hash(nextRawToken);

        RefreshTokenSession rotated = RefreshTokenSession.builder()
                .user(current.getUser())
                .tokenHash(nextTokenHash)
                .tokenFamilyId(current.getTokenFamilyId())
                .parentTokenId(current.getId())
                .expiresAt(now.plus(jwtProperties.refreshTokenTtl()))
                .createdByIp(ipAddress)
                .createdByUserAgent(userAgent)
                .build();

        refreshTokenSessionRepository.save(rotated);

        current.setLastUsedAt(now);
        current.setRotatedAt(now);
        current.setRevokedAt(now);
        current.setRevocationReason(REASON_ROTATED);
        current.setReplacedByTokenId(rotated.getId());
        refreshTokenSessionRepository.save(current);

        return new RefreshTokenIssueResult(
                nextRawToken,
                rotated.getExpiresAt(),
                rotated.getTokenFamilyId(),
                rotated.getId(),
                current.getUser().getId(),
                current.getUser().getEmail(),
                current.getUser().getRole());
    }

    @Transactional
    public void revokeFamilyByToken(String rawRefreshToken, String reason) {
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshTokenSession token = refreshTokenSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        revokeFamily(token.getTokenFamilyId(), reason, Instant.now());
    }

    private void revokeFamily(UUID tokenFamilyId, String reason, Instant now) {
        List<RefreshTokenSession> family = refreshTokenSessionRepository.findAllByTokenFamilyId(tokenFamilyId);
        for (RefreshTokenSession token : family) {
            if (!token.isRevoked()) {
                revokeToken(token, reason, now);
            }
            if (REASON_REUSE_DETECTED.equals(reason) && token.getReuseDetectedAt() == null) {
                token.setReuseDetectedAt(now);
            }
        }
        refreshTokenSessionRepository.saveAll(family);
    }

    private void markReuseDetected(RefreshTokenSession token, Instant now) {
        if (token.getReuseDetectedAt() == null) {
            token.setReuseDetectedAt(now);
            refreshTokenSessionRepository.save(token);
        }
    }

    private static void revokeToken(RefreshTokenSession token, String reason, Instant now) {
        if (!token.isRevoked()) {
            token.setRevokedAt(now);
            token.setRevocationReason(reason);
        }
    }

    public record RefreshTokenIssueResult(
            String rawToken,
            Instant expiresAt,
            UUID tokenFamilyId,
            UUID tokenId,
            UUID userId,
            String userEmail,
            String userRole) {
    }
}
