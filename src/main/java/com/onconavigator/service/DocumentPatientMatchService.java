package com.onconavigator.service;

import com.onconavigator.ai.model.DocumentClassification;
import com.onconavigator.domain.Patient;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.security.HmacTokenService;
import com.onconavigator.web.dto.DocumentUploadResponse.PatientCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patient matching from document classification results using HMAC MRN lookup
 * and in-memory name+DOB comparison.
 *
 * <p>Matching strategy (per D-05, D-06, D-07, D-08):
 * <ol>
 *   <li>If MRN is present in classification, compute HMAC token and look up via
 *       the deterministic MRN index. Exact match returns immediately.</li>
 *   <li>If MRN match fails or MRN is absent, fall back to in-memory name+DOB
 *       comparison. All patients are loaded and their encrypted PHI fields are
 *       decrypted in memory (acceptable at pilot scale &lt;500 patients).</li>
 *   <li>Results are ranked by confidence: HIGH (name+DOB exact), MEDIUM (name
 *       partial+DOB), LOW (name only, no DOB in document).</li>
 * </ol>
 *
 * <p>HIPAA note: In-memory decrypted patient data is used ONLY for comparison
 * and never logged. Result contains only UUIDs and display names. Log statements
 * contain ONLY UUIDs -- never patient names, MRNs, or DOBs.
 */
@Service
public class DocumentPatientMatchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPatientMatchService.class);

    private final PatientRepository patientRepository;
    private final HmacTokenService hmacTokenService;

    public DocumentPatientMatchService(PatientRepository patientRepository,
                                       HmacTokenService hmacTokenService) {
        this.patientRepository = patientRepository;
        this.hmacTokenService = hmacTokenService;
    }

    /**
     * Result of a patient matching operation.
     *
     * @param status           one of "EXACT", "CANDIDATES", "NO_MATCH"
     * @param matchedPatientId the matched patient UUID (non-null only for EXACT match)
     * @param candidates       ranked candidate list (empty for EXACT and NO_MATCH)
     */
    public record MatchResult(String status, UUID matchedPatientId, List<PatientCandidate> candidates) {}

    /**
     * Match document classification results to an existing patient.
     *
     * <p>Tries MRN-based HMAC lookup first (fast, deterministic). Falls back to
     * in-memory name+DOB comparison if MRN is unavailable or not found.
     *
     * @param classification the Claude classification result with extracted patient identifiers
     * @return match result with status, optional matched patient ID, and candidates
     */
    public MatchResult matchPatient(DocumentClassification classification) {
        // Strategy 1: HMAC MRN lookup (D-05)
        if (classification.mrn() != null && !classification.mrn().isBlank()) {
            String hmacToken = hmacTokenService.computeMrnToken(classification.mrn());
            Optional<Patient> mrnMatch = patientRepository.findByMrnHmacToken(hmacToken);
            if (mrnMatch.isPresent()) {
                UUID patientId = mrnMatch.get().getId();
                log.info("Document matched to patient {} via MRN HMAC lookup", patientId);
                return new MatchResult("EXACT", patientId, List.of());
            }
        }

        // Strategy 2: In-memory name+DOB matching (D-08)
        return matchByNameAndDob(classification);
    }

    /**
     * In-memory name+DOB patient matching.
     *
     * <p>Loads all patients (acceptable at pilot scale &lt;500), decrypts PHI fields
     * in memory via JPA EncryptionConverter, and compares against extracted identifiers.
     */
    private MatchResult matchByNameAndDob(DocumentClassification classification) {
        String docName = classification.patientName();
        String docDob = classification.dateOfBirth();

        if (docName == null || docName.isBlank()) {
            log.info("No patient name in classification, cannot perform name matching");
            return new MatchResult("NO_MATCH", null, List.of());
        }

        List<Patient> allPatients = patientRepository.findAll();
        List<PatientCandidate> candidates = new ArrayList<>();

        for (Patient patient : allPatients) {
            String patientFullName = patient.getFirstName() + " " + patient.getLastName();
            String confidence = scoreMatch(docName, patientFullName, docDob, patient.getDateOfBirth());

            if (confidence != null) {
                // CR-04: Mask MRN to show only last 4 digits (PHI minimization in API response).
                // The full MRN is never needed in the matching UI -- last 4 is sufficient
                // for the nurse navigator to distinguish candidates.
                String mrn = patient.getMrn();
                String maskedMrn = (mrn != null && mrn.length() > 4)
                        ? "***" + mrn.substring(mrn.length() - 4)
                        : "****";
                candidates.add(new PatientCandidate(
                        patient.getId(),
                        patientFullName,
                        maskedMrn,
                        patient.getDateOfBirth(),
                        confidence
                ));
            }
        }

        // Sort by confidence: HIGH > MEDIUM > LOW
        candidates.sort(Comparator.comparingInt(this::confidenceRank).reversed());

        // Limit to top 5 candidates
        if (candidates.size() > 5) {
            candidates = new ArrayList<>(candidates.subList(0, 5));
        }

        if (candidates.isEmpty()) {
            log.info("No patient match found for document classification");
            return new MatchResult("NO_MATCH", null, List.of());
        }

        // Single HIGH confidence match = EXACT
        if (candidates.size() == 1 && "HIGH".equals(candidates.getFirst().confidence())) {
            UUID patientId = candidates.getFirst().patientId();
            log.info("Document matched to patient {} via name+DOB (HIGH confidence)", patientId);
            return new MatchResult("EXACT", patientId, List.of());
        }

        // Multiple matches or non-HIGH single match = CANDIDATES
        log.info("Document matched {} candidate patients", candidates.size());
        return new MatchResult("CANDIDATES", null, candidates);
    }

    /**
     * Score the match between document-extracted identifiers and a patient record.
     *
     * @param docName        the patient name from the document
     * @param patientName    the patient's full name from the database (decrypted)
     * @param docDob         the DOB from the document (may be null)
     * @param patientDob     the patient's DOB from the database (decrypted)
     * @return "HIGH", "MEDIUM", "LOW", or null (no match)
     */
    private String scoreMatch(String docName, String patientName, String docDob, String patientDob) {
        boolean nameExactMatch = docName.equalsIgnoreCase(patientName);
        boolean namePartialMatch = !nameExactMatch && (
                patientName.toLowerCase().contains(docName.toLowerCase()) ||
                docName.toLowerCase().contains(patientName.toLowerCase())
        );

        if (!nameExactMatch && !namePartialMatch) {
            return null; // No name match at all
        }

        boolean dobMatch = docDob != null && !docDob.isBlank()
                && patientDob != null && !patientDob.isBlank()
                && docDob.equals(patientDob);

        if (nameExactMatch && dobMatch) {
            return "HIGH";
        }
        if (namePartialMatch && dobMatch) {
            return "MEDIUM";
        }
        if (nameExactMatch || namePartialMatch) {
            // Name match but no DOB in document or no DOB match
            return "LOW";
        }
        return null;
    }

    /**
     * Convert confidence string to numeric rank for sorting.
     */
    private int confidenceRank(PatientCandidate candidate) {
        return switch (candidate.confidence()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
