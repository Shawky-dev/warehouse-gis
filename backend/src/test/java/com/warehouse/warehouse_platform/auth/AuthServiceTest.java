package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.security.jwt.JwtTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_shouldAuthenticateIssueTokens_andReturnContract() {
        LoginRequest request = new LoginRequest("admin@system.local", "admin123");
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "admin@system.local",
                null,
                java.util.List.of());

        User user = testUser();
        Instant accessExp = Instant.now().plusSeconds(600);
        Instant refreshExp = Instant.now().plusSeconds(1200);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(userRepository.findByEmail("admin@system.local")).thenReturn(Optional.of(user));
        when(jwtTokenService.createAccessToken(authenticated))
                .thenReturn(new JwtTokenService.AccessTokenResult("access-token", accessExp));
        when(refreshTokenService.issueForLogin(user, "127.0.0.1", "bruno"))
                .thenReturn(new RefreshTokenService.RefreshTokenIssueResult(
                        "refresh-token",
                        refreshExp,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        user.getId(),
                        user.getEmail(),
                        user.getRole()));

        AuthService.AuthResult result = authService.login(request, "127.0.0.1", "bruno");

        assertEquals("access-token", result.accessToken());
        assertEquals(accessExp, result.accessTokenExpiresAt());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(refreshExp, result.refreshTokenExpiresAt());
        assertEquals(user.getId(), result.userId());
        assertEquals(user.getEmail(), result.email());
        assertEquals(user.getRole(), result.role());

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertEquals("admin@system.local", authCaptor.getValue().getName());
        assertEquals("admin123", authCaptor.getValue().getCredentials());
    }

    @Test
    void login_shouldThrow_whenAuthenticatedUserMissingInDatabase() {
        LoginRequest request = new LoginRequest("missing@system.local", "pass");
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "missing@system.local",
                null,
                java.util.List.of());

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(userRepository.findByEmail("missing@system.local")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> authService.login(request, "127.0.0.1", "bruno"));

        assertEquals("Authenticated user not found", ex.getMessage());
    }

    @Test
    void refresh_shouldRotateRefreshToken_andIssueAccessTokenWithRoleAuthority() {
        User user = testUser();
        Instant refreshExp = Instant.now().plusSeconds(1000);
        Instant accessExp = Instant.now().plusSeconds(500);

        when(refreshTokenService.rotate("raw-refresh", "127.0.0.1", "bruno"))
                .thenReturn(new RefreshTokenService.RefreshTokenIssueResult(
                        "new-refresh",
                        refreshExp,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        user.getId(),
                        user.getEmail(),
                        user.getRole()));

        when(jwtTokenService.createAccessToken(any(Authentication.class)))
                .thenReturn(new JwtTokenService.AccessTokenResult("new-access", accessExp));

        AuthService.AuthResult result = authService.refresh("raw-refresh", "127.0.0.1", "bruno");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        assertEquals(user.getRole(), result.role());

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(jwtTokenService).createAccessToken(authCaptor.capture());
        assertEquals(user.getEmail(), authCaptor.getValue().getName());
        assertEquals("ROLE_ADMIN", authCaptor.getValue().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void logout_shouldDelegateFamilyRevoke() {
        authService.logout("raw-refresh");

        verify(refreshTokenService).revokeFamilyByToken("raw-refresh", "LOGOUT");
    }

    private User testUser() {
        User user = new User();
        user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        user.setEmail("admin@system.local");
        user.setRole("ADMIN");
        return user;
    }
}
