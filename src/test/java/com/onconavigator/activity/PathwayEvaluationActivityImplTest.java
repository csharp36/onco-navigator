package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.ai.model.AlertText;
import com.onconavigator.ai.service.AlertGenerationAiService;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PathwayEvaluationActivityImpl} Claude AI integration.
 *
 * <p>Tests verify the template-first / Claude-fallback alert generation logic
 * using per-patient DAG pathway steps (Phase 6 model). These tests complement
 * the baseline deviation detection tests in {@link PathwayEvaluationActivityTest}.
 *
 * <p>Updated in Phase 6 to use PatientPathwayRepository/StepRepository/EdgeRepository
 * instead of the previous PathwayTemplateRepository/PhysicianOverrideRepository.
 *
 * <p>Test cases cover:
 * <ul>
 *   <li>AI-01: Step alertText used for standard deviations (Claude NOT called)</li>
 *   <li>AI-02/AI-03: Claude called for non-standard deviations (null/blank alertText)</li>
 *   <li>AI-04: Generic fallback when Claude returns null (circuit breaker open)</li>
 *   <li>Zero-PHI: Only anonymized context passed to Claude (no patient identifiers)</li>
 * </ul>
 *
 * <p>PHI safety: All test data uses synthetic UUIDs and placeholder strings. No real PHI.
 * Test patient names are deliberately chosen to verify they do NOT appear in Claude parameters.
 */
class PathwayEvaluationActivityImplTest {

