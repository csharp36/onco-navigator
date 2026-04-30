package com.onconavigator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Provides the AES-256 encryption key for PHI column-level encryption.
 *
 * <p>The key is read from {@code onconavigator.encryption.key}, a Base64-encoded
 * 256-bit value. On local dev this property is provided via {@code application-local.yml}
 * (optionally Jasypt-encrypted). On AWS the value comes from Secrets Manager.
 *
 * <p>HIPAA requirement: PHI fields (name, DOB, MRN) must be encrypted at rest.
 * This key is the root secret protecting those fields. Never log it.
 */
@Configuration
public class EncryptionConfig {

    @Value("${onconavigator.encryption.key}")
    private String encryptionKeyBase64;

    /**
     * The AES-256 {@link SecretKey} used by {@link com.onconavigator.security.EncryptionConverter}
     * to encrypt and decrypt PHI columns.
     *
     * @return a 256-bit AES key decoded from the configured Base64 string
     */
    @Bean
    public SecretKey phiEncryptionKey() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "PHI encryption key must be 256 bits (32 bytes). "
                    + "Got " + keyBytes.length + " bytes. "
                    + "Generate a key with: openssl rand -base64 32");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
