package com.onconavigator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup validator that prevents the application from running with the all-zeroes
 * placeholder AES encryption key.
 *
 * <p>The {@code application-local.yml} ships with a known placeholder value
 * ({@link #PLACEHOLDER}) so that the configuration key is always present.
 * If a developer starts the application without replacing the placeholder with a real
 * key (generated via {@code openssl rand -base64 32}), PHI fields would be encrypted
 * with an all-zeroes key — effectively no encryption.
 *
 * <p>This validator fails fast at startup so the failure mode is loud and obvious
 * rather than silent. In production (AWS profile), the key is injected from Secrets
 * Manager and will never match the placeholder.
 *
 * <p>HIPAA relevance: Using the placeholder key renders column-level PHI encryption
 * trivially reversible. This class ensures that cannot happen undetected.
 */
@Component
public class EncryptionKeyValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EncryptionKeyValidator.class);

    /**
     * The known all-zeroes placeholder key shipped in {@code application-local.yml}.
     * 44 Base64 characters encoding 32 bytes of {@code 0x00}.
     */
    static final String PLACEHOLDER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Value("${onconavigator.encryption.key}")
    private String encryptionKey;

    /**
     * Validates the encryption key at application startup.
     *
     * @param args application arguments (unused)
     * @throws IllegalStateException if the encryption key is the placeholder value
     */
    @Override
    public void run(ApplicationArguments args) {
        if (PLACEHOLDER.equals(encryptionKey)) {
            log.error("FATAL: onconavigator.encryption.key is the all-zeroes placeholder value. "
                    + "PHI encryption is not safe. "
                    + "Generate a real key: openssl rand -base64 32");
            throw new IllegalStateException(
                    "FATAL: onconavigator.encryption.key is the placeholder value. "
                    + "Generate a real key with: openssl rand -base64 32");
        }
        log.info("Encryption key validation passed.");
    }
}
