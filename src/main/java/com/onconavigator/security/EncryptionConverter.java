package com.onconavigator.security;

import com.onconavigator.config.ApplicationContextProvider;
import com.onconavigator.config.EncryptionConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * JPA {@link AttributeConverter} that encrypts String PHI fields to {@code byte[]} using
 * AES-256-GCM before writing to the database, and decrypts on read.
 *
 * <p>Layout of the stored byte array: {@code [12-byte IV][ciphertext + 16-byte GCM tag]}
 * The IV is randomly generated per encryption operation using a CSPRNG, ensuring that
 * two encryptions of the same plaintext produce different ciphertexts (semantic security).
 *
 * <p>HIPAA relevance: This converter is applied to all PHI fields (firstName, lastName,
 * dateOfBirth, mrn on {@link com.onconavigator.domain.Patient} and notes on
 * {@link com.onconavigator.domain.CareEvent}). The corresponding database columns use
 * {@code BYTEA} type — no readable PHI is stored in PostgreSQL.
 *
 * <p>The {@link SecretKey} is retrieved from Spring context via
 * {@link ApplicationContextProvider} because JPA converters are instantiated by Hibernate,
 * not Spring, and cannot receive constructor injection.
 *
 * <p>GCM authentication tag (128 bits) provides integrity verification: any modification
 * to the ciphertext in the database will cause decryption to throw an
 * {@link javax.crypto.AEADBadTagException}, detecting tampering.
 */
@Converter
public class EncryptionConverter implements AttributeConverter<String, byte[]> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * Encrypts a plaintext String PHI value to a byte array for database storage.
     *
     * @param attribute the plaintext PHI string (may be null for optional fields)
     * @return IV + ciphertext as a byte array, or null if the input is null
     */
    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            SecretKey key = ApplicationContextProvider.getBean(SecretKey.class);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [12-byte IV][ciphertext]
            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);
            return result;

        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt PHI field", e);
        }
    }

    /**
     * Decrypts a byte array from the database back to a plaintext String.
     * Extracts the 12-byte IV from the first bytes, then decrypts the remainder.
     * GCM authentication tag verification happens automatically — a modified ciphertext
     * will throw an exception rather than return corrupted data.
     *
     * @param dbData the stored byte array (IV + ciphertext), or null for optional fields
     * @return the decrypted plaintext String, or null if the input is null
     */
    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }

        if (dbData.length < IV_LENGTH_BYTES) {
            throw new EncryptionException(
                    "Encrypted PHI data too short to contain IV: length=" + dbData.length, null);
        }

        try {
            SecretKey key = ApplicationContextProvider.getBean(SecretKey.class);

            byte[] iv = Arrays.copyOfRange(dbData, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(dbData, IV_LENGTH_BYTES, dbData.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);

        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt PHI field", e);
        }
    }

    /**
     * Runtime exception wrapping encryption/decryption failures.
     * Message intentionally omits PHI content.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