    private PatientRepository patientRepository;
    private CareEventRepository careEventRepository;
    private AlertRepository alertRepository;
    private PatientPathwayRepository pathwayRepository;
    private PatientPathwayStepRepository stepRepository;
    private PatientPathwayEdgeRepository edgeRepository;
    private ObjectMapper objectMapper;
    private AlertGenerationAiService alertGenerationAiService;
    private NotificationService notificationService;
    private PathwayEvaluationActivityImpl activity;

    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PATHWAY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID STEP1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        careEventRepository = Mockito.mock(CareEventRepository.class);
        alertRepository = Mockito.mock(AlertRepository.class);
        pathwayRepository = Mockito.mock(PatientPathwayRepository.class);
        stepRepository = Mockito.mock(PatientPathwayStepRepository.class);
        edgeRepository = Mockito.mock(PatientPathwayEdgeRepository.class);
        objectMapper = new ObjectMapper();
        alertGenerationAiService = Mockito.mock(AlertGenerationAiService.class);
        notificationService = Mockito.mock(NotificationService.class);
        activity = new PathwayEvaluationActivityImpl(
                patientRepository, careEventRepository, alertRepository,
                pathwayRepository, stepRepository, edgeRepository,
                objectMapper, alertGenerationAiService, notificationService);
    }

    // ---- Helper factories ----

    private Patient createTestPatient(CancerType type, LocalDate diagnosisDate) {
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setCancerType(type);
        patient.setDiagnosisDate(diagnosisDate);
        // Set PHI fields to verify they are NOT leaked to Claude
        patient.setFirstName("Jane");
        patient.setLastName("Doe");
        patient.setMrn("MRN-12345");
        patient.setDateOfBirth("1965-08-14");
        patient.setCancerStage("IIA");
        return patient;
    }

    private PatientPathway createTestPathway() {
        PatientPathway pathway = new PatientPathway();
        pathway.setId(PATHWAY_ID);
        return pathway;
    }

    private PatientPathwayStep createActiveStep(UUID id, String name, CareEventType eventType,
                                                  int windowDays, String alertText,
                                                  PatientPathway pathway) {
        PatientPathwayStep step = new PatientPathwayStep();
        step.setId(id);
        step.setPathway(pathway);
        step.setName(name);
        step.setEventType(eventType);
        step.setWindowDays(windowDays);
        step.setStatus(PathwayStepStatus.ACTIVE);
        step.setRequired(true);
        step.setAlertText(alertText);
        return step;
    }

    private void setupCommonMocks(Patient patient, PatientPathway pathway,
                                   PatientPathwayStep step) {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of());
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of());
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any()))
                .thenReturn(false);
    }

    // ---- Tests ----

    /**
     * AI-01: Step alertText is the primary source for standard deviations.
     *
     * <p>When {@code step.getAlertText()} is non-null and non-blank, the alert's
     * deviationDescription should be the step's alertText. Claude should NOT be called.
     */
    @Test
    void evaluate_usesStepAlertText_whenAlertTextIsPresent() {
        // 35 days past diagnosis — triggers MISSING_EVENT for 14-day window
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14,
                "Expected template text for surgeon consultation deviation.", pathway);

        setupCommonMocks(patient, pathway, step);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with the step's alertText
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .isEqualTo("Expected template text for surgeon consultation deviation.");

        // Claude should NOT have been called (step alertText is primary -- AI-01)
        verifyNoInteractions(alertGenerationAiService);
    }

    /**
     * AI-02/AI-03: Claude called for non-standard deviations when alertText is null.
     *
     * <p>When {@code step.getAlertText()} is null, the activity should call
     * AlertGenerationAiService to generate Claude-powered text.
     */
    @Test
    void evaluate_callsClaude_whenAlertTextIsNullOrBlank() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveStep(STEP1_ID, "Radiation Therapy Planning",
                CareEventType.RADIATION, 28, null /* null alertText */, pathway);

        setupCommonMocks(patient, pathway, step);

        // Mock Claude to return generated alert text
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText(
                        "Claude generated desc for radiation therapy delay",
                        "Claude suggested action: contact radiation oncology",
                        null));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with Claude-generated text
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .isEqualTo("Claude generated desc for radiation therapy delay");

        // Verify Claude was called
        verify(alertGenerationAiService).generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList());
    }

    /**
     * AI-04: Generic fallback when Claude returns null (circuit breaker open).
     *
     * <p>When alertText is null AND Claude returns null, the activity should use
     * the generic fallback description containing the step name.
     */
    @Test
    void evaluate_usesFallbackDescription_whenClaudeReturnsNull() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveStep(STEP1_ID, "Radiation Therapy Planning",
                CareEventType.RADIATION, 28, null /* null alertText */, pathway);

        setupCommonMocks(patient, pathway, step);

        // Claude returns null (circuit breaker open)
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(null);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with fallback description containing the step name
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .contains("Radiation Therapy Planning");
    }

    /**
     * Zero-PHI boundary: Only anonymized context passed to Claude.
     *
     * <p>Captures the parameters passed to AlertGenerationAiService and verifies:
     * <ul>
     *   <li>First param (cancerType) is an enum name (e.g., "BREAST"), not a patient name</li>
     *   <li>Second param (pathwayStepName) is a step name, not PHI</li>
     *   <li>No captured parameter contains the test patient's name ("Jane", "Doe")</li>
     *   <li>No captured parameter contains the test patient's MRN or DOB</li>
     * </ul>
     */
    @Test
    void evaluate_passesOnlyAnonymizedContextToClaude() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveStep(STEP1_ID, "Radiation Therapy Planning",
                CareEventType.RADIATION, 28, null /* null alertText — triggers Claude */, pathway);

        setupCommonMocks(patient, pathway, step);

        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText("Test desc", "Test action", null));

        activity.evaluate(PATIENT_ID);

        // Capture the parameters passed to Claude
        ArgumentCaptor<String> cancerTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stepNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deviationTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> windowDaysCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> completedStepsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> missingStepsCaptor = ArgumentCaptor.forClass(List.class);

        verify(alertGenerationAiService).generateAlertDescription(
                cancerTypeCaptor.capture(),
                stepNameCaptor.capture(),
                deviationTypeCaptor.capture(),
                windowDaysCaptor.capture(),
                completedStepsCaptor.capture(),
                missingStepsCaptor.capture());

        // Verify first param is enum name (non-PHI)
        assertThat(cancerTypeCaptor.getValue()).isEqualTo("BREAST");

        // Verify second param is a step name (non-PHI)
        assertThat(stepNameCaptor.getValue()).isEqualTo("Radiation Therapy Planning");

        // Verify deviation type is an enum name (non-PHI)
        assertThat(deviationTypeCaptor.getValue()).isEqualTo("MISSING_EVENT");

        // Verify window days is a number (non-PHI)
        assertThat(windowDaysCaptor.getValue()).isEqualTo("28");

        // CRITICAL: Verify NO patient identifiers in any parameter
        String allParams = cancerTypeCaptor.getValue()
                + stepNameCaptor.getValue()
                + deviationTypeCaptor.getValue()
                + windowDaysCaptor.getValue()
                + String.join(",", completedStepsCaptor.getValue())
                + String.join(",", missingStepsCaptor.getValue());

        // Test patient is "Jane Doe" with MRN "MRN-12345" and DOB "1965-08-14"
        assertThat(allParams).doesNotContain("Jane");
        assertThat(allParams).doesNotContain("Doe");
        assertThat(allParams).doesNotContain("MRN-12345");
        assertThat(allParams).doesNotContain("1965-08-14");
        assertThat(allParams).doesNotContainPattern("\\bMRN[:\\s]*\\w+\\b");
        assertThat(allParams).doesNotContainPattern("\\b\\d{3}-\\d{2}-\\d{4}\\b"); // no SSN-like
    }

    /**
     * AI-02: Blank alertText (whitespace only) triggers Claude path same as null.
     */
    @Test
    void evaluate_callsClaude_whenAlertTextIsBlank() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveStep(STEP1_ID, "Radiation Therapy Planning",
                CareEventType.RADIATION, 28, "   " /* blank alertText */, pathway);

        setupCommonMocks(patient, pathway, step);

        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText("Claude text for blank template", "Claude action", null));

        activity.evaluate(PATIENT_ID);

        // Verify Claude was called (blank alertText treated as non-standard)
        verify(alertGenerationAiService).generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList());

        // Verify alert was saved with Claude text
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .isEqualTo("Claude text for blank template");
    }

    // ---- Gap 4: cap150 enforcement at activity layer ----

    /**
     * PW-ALL-007: suggestedAction exceeding 150 characters is truncated before saving alert.
     */
    @Test
    void evaluate_cap150_truncatesSuggestedAction_when_exceedingLimit() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        // Step has alertText (template-first path) with suggestedAction > 150 chars
        String longAction = "A".repeat(200);
        PatientPathwayStep step = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Template alert text.", pathway);
        step.setSuggestedAction(longAction);

        setupCommonMocks(patient, pathway, step);

        activity.evaluate(PATIENT_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getSuggestedAction()).isNotNull();
        assertThat(savedAlert.getSuggestedAction().length()).isLessThanOrEqualTo(150);
    }

    /**
     * PW-ALL-007: missingSummary exceeding 150 characters is truncated before saving alert.
     * When Claude returns a missingSummary longer than 150 chars, cap150 truncates it.
     */
    @Test
    void evaluate_cap150_truncatesMissingSummary_when_exceedingLimit() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        // Step has null alertText, so Claude path is taken
        PatientPathwayStep step = createActiveStep(STEP1_ID, "Radiation Therapy Planning",
                CareEventType.RADIATION, 28, null, pathway);

        setupCommonMocks(patient, pathway, step);

        // Claude returns a missingSummary longer than 150 chars
        String longSummary = "M".repeat(200);
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText("Claude description", "Short action", longSummary));

        activity.evaluate(PATIENT_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getMissingSummary()).isNotNull();
        assertThat(savedAlert.getMissingSummary().length()).isLessThanOrEqualTo(150);
    }

    // ---- Gap 5: Template-based missingSummary derivation ----

    /**
     * When step.getAlertText() is non-null (template-first path), missingSummary is
     * derived from deviationDescription (which is the alertText value).
     */
    @Test
    void evaluate_templatePath_derivesMissingSummary_fromAlertText() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        String templateText = "Surgeon consultation has not been completed within the expected window.";
        PatientPathwayStep step = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, templateText, pathway);

        setupCommonMocks(patient, pathway, step);

        activity.evaluate(PATIENT_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        // missingSummary should be derived from the alertText (template text)
        assertThat(savedAlert.getMissingSummary()).isNotNull();
        assertThat(savedAlert.getMissingSummary()).isEqualTo(templateText);
        // Claude should NOT have been called (template-first path)
        verifyNoInteractions(alertGenerationAiService);
    }

    /**
     * When step.getAlertText() is longer than 150 chars, the derived missingSummary
     * is truncated to 150 characters.
     */
    @Test
    void evaluate_templatePath_truncatesMissingSummary_whenAlertTextExceeds150() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        // AlertText longer than 150 chars
        String longAlertText = "T".repeat(200);
        PatientPathwayStep step = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, longAlertText, pathway);

        setupCommonMocks(patient, pathway, step);

        activity.evaluate(PATIENT_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        // missingSummary should be derived from first 150 chars of alertText, then cap150'd
        assertThat(savedAlert.getMissingSummary()).isNotNull();
        assertThat(savedAlert.getMissingSummary().length()).isLessThanOrEqualTo(150);
        // deviationDescription should still be the full alertText
        assertThat(savedAlert.getDeviationDescription()).isEqualTo(longAlertText);
    }
}
