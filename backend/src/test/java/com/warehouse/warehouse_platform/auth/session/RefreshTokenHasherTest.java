package com.warehouse.warehouse_platform.auth.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void hash_shouldBeDeterministic_andHexEncoded() {
        String token = "sample-refresh-token";

        String first = hasher.hash(token);
        String second = hasher.hash(token);

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void hash_shouldDiffer_forDifferentInputs() {
        String first = hasher.hash("token-a");
        String second = hasher.hash("token-b");

        assertNotEquals(first, second);
    }
}
