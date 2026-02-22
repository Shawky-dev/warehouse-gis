package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenService;
import com.warehouse.warehouse_platform.security.RoleAuthorityResolver;
import com.warehouse.warehouse_platform.security.jwt.JwtTokenService;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final RoleAuthorityResolver roleAuthorityResolver;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            RoleAuthorityResolver roleAuthorityResolver) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.roleAuthorityResolver = roleAuthorityResolver;
    }

    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        normalizeEmail(request.email()),
                        request.password()
                );

        Authentication authentication = authenticationManager.authenticate(authToken);

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        List<GrantedAuthority> grantedAuthorities = ensureAuthorities(authentication, user.getRole());
        Authentication tokenAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                authentication.getName(),
                null,
                grantedAuthorities);

        JwtTokenService.AccessTokenResult accessToken = jwtTokenService.createAccessToken(tokenAuthentication);
        RefreshTokenService.RefreshTokenIssueResult refreshToken = refreshTokenService.issueForLogin(
                user,
                ipAddress,
                userAgent);
        RoleAuthorityResolver.AuthoritySnapshot authoritySnapshot =
                roleAuthorityResolver.splitAuthorities(grantedAuthorities);

        return new AuthResult(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.rawToken(),
                refreshToken.expiresAt(),
                user.getId(),
                user.getEmail(),
                authoritySnapshot.roles(),
                authoritySnapshot.permissions());
    }

    public AuthResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshTokenService.RefreshTokenIssueResult rotatedToken = refreshTokenService.rotate(
                rawRefreshToken,
                ipAddress,
                userAgent);

        List<GrantedAuthority> grantedAuthorities = roleAuthorityResolver.resolveAuthorities(rotatedToken.userRole());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                rotatedToken.userEmail(),
                null,
                grantedAuthorities);

        JwtTokenService.AccessTokenResult accessToken = jwtTokenService.createAccessToken(authentication);
        RoleAuthorityResolver.AuthoritySnapshot authoritySnapshot =
                roleAuthorityResolver.splitAuthorities(grantedAuthorities);

        return new AuthResult(
                accessToken.token(),
                accessToken.expiresAt(),
                rotatedToken.rawToken(),
                rotatedToken.expiresAt(),
                rotatedToken.userId(),
                rotatedToken.userEmail(),
                authoritySnapshot.roles(),
                authoritySnapshot.permissions());
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
            List<String> roles,
            List<String> permissions) {
    }

    private List<GrantedAuthority> ensureAuthorities(Authentication authentication, String roleCode) {
        if (authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return roleAuthorityResolver.resolveAuthorities(roleCode);
        }

        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> (GrantedAuthority) grantedAuthority)
                .toList();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
