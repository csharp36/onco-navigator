package com.onconavigator.service;

import com.onconavigator.ai.model.DocumentClassification;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.security.HmacTokenService;
import com.onconavigator.service.DocumentPatientMatchService.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentPatientMatchService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>HMAC MRN exact match (fast path)</li>
 *   <li>Name+DOB fallback when MRN not found</li>
 *   <li>Multiple name matches returning CANDIDATES with confidence levels</li>
 *   <li>No match when nothing matches</li>
 *   <li>No match when classification has no identifiers</li>
 * </ul>
 *
 * <p>PHI safety: All test data uses synthetic names and UUIDs. No real PHI.
 */
@ExtendWith(MockitoExtension.class)
class DocumentPatientMatchServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private HmacTokenService hmacTokenService;

    private DocumentPatientMatchService service;

    private static final UUID PATIENT_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PATIENT_ID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        service = new DocumentPatientMatchService(patientRepository, hmacTokenService);
    }

    /**
     * Helper to create a test Patient with decrypted PHI fields set directly.
     * In production, EncryptionConverter auto-decrypts on entity load;
     * mock repository returns Patient objects with already-decrypted values.
     */
    private Patient createTestPatient(UUID id, String firstName, String lastName,
                                       String dateOfBirth, String mrn) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setDateOfBirth(dateOfBirth);
        patient.setMrn(mrn);
        patient.setCancerType(CancerType.BREAST);
        patient.setDiagnosisDate(LocalDate.of(2026, 1, 15));
        patient.setCancerStage("IIA");
        return patient;
    }

    @Test
    void matchPatient_returnsExact_whenMrnMatches() {
        DocumentClassification classification = new DocumentClassification(
                "PATHOLOGY_REPORT", "HIGH", "TEST-001", "Sarah TestPatient",
                "1965-08-14", "PATHOLOGY_REPORT", "2026-01-15", null);

        Patient matchedPatient = createTestPatient(
                PATIENT_ID_1, "Sarah", "TestPatient", "1965-08-14", "TEST-001");

        when(hmacTokenService.computeMrnToken("TEST-001")).thenReturn("hmac-token-for-test-001");
        when(patientRepository.findByMrnHmacToken("hmac-token-for-test-001"))
                .thenReturn(Optional.of(matchedPatient));

        MatchResult result = service.matchPatient(classification);

        assertThat(result.status()).isEqualTo("EXACT");
        assertThat(result.matchedPatientId()).isEqualTo(PATIENT_ID_1);
        assertThat(result.candidates()).isEmpty();

        // Verify HMAC was computed for the MRN
        verify(hmacTokenService).computeMrnToken("TEST-001");
    }

    @Test
    void matchPatient_fallsBackToNameDob_whenMrnNotFound() {
        // Classification has no MRN but has name and DOB
        DocumentClassification classification = new DocumentClassification(
                "RADIOLOGY_REPORT", "HIGH", null, "Sarah TestPatient",
                "1965-08-14", "IMAGING", "2026-02-10", null);

        Patient matchingPatient = createTestPatient(
                PATIENT_ID_1, "Sarah", "TestPatient", "1965-08-14", "TEST-001");

        when(patientRepository.findAll()).thenReturn(List.of(matchingPatient));

        MatchResult result = service.matchPatient(classification);

        // Single HIGH confidence match (name exact + DOB match) = EXACT
        assertThat(result.status()).isEqualTo("EXACT");
        assertThat(result.matchedPatientId()).isEqualTo(PATIENT_ID_1);

        // HMAC lookup was skipped (no MRN)
        verifyNoInteractions(hmacTokenService);
    }

    @Test
    void matchPatient_returnsCandidates_whenMultipleNameMatches() {
        DocumentClassification classification = new DocumentClassification(
                "LAB_RESULT", "MEDIUM", null, "Sarah",
                null, "LAB_WORK", "2026-03-01", null);

        // Three patients with names containing "Sarah"
        Patient patient1 = createTestPatient(
                PATIENT_ID_1, "Sarah", "TestPatient", "1965-08-14", "TEST-001");
        Patient patient2 = createTestPatient(
                PATIENT_ID_2, "Sarah", "Johnson", "1972-03-22", "TEST-002");
        Patient patient3 = createTestPatient(
                PATIENT_ID_3, "Sarah", "Williams", "1980-11-05", "TEST-003");

        when(patientRepository.findAll()).thenReturn(List.of(patient1, patient2, patient3));

        MatchResult result = service.matchPatient(classification);

        assertThat(result.status()).isEqualTo("CANDIDATES");
        assertThat(result.matchedPatientId()).isNull();
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().size()).isGreaterThanOrEqualTo(2);

        // Verify candidates have confidence levels
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.confidence()).isIn("HIGH", "MEDIUM", "LOW");
            assertThat(candidate.patientId()).isNotNull();
        });
    }

    @Test
    void matchPatient_returnsNoMatch_whenNothingMatches() {
        DocumentClassification classification = new DocumentClassification(
                "OPERATIVE_NOTE", "HIGH", null, "Completely Unknown Name",
                "2000-01-01", "SURGERY", "2026-04-01", null);

        // Patients that don't match at all
        Patient patient1 = createTestPatient(
                PATIENT_ID_1, "Sarah", "TestPatient", "1965-08-14", "TEST-001");
        Patient patient2 = createTestPatient(
                PATIENT_ID_2, "James", "Johnson", "1972-03-22", "TEST-002");

        when(patientRepository.findAll()).thenReturn(List.of(patient1, patient2));

        MatchResult result = service.matchPatient(classification);

        assertThat(result.status()).isEqualTo("NO_MATCH");
        assertThat(result.matchedPatientId()).isNull();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void matchPatient_returnsNoMatch_whenClassificationHasNoIdentifiers() {
        // All identifier fields null
        DocumentClassification classification = new DocumentClassification(
                "LAB_RESULT", "LOW", null, null,
                null, "LAB_WORK", "2026-03-01", null);

        MatchResult result = service.matchPatient(classification);

        assertThat(result.status()).isEqualTo("NO_MATCH");
        assertThat(result.matchedPatientId()).isNull();
        assertThat(result.candidates()).isEmpty();

        // No database interaction needed when no identifiers present
        verifyNoInteractions(hmacTokenService);
        verify(patientRepository, never()).findByMrnHmacToken(any());
    }

    @Test
    void matchPatient_fallsBackToNameDob_whenMrnHmacNotFound() {
        // Classification has MRN, but HMAC lookup finds no match -- fallback to name+DOB
        DocumentClassification classification = new DocumentClassification(
                "PATHOLOGY_REPORT", "HIGH", "UNKNOWN-999", "Sarah TestPatient",
                "1965-08-14", "PATHOLOGY_REPORT", "2026-01-15", null);

        when(hmacTokenService.computeMrnToken("UNKNOWN-999")).thenReturn("hmac-for-unknown");
        when(patientRepository.findByMrnHmacToken("hmac-for-unknown")).thenReturn(Optional.empty());

        Patient matchingPatient = createTestPatient(
                PATIENT_ID_1, "Sarah", "TestPatient", "1965-08-14", "TEST-001");
        when(patientRepository.findAll()).thenReturn(List.of(matchingPatient));

        MatchResult result = service.matchPatient(classification);

        // Fell back to name+DOB and found single HIGH match = EXACT
        assertThat(result.status()).isEqualTo("EXACT");
        assertThat(result.matchedPatientId()).isEqualTo(PATIENT_ID_1);

        // Verify HMAC was attempted first
        verify(hmacTokenService).computeMrnToken("UNKNOWN-999");
    }
}
