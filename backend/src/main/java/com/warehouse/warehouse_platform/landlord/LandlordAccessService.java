package com.warehouse.warehouse_platform.landlord;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LandlordAccessService {

    public LandlordSessionResponse getAdminSession(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .toList();

        return new LandlordSessionResponse(
                authentication.getName(),
                authorities,
                Instant.now(),
                "Landlord access granted");
    }

    public record LandlordSessionResponse(
            String subject,
            List<String> authorities,
            Instant serverTime,
            String message
    ) {
    }
}
