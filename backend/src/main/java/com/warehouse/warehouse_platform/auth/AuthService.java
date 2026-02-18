package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.security.jwt.JwtTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                );

        Authentication authentication = authenticationManager.authenticate(authToken);

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        JwtTokenService.AccessTokenResult accessToken = jwtTokenService.createAccessToken(authentication);
        RefreshTokenService.RefreshTokenIssueResult refreshToken = refreshTokenService.issueForLogin(
                user,
                ipAddress,
                userAgent);

        return new AuthResult(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.rawToken(),
                refreshToken.expiresAt(),
                user.getId(),
                user.getEmail(),
                user.getRole());
    }

    public AuthResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshTokenService.RefreshTokenIssueResult rotatedToken = refreshTokenService.rotate(
                rawRefreshToken,
                ipAddress,
                userAgent);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                rotatedToken.userEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rotatedToken.userRole())));

        JwtTokenService.AccessTokenResult accessToken = jwtTokenService.createAccessToken(authentication);

        return new AuthResult(
                accessToken.token(),
                accessToken.expiresAt(),
                rotatedToken.rawToken(),
                rotatedToken.expiresAt(),
                rotatedToken.userId(),
                rotatedToken.userEmail(),
                rotatedToken.userRole());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamilyByToken(rawRefreshToken, "LOGOUT");
    }

    public record AuthResult(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UUID userId,
            String email,
            String role) {
    }
}
