package com.warehouse.warehouse_platform.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createAccessToken_shouldEmbedTenantRolesAndStandardClaims() throws Exception {
        TenantContext.setTenantId("acme");
        KeyPair keyPair = keyPair();
        JwtTokenService service = new JwtTokenService(jwtEncoder(keyPair), jwtProperties());
        JwtDecoder decoder = jwtDecoder(keyPair);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin@acme.local",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_WORKER")));

        JwtTokenService.AccessTokenResult result = service.createAccessToken(authentication);
        Jwt decoded = decoder.decode(result.token());

        assertEquals("warehouse-platform-api-test", decoded.getClaimAsString("iss"));
        assertTrue(decoded.getAudience().contains("warehouse-platform-web-test"));
        assertEquals("admin@acme.local", decoded.getSubject());
        assertEquals("acme", decoded.getClaimAsString("tenant"));

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) decoded.getClaim("roles");
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_WORKER"));

        assertNotNull(decoded.getIssuedAt());
        assertNotNull(decoded.getExpiresAt());
        assertTrue(decoded.getExpiresAt().isAfter(decoded.getIssuedAt()));
        assertEquals(
                result.expiresAt().truncatedTo(ChronoUnit.SECONDS),
                decoded.getExpiresAt());
        assertNotNull(decoded.getId());
        assertFalse(decoded.getId().isBlank());
    }

    @Test
    void createAccessToken_shouldDefaultTenantClaimToBootstrapWhenContextMissing() throws Exception {
        KeyPair keyPair = keyPair();
        JwtTokenService service = new JwtTokenService(jwtEncoder(keyPair), jwtProperties());
        JwtDecoder decoder = jwtDecoder(keyPair);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin@system.local",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        JwtTokenService.AccessTokenResult result = service.createAccessToken(authentication);
        Jwt decoded = decoder.decode(result.token());

        assertEquals("BOOTSTRAP", decoded.getClaimAsString("tenant"));
    }

    private static JwtProperties jwtProperties() {
        return new JwtProperties(
                "warehouse-platform-api-test",
                "warehouse-platform-web-test",
                Duration.ofMinutes(10),
                Duration.ofDays(14),
                "warehouse-k1-test",
                new ByteArrayResource(new byte[] {}),
                new ByteArrayResource(new byte[] {}));
    }

    private static JwtEncoder jwtEncoder(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsa = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("warehouse-k1-test")
                .build();

        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
    }

    private static JwtDecoder jwtDecoder(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }
}
