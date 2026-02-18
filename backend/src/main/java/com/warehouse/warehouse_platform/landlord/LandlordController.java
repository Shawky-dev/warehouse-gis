package com.warehouse.warehouse_platform.landlord;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/landlord")
public class LandlordController {

    private final LandlordAccessService landlordAccessService;

    public LandlordController(LandlordAccessService landlordAccessService) {
        this.landlordAccessService = landlordAccessService;
    }

    @GetMapping("/session")
    public ResponseEntity<LandlordAccessService.LandlordSessionResponse> session(Authentication authentication) {
        return ResponseEntity.ok(landlordAccessService.getAdminSession(authentication));
    }
}
