package com.warehouse.warehouse_platform.auth.session;

import com.warehouse.warehouse_platform.security.jwt.JwtProperties;
import com.warehouse.warehouse_platform.security.jwt.JwtTokenService;
import com.warehouse.warehouse_platform.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenSessionRepository repository;

    @Mock
    private RefreshTokenHasher hasher;

    @Mock
    private JwtTokenService jwtTokenService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "warehouse-platform-api",
                "warehouse-platform-web",
                Duration.ofMinutes(10),
                Duration.ofDays(14),
                "warehouse-k1",
                new ByteArrayResource(new byte[0]),
                new ByteArrayResource(new byte[0]));

        service = new RefreshTokenService(repository, hasher, jwtTokenService, properties);
    }

    @Test
    void issueForLogin_shouldStoreHashedToken_andReturnRawToken() {
        User user = testUser();
        when(jwtTokenService.generateRefreshTokenValue()).thenReturn("raw-refresh");
        when(hasher.hash("raw-refresh")).thenReturn("hash-refresh");
        when(repository.save(any(RefreshTokenSession.class))).thenAnswer(invocation -> {
            RefreshTokenSession session = invocation.getArgument(0);
            session.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            if (session.getIssuedAt() == null) {
                session.setIssuedAt(Instant.now());
            }
            return session;
        });

        RefreshTokenService.RefreshTokenIssueResult result = service.issueForLogin(user, "127.0.0.1", "bruno");

        assertEquals("raw-refresh", result.rawToken());
        assertEquals(user.getId(), result.userId());
        assertEquals(user.getEmail(), result.userEmail());
        assertEquals(user.getRole(), result.userRole());
        assertNotNull(result.tokenFamilyId());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), result.tokenId());

        ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(repository).save(captor.capture());
        assertEquals("hash-refresh", captor.getValue().getTokenHash());
        assertEquals("127.0.0.1", captor.getValue().getCreatedByIp());
    }

    @Test
    void rotate_shouldThrow_whenTokenDoesNotExist() {
        when(hasher.hash("raw-token")).thenReturn("missing-hash");
        when(repository.findByTokenHash("missing-hash")).thenReturn(Optional.empty());

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> service.rotate("raw-token", "127.0.0.1", "bruno"));

        assertEquals("Invalid refresh token", ex.getMessage());
    }

    @Test
    void rotate_shouldThrowAndMarkExpired_whenTokenExpired() {
        RefreshTokenSession expired = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash")
                .tokenFamilyId(UUID.randomUUID())
                .expiresAt(Instant.now().minusSeconds(5))
                .build();

        when(hasher.hash("raw-token")).thenReturn("hash");
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> service.rotate("raw-token", "127.0.0.1", "bruno"));

        assertEquals("Refresh token expired", ex.getMessage());
        assertNotNull(expired.getRevokedAt());
        assertEquals("EXPIRED", expired.getRevocationReason());
        verify(repository, never()).save(any(RefreshTokenSession.class));
    }

    @Test
    void rotate_shouldThrowRevoked_whenRevokedAndNotReplaced() {
        RefreshTokenSession revoked = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash")
                .tokenFamilyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(1000))
                .revokedAt(Instant.now())
                .revocationReason("LOGOUT")
                .build();

        when(hasher.hash("raw-token")).thenReturn("hash");
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> service.rotate("raw-token", "127.0.0.1", "bruno"));

        assertEquals("Refresh token revoked", ex.getMessage());
        verify(repository, never()).findAllByTokenFamilyId(any(UUID.class));
    }

    @Test
    void rotate_shouldDetectReuse_andRevokeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshTokenSession reused = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .revokedAt(Instant.now().minusSeconds(30))
                .revocationReason("ROTATED")
                .replacedByTokenId(UUID.randomUUID())
                .build();

        RefreshTokenSession activeSibling = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash2")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .build();

        when(hasher.hash("reused-raw")).thenReturn("hash");
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(reused));
        when(repository.findAllByTokenFamilyId(familyId)).thenReturn(List.of(reused, activeSibling));

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> service.rotate("reused-raw", "127.0.0.1", "bruno"));

        assertEquals("Refresh token reuse detected", ex.getMessage());
        assertNotNull(reused.getReuseDetectedAt());
        assertNotNull(activeSibling.getRevokedAt());
        assertEquals("REUSE_DETECTED", activeSibling.getRevocationReason());
        assertNotNull(activeSibling.getReuseDetectedAt());

        verify(repository).saveAll(any(List.class));
    }

    @Test
    void rotate_shouldCreateNewToken_andRevokeCurrent() {
        User user = testUser();
        UUID familyId = UUID.randomUUID();

        RefreshTokenSession current = RefreshTokenSession.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .user(user)
                .tokenHash("current-hash")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .build();

        when(hasher.hash("current-raw")).thenReturn("current-hash");
        when(repository.findByTokenHash("current-hash")).thenReturn(Optional.of(current));
        when(jwtTokenService.generateRefreshTokenValue()).thenReturn("next-raw");
        when(hasher.hash("next-raw")).thenReturn("next-hash");
        when(repository.save(any(RefreshTokenSession.class))).thenAnswer(invocation -> {
            RefreshTokenSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            }
            return session;
        });

        RefreshTokenService.RefreshTokenIssueResult result = service.rotate("current-raw", "127.0.0.1", "bruno");

        assertEquals("next-raw", result.rawToken());
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), result.tokenId());
        assertEquals(user.getId(), result.userId());
        assertEquals(user.getEmail(), result.userEmail());

        assertNotNull(current.getRevokedAt());
        assertNotNull(current.getRotatedAt());
        assertNotNull(current.getLastUsedAt());
        assertEquals("ROTATED", current.getRevocationReason());
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), current.getReplacedByTokenId());
    }

    @Test
    void revokeFamilyByToken_shouldThrow_whenTokenInvalid() {
        when(hasher.hash("raw")).thenReturn("hash");
        when(repository.findByTokenHash("hash")).thenReturn(Optional.empty());

        RefreshTokenException ex = assertThrows(
                RefreshTokenException.class,
                () -> service.revokeFamilyByToken("raw", "LOGOUT"));

        assertEquals("Invalid refresh token", ex.getMessage());
    }

    @Test
    void revokeFamilyByToken_shouldRevokeAllActiveInFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshTokenSession trigger = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .build();

        RefreshTokenSession active = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("active")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .build();

        RefreshTokenSession alreadyRevoked = RefreshTokenSession.builder()
                .id(UUID.randomUUID())
                .tokenHash("revoked")
                .tokenFamilyId(familyId)
                .expiresAt(Instant.now().plusSeconds(1000))
                .revokedAt(Instant.now().minusSeconds(5))
                .revocationReason("LOGOUT")
                .build();

        when(hasher.hash("raw")).thenReturn("hash");
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(trigger));
        when(repository.findAllByTokenFamilyId(familyId)).thenReturn(List.of(trigger, active, alreadyRevoked));

        service.revokeFamilyByToken("raw", "LOGOUT");

        assertTrue(trigger.isRevoked());
        assertTrue(active.isRevoked());
        assertEquals("LOGOUT", active.getRevocationReason());
        assertEquals("LOGOUT", trigger.getRevocationReason());
        assertEquals("LOGOUT", alreadyRevoked.getRevocationReason());

        verify(repository).saveAll(any(List.class));
    }

    private User testUser() {
        User user = new User();
        user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        user.setEmail("admin@system.local");
        user.setRole("ADMIN");
        return user;
    }
}
