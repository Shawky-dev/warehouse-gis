package com.warehouse.warehouse_platform.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        UserPayload user
) {
    public static AuthResponse from(AuthService.AuthResult result) {
        return new AuthResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenExpiresAt(),
                new UserPayload(
                        result.userId(),
                        result.email(),
                        List.of("ROLE_" + result.role())));
    }

    public record UserPayload(
            UUID id,
            String email,
            List<String> roles
    ) {
    }
}
