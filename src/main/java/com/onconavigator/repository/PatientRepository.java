package com.onconavigator.repository;

import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.PatientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Patient} entities.
 *
 * <p>Note on {@link #findByMrn}: MRN is stored encrypted in the database, so this derived
 * query method will NOT work correctly — encrypted ciphertexts are never equal even for the
 * same plaintext (due to random IVs). This method is marked TODO for Phase 3, where a
 * custom query using the application-layer decryption or a deterministic index token will
 * be implemented.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /**
     * Find patients by their enrollment status.
     *
     * @param status the patient status to filter by
     * @return list of patients with that status
     */
    List<Patient> findByStatus(PatientStatus status);

    /**
     * Find a patient by their MRN.
     *
     * <p>TODO (Phase 3 — REPLACED): MRN is AES-GCM encrypted with a random IV, making direct
     * equality queries impossible. This method will never match because encrypted ciphertexts
     * are never equal even for the same plaintext (due to random IVs). Use
     * {@link #findByMrnHmacToken(String)} with a pre-computed HMAC token instead.
     *
     * <p>Kept for documentation of the design decision. Do not call this method.
     *
     * @param mrn the plaintext MRN — does NOT work with encrypted storage
     * @return always empty due to random-IV encryption
     * @deprecated Use {@link #findByMrnHmacToken(String)} with a pre-computed HMAC token
     */
    @Deprecated
    Optional<Patient> findByMrn(String mrn);

    /**
     * Find a patient by their deterministic HMAC index token.
     *
     * <p>The token is computed by {@link com.onconavigator.security.HmacTokenService#computeMrnToken(String)}
     * from the plaintext MRN before any database operation. The HMAC-SHA256 token is stored
     * in the {@code mrn_hmac_token} column alongside the AES-GCM encrypted MRN, indexed for
     * efficient equality lookups (per D-04 design decision and V8 Flyway migration).
     *
     * @param mrnHmacToken the 64-character hex HMAC-SHA256 token for the MRN
     * @return the patient with that MRN, if found
     */
    Optional<Patient> findByMrnHmacToken(String mrnHmacToken);

    /**
     * Count patients by enrollment status.
     *
     * <p>Used by the dashboard stats endpoint to display active patient counts
     * without loading all patient records.
     *
     * @param status the patient status to count
     * @return number of patients with that status
     */
    long countByStatus(PatientStatus status);
}
