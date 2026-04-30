package com.onconavigator.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptionConverter} proving AES-256-GCM encryption correctness.
 *
 * <p>Uses the package-private {@code EncryptionConverter(SecretKey)} constructor to inject
 * a known test key directly, bypassing Spring context. This allows the test to run without
 * a full application context and without a database.
 *
 * <p>HIPAA relevance: These tests are the verifiable evidence that PHI column encryption
 * round-trips correctly and uses semantic security (different ciphertext for same plaintext).
 * They run in CI and catch regressions in the encryption implementation.
 */
class EncryptionConverterTest {

    private EncryptionConverter converter;

    @BeforeEach
    void setUp() {
        // Generate a fresh 256-bit AES key for each test
        byte[] keyBytes = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(keyBytes);
        SecretKey testKey = new SecretKeySpec(keyBytes, "AES");
        converter = new EncryptionConverter(testKey);
    }

    /**
     * Core correctness test: encrypt then decrypt produces the original string.
     * Failure here means PHI data is irreversibly corrupted on write.
     */
    @Test
    void encryptDecrypt_roundTrip_returnsOriginal() {
        String original = "John Smith";
        byte[] encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    /**
     * Empty strings are valid PHI field values (e.g., missing middle name) and must
     * round-trip correctly without special-casing.
     */
    @Test
    void encryptDecrypt_emptyString_returnsEmpty() {
        String original = "";
        byte[] encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    /**
     * Null input is used for optional PHI fields (e.g., CareEvent notes).
     * Must return null without throwing — JPA may call convertToDatabaseColumn(null)
     * for optional columns.
     */
    @Test
    void encrypt_nullValue_returnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    /**
     * Null database value is used for unset optional fields. Must return null so JPA
     * correctly sets the entity field to null.
     */
    @Test
    void decrypt_nullValue_returnsNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    /**
     * AES-GCM with random IV must produce different ciphertext each time the same plaintext
     * is encrypted. This is semantic security — it prevents an attacker from detecting
     * repeated values even if they observe encrypted PHI bytes in the database.
     *
     * <p>HIPAA relevance: without random IV, an attacker could correlate patient records
     * by matching ciphertext values, revealing which patients share attributes.
     */
    @Test
    void encrypt_sameInput_producesDifferentCiphertext() {
        String input = "Patient Name";
        byte[] encrypted1 = converter.convertToDatabaseColumn(input);
        byte[] encrypted2 = converter.convertToDatabaseColumn(input);
        assertFalse(java.util.Arrays.equals(encrypted1, encrypted2),
            "Same plaintext must produce different ciphertext (random IV required for semantic security)");
    }

    /**
     * Verifies the binary layout: [12-byte IV][ciphertext][16-byte GCM tag].
     * The minimum valid output length is IV (12) + 1 byte plaintext + GCM tag (16) = 29 bytes.
     */
    @Test
    void encrypt_outputContainsIvPlusCiphertext() {
        String input = "test";
        byte[] encrypted = converter.convertToDatabaseColumn(input);
        // 12 bytes IV + at least 4 bytes ciphertext + 16 bytes GCM auth tag
        assertTrue(encrypted.length >= 12 + 1 + 16,
            "Encrypted output must contain IV (12 bytes) + ciphertext + GCM tag (16 bytes), "
            + "got length: " + encrypted.length);
    }

    /**
     * Round-trip test for Unicode content (patient notes may contain accented characters
     * or other multi-byte UTF-8 sequences).
     */
    @Test
    void encryptDecrypt_unicodeContent_roundTrips() {
        String original = "Résumé — Müller, 日本語";
        byte[] encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    /**
     * GCM authentication tag catches tampering: if any byte of the ciphertext is modified,
     * decryption must throw rather than return corrupted plaintext.
     * This is the HIPAA tamper-detection property.
     */
    @Test
    void decrypt_tamperedCiphertext_throwsException() {
        String original = "Sensitive PHI data";
        byte[] encrypted = converter.convertToDatabaseColumn(original);

        // Flip a bit in the ciphertext portion (after the 12-byte IV)
        encrypted[20] ^= 0xFF;

        assertThrows(EncryptionConverter.EncryptionException.class,
            () -> converter.convertToEntityAttribute(encrypted),
            "Tampered ciphertext must throw EncryptionException (GCM authentication tag failure)");
    }
}
