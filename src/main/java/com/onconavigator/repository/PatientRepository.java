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
     * <p>TODO (Phase 3): MRN is AES-GCM encrypted with a random IV, making direct equality
     * queries impossible. Implement using a deterministic HMAC index token stored alongside
     * the encrypted value, or use application-layer decryption with a full table scan
     * (acceptable for V1 pilot with small patient count).
     *
     * @param mrn the plaintext MRN to search for
     * @return the patient, if found
     */
    Optional<Patient> findByMrn(String mrn);
}
