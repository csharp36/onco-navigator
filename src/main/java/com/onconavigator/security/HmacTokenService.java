package com.onconavigator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Computes a deterministic HMAC-SHA256 token for MRN equality search.
 *
 * <p>MRN is stored AES-GCM encrypted with a random IV, making direct database equality
 * queries impossible (same plaintext produces different ciphertexts). This service computes
 * a deterministic HMAC-SHA256 token from the plaintext MRN, which is stored alongside the
 * encrypted value and indexed for efficient equality lookups (per D-04 design decision).
 *
 * <p>The HMAC token is non-reversible — knowledge of the token does not reveal the MRN.
 * However, the key must be kept secret (stored in {@code application-local.yml} for local dev,
 * AWS Secrets Manager for production). If the key is compromised, the tokens become reversible
 * by brute-force over the MRN space.
 *
 * <p>Key separation: This service uses {@code onconavigator.hmac.key}, which is a DIFFERENT
 * key from {@code onconavigator.encryption.key} (the AES key). Using separate keys limits
 * the blast radius of any individual key compromise (HIPAA security best practice).
 *
 * <p>HIPAA note: Do NOT log the output of {@link #computeMrnToken} — while non-reversible,
 * these tokens are stable identifiers that could enable correlation attacks.
 */
@Service
public class HmacTokenService {

    private final byte[] hmacKey;

    public HmacTokenService(@Value("${onconavigator.hmac.key}") String hmacKeyBase64) {
        this.hmacKey = Base64.getDecoder().decode(hmacKeyBase64);
        if (this.hmacKey.length != 32) {
            throw new IllegalArgumentException(
                    "HMAC key must be 256 bits (32 bytes). Generate with: openssl rand -base64 32");
        }
    }

    /**
     * Compute the HMAC-SHA256 token for the given MRN.
     *
     * <p>Returns a 64-character lowercase hex string. The same MRN always produces
     * the same token (deterministic). Different MRNs produce different tokens (collision-resistant).
     *
     * @param mrn the plaintext MRN to hash
     * @return 64-character hex HMAC-SHA256 token
     */
    public String computeMrnToken(String mrn) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] hash = mac.doFinal(mrn.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
