package com.warehouse.warehouse_platform.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey(JwtProperties properties)
            throws IOException, GeneralSecurityException {
        String pem = readResource(properties.privateKey());
        String normalized = stripPemHeaders(pem, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(normalized);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    @Bean
    public RSAPublicKey jwtPublicKey(JwtProperties properties)
            throws IOException, GeneralSecurityException {
        String pem = readResource(properties.publicKey());
        String normalized = stripPemHeaders(pem, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(normalized);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties,
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey) {

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties, RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error("invalid_token", "Missing required audience", null);
            return OAuth2TokenValidatorResult.failure(error);
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private static String readResource(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String stripPemHeaders(String pem, String label) {
        return pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
    }
}
