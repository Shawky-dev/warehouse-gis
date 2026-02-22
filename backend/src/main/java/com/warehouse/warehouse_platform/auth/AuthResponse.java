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
                        result.roles(),
                        result.permissions()));
    }

    public record UserPayload(
            UUID id,
            String email,
            List<String> roles,
            List<String> permissions
    ) {
    }
}
