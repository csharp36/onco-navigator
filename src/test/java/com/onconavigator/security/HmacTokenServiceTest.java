package com.onconavigator.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HmacTokenService} proving HMAC-SHA256 token correctness.
 *
 * <p>Verifies the three properties required for the deterministic MRN search index:
 * <ol>
 *   <li>Deterministic — same MRN always produces the same token</li>
 *   <li>Discriminating — different MRNs produce different tokens</li>
 *   <li>Fixed length — token is always a 64-character hex string (256-bit HMAC-SHA256)</li>
 * </ol>
 *
 * <p>Uses a known test key injected directly, bypassing Spring context.
 * The test key is a valid 32-byte Base64-encoded string (not a real key).
 */
class HmacTokenServiceTest {

    // 32 bytes of test data (not a real key — safe to hardcode in tests)
    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]);

    private HmacTokenService service;

    @BeforeEach
    void setUp() {
        service = new HmacTokenService(TEST_KEY_BASE64);
    }

    @Test
    void computeMrnToken_isDeterministic() {
        String mrn = "MRN-123456";
        String token1 = service.computeMrnToken(mrn);
        String token2 = service.computeMrnToken(mrn);

        assertEquals(token1, token2, "Same MRN must always produce the same HMAC token");
    }

    @Test
    void computeMrnToken_differentMrnsProduceDifferentTokens() {
        String token1 = service.computeMrnToken("MRN-000001");
        String token2 = service.computeMrnToken("MRN-000002");

        assertNotEquals(token1, token2, "Different MRNs must produce different HMAC tokens");
    }

    @Test
    void computeMrnToken_returns64HexCharacters() {
        String token = service.computeMrnToken("MRN-TEST");

        assertEquals(64, token.length(), "HMAC-SHA256 token must be 64 hex characters (256 bits)");
        assertTrue(token.matches("[0-9a-f]{64}"), "Token must be lowercase hex");
    }

    @Test
    void constructor_rejectsKeyShorterThan32Bytes() {
        // 31 bytes — one byte short of required 256-bit minimum
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        assertThrows(IllegalArgumentException.class,
                () -> new HmacTokenService(shortKey),
                "Constructor must reject keys shorter than 256 bits");
    }

    @Test
    void constructor_rejectsKeyLongerThan32Bytes() {
        // 33 bytes — one byte over the required 256-bit key
        String longKey = Base64.getEncoder().encodeToString(new byte[33]);

        assertThrows(IllegalArgumentException.class,
                () -> new HmacTokenService(longKey),
                "Constructor must reject keys longer than 256 bits");
    }

    @Test
    void computeMrnToken_emptyMrnProducesStableToken() {
        // Edge case: empty MRN should not crash — it produces a valid HMAC of empty bytes
        String token1 = service.computeMrnToken("");
        String token2 = service.computeMrnToken("");

        assertEquals(64, token1.length());
        assertEquals(token1, token2, "Empty MRN must also be deterministic");
    }
}
