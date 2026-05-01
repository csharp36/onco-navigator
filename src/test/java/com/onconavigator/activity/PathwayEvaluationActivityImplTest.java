package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.ai.model.AlertText;
import com.onconavigator.ai.service.AlertGenerationAiService;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.PathwayTemplateRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.repository.PhysicianOverrideRepository;
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
 * introduced in Phase 4 Plan 04. These tests complement the baseline deviation
 * detection tests in {@link PathwayEvaluationActivityTest}.
 *
 * <p>Test cases cover:
 * <ul>
 *   <li>AI-01: Template text used for standard deviations (Claude NOT called)</li>
 *   <li>AI-02/AI-03: Claude called for non-standard deviations (null/blank alertText)</li>
 *   <li>AI-04: Generic fallback template when Claude returns null (circuit breaker open)</li>
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
    private PathwayTemplateRepository templateRepository;
    private PhysicianOverrideRepository overrideRepository;
    private ObjectMapper objectMapper;
    private AlertGenerationAiService alertGenerationAiService;
    private PathwayEvaluationActivityImpl activity;

    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        careEventRepository = Mockito.mock(CareEventRepository.class);
        alertRepository = Mockito.mock(AlertRepository.class);
        templateRepository = Mockito.mock(PathwayTemplateRepository.class);
        overrideRepository = Mockito.mock(PhysicianOverrideRepository.class);
        objectMapper = new ObjectMapper();
        alertGenerationAiService = Mockito.mock(AlertGenerationAiService.class);
        activity = new PathwayEvaluationActivityImpl(
                patientRepository, careEventRepository, alertRepository,
                templateRepository, overrideRepository, objectMapper,
                alertGenerationAiService);
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

    private PathwayTemplate createTestTemplate(CancerType type, String templateDataJson) {
        PathwayTemplate template = new PathwayTemplate();
        template.setId(UUID.randomUUID());
        template.setCancerType(type);
        template.setTemplateData(templateDataJson);
        return template;
    }

    private CareEvent createTestEvent(CareEventType type, CareEventStatus status, LocalDate date) {
        CareEvent event = new CareEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setStatus(status);
        event.setEventDate(date);
        return event;
    }

    /**
     * Template with step 1 having standard alertText (non-null) -- template-first path.
     */
    private String buildTemplateWithAlertText() {
        return """
                [
                  {
                    "stepId": "BREAST_01",
                    "stepNumber": 1,
                    "name": "Surgeon Consultation",
                    "description": "Initial surgical consultation",
                    "eventType": "CONSULTATION",
                    "windowDays": 14,
                    "anchorType": "DIAGNOSIS_DATE",
                    "anchorStepId": null,
                    "required": true,
                    "alertText": "Expected template text for surgeon consultation deviation.",
                    "suggestedAction": "Contact surgeon office to schedule.",
                    "prerequisites": []
                  }
                ]
                """;
    }

    /**
     * Template with step 1 having NULL alertText -- non-standard deviation path (Claude).
     */
    private String buildTemplateWithNullAlertText() {
        return """
                [
                  {
                    "stepId": "BREAST_01",
                    "stepNumber": 1,
                    "name": "Radiation Therapy Planning",
                    "description": "Radiation therapy planning session",
                    "eventType": "RADIATION",
                    "windowDays": 28,
                    "anchorType": "DIAGNOSIS_DATE",
                    "anchorStepId": null,
                    "required": true,
                    "alertText": null,
                    "suggestedAction": null,
                    "prerequisites": []
                  }
                ]
                """;
    }

    /**
     * Template with step 1 having blank alertText -- treated same as null.
     */
    private String buildTemplateWithBlankAlertText() {
        return """
                [
                  {
                    "stepId": "BREAST_01",
                    "stepNumber": 1,
                    "name": "Radiation Therapy Planning",
                    "description": "Radiation therapy planning session",
                    "eventType": "RADIATION",
                    "windowDays": 28,
                    "anchorType": "DIAGNOSIS_DATE",
                    "anchorStepId": null,
                    "required": true,
                    "alertText": "   ",
                    "suggestedAction": null,
                    "prerequisites": []
                  }
                ]
                """;
    }

    private void setupCommonMocks(Patient patient, PathwayTemplate template) {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of());
        when(templateRepository.findByCancerType(patient.getCancerType()))
                .thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any()))
                .thenReturn(false);
    }

    // ---- Tests ----

    /**
     * AI-01: Template text is the primary source for standard deviations.
     *
     * <p>When {@code step.alertText()} is non-null and non-blank, the alert's
     * deviationDescription should be the template text. Claude should NOT be called.
     */
    @Test
    void evaluate_usesTemplateText_whenAlertTextIsPresent() {
        // 35 days past diagnosis -- triggers MISSING_EVENT for 14-day window
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTemplateWithAlertText());

        setupCommonMocks(patient, template);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with template text
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .isEqualTo("Expected template text for surgeon consultation deviation.");
        assertThat(savedAlert.getSuggestedAction())
                .isEqualTo("Contact surgeon office to schedule.");

        // Claude should NOT have been called (template text is primary -- AI-01)
        verifyNoInteractions(alertGenerationAiService);
    }

    /**
     * AI-02/AI-03: Claude called for non-standard deviations when alertText is null.
     *
     * <p>When {@code step.alertText()} is null, the activity should call
     * AlertGenerationAiService to generate Claude-powered text.
     */
    @Test
    void evaluate_callsClaude_whenAlertTextIsNullOrBlank() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTemplateWithNullAlertText());

        setupCommonMocks(patient, template);

        // Mock Claude to return generated alert text
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText(
                        "Claude generated desc for radiation therapy delay",
                        "Claude suggested action: contact radiation oncology"));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with Claude-generated text
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .isEqualTo("Claude generated desc for radiation therapy delay");
        assertThat(savedAlert.getSuggestedAction())
                .isEqualTo("Claude suggested action: contact radiation oncology");

        // Verify Claude was called
        verify(alertGenerationAiService).generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList());
    }

    /**
     * AI-04: Generic fallback template when Claude returns null (circuit breaker open).
     *
     * <p>When alertText is null AND Claude returns null (e.g., circuit breaker is open),
     * the activity should use the generic fallback template text.
     */
    @Test
    void evaluate_usesFallbackTemplate_whenClaudeReturnsNull() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTemplateWithNullAlertText());

        setupCommonMocks(patient, template);

        // Claude returns null (circuit breaker open)
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(null);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify alert was saved with generic fallback text (AI-04)
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getDeviationDescription())
                .contains("Care pathway deviation detected for step:");
        assertThat(savedAlert.getDeviationDescription())
                .contains("Radiation Therapy Planning");
        assertThat(savedAlert.getSuggestedAction())
                .contains("Review the patient's pathway status");
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
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTemplateWithNullAlertText());

        setupCommonMocks(patient, template);

        // Mock Claude to return generated text
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText("Test desc", "Test action"));

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
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTemplateWithBlankAlertText());

        setupCommonMocks(patient, template);

        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(new AlertText("Claude text for blank template", "Claude action"));

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
}
