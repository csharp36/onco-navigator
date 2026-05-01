package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.onconavigator.ai.service.AlertGenerationAiService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PathwayEvaluationActivityImpl}.
 *
 * <p>All repositories are Mockito mocks — no database or Spring context required.
 * Tests verify the deviation detection logic directly against controlled fixture data.
 *
 * <p>Test cases cover:
 * <ul>
 *   <li>PATH-03: MISSING_EVENT alert created when a required step has no care event and time has elapsed</li>
 *   <li>PATH-04: DELAYED_EVENT alert created when an event exists but is not COMPLETED and time has elapsed</li>
 *   <li>PATH-05: OUT_OF_ORDER alert created when an event exists for a step whose prerequisites are incomplete</li>
 *   <li>PATH-06: Duplicate alert suppressed when an OPEN alert already exists for (patient, step)</li>
 *   <li>PATH-07: evaluate() returns a non-null PathwayEvaluationResult (logging side effect confirmed)</li>
 *   <li>PATH-08: Physician override suppresses all alerts for the overridden step</li>
 *   <li>D-09: allStepsComplete is true when all steps have COMPLETED care events</li>
 *   <li>D-08: closeOpenAlerts sets status to RESOLVED for all OPEN alerts</li>
 * </ul>
 *
 * <p>PHI safety: All test data uses synthetic UUIDs and placeholder strings. No real PHI.
 */
class PathwayEvaluationActivityTest {

    private PatientRepository patientRepository;
    private CareEventRepository careEventRepository;
    private AlertRepository alertRepository;
    private PathwayTemplateRepository templateRepository;
    private PhysicianOverrideRepository overrideRepository;
    private ObjectMapper objectMapper;
    private AlertGenerationAiService alertGenerationAiService;
    private PathwayEvaluationActivityImpl activity;

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

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

    /** Build a minimal Patient with the given cancer type and diagnosis date. No PHI fields needed. */
    private Patient createTestPatient(CancerType type, LocalDate diagnosisDate) {
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setCancerType(type);
        patient.setDiagnosisDate(diagnosisDate);
        return patient;
    }

    /** Build a PathwayTemplate with JSONB template_data. */
    private PathwayTemplate createTestTemplate(CancerType type, String templateDataJson) {
        PathwayTemplate template = new PathwayTemplate();
        template.setId(UUID.randomUUID());
        template.setCancerType(type);
        template.setTemplateData(templateDataJson);
        return template;
    }

    /** Build a CareEvent with the given type, status, and event date. */
    private CareEvent createTestEvent(CareEventType type, CareEventStatus status, LocalDate date) {
        CareEvent event = new CareEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setStatus(status);
        event.setEventDate(date);
        return event;
    }

    /**
     * Build a 2-step JSON pathway template string used in most tests.
     * Step 1: Surgeon Consultation, CONSULTATION event, windowDays=14, DIAGNOSIS_DATE anchor, required
     * Step 2: Pathology Report, PATHOLOGY_REPORT event, windowDays=14, PREVIOUS_STEP anchor, required, prereq=[TEST_01]
     */
    private String buildTwoStepTemplateJson() {
        return """
                [
                  {
                    "stepId": "TEST_01",
                    "stepNumber": 1,
                    "name": "Surgeon Consultation",
                    "description": "Initial surgical consultation",
                    "eventType": "CONSULTATION",
                    "windowDays": 14,
                    "anchorType": "DIAGNOSIS_DATE",
                    "anchorStepId": null,
                    "required": true,
                    "alertText": "Surgeon consultation not recorded within 14 days of diagnosis.",
                    "suggestedAction": "Contact surgeon to schedule consultation.",
                    "prerequisites": []
                  },
                  {
                    "stepId": "TEST_02",
                    "stepNumber": 2,
                    "name": "Pathology Report",
                    "description": "Pathology report review",
                    "eventType": "PATHOLOGY_REPORT",
                    "windowDays": 14,
                    "anchorType": "PREVIOUS_STEP",
                    "anchorStepId": null,
                    "required": true,
                    "alertText": "Pathology report not received within 14 days of consultation.",
                    "suggestedAction": "Follow up with pathology lab.",
                    "prerequisites": ["TEST_01"]
                  }
                ]
                """;
    }

    /**
     * Test 1 (PATH-03): Scenario A — Missing event.
     *
     * <p>Patient diagnosed 35 days ago. Step 1 requires a CONSULTATION event within 14 days
     * of diagnosis. No care events exist. A MISSING_EVENT alert must be created.
     */
    @Test
    void testMissingEventDetected_PATH03() {
        // 35 days ago — well past the 14-day window
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);
        assertFalse(result.allStepsComplete(), "allStepsComplete must be false when events are missing");

        // Verify a MISSING_EVENT alert was saved for step 1
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.MISSING_EVENT),
                "A MISSING_EVENT alert must be created for a step with no care event past the window");
    }

    /**
     * Test 2 (PATH-04): Scenario C — Delayed event.
     *
     * <p>Step 1 (Surgeon Consultation) was completed 18 days ago.
     * Step 2 (Pathology Report) has a PENDING care event, and 18 days have elapsed since step 1
     * completed (windowDays=14). A DELAYED_EVENT alert must be created for step 2.
     */
    @Test
    void testDelayedEventDetected_PATH04() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(50);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        // Step 1 was completed 18 days ago — provides anchor for step 2
        LocalDate step1CompletedDate = LocalDate.now().minusDays(18);
        CareEvent step1Event = createTestEvent(CareEventType.CONSULTATION, CareEventStatus.COMPLETED, step1CompletedDate);
        // Step 2 has a PENDING event — not yet COMPLETED and past the 14-day window
        CareEvent step2Event = createTestEvent(CareEventType.PATHOLOGY_REPORT, CareEventStatus.PENDING, step1CompletedDate.plusDays(1));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of(step2Event, step1Event)); // most recent first
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);

        // Verify a DELAYED_EVENT alert was saved for step 2
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.DELAYED_EVENT),
                "A DELAYED_EVENT alert must be created for a PENDING event that is past the window");
    }

    /**
     * Test 3 (PATH-05): Scenario B — Out-of-order event.
     *
     * <p>Step 2 (Pathology Report) has a SCHEDULED event, but step 1 (Surgeon Consultation,
     * a prerequisite) is NOT yet COMPLETED. An OUT_OF_ORDER alert must be created.
     */
    @Test
    void testOutOfOrderDetected_PATH05() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(20);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        // Step 2 has a SCHEDULED event, but step 1 is still PENDING (prerequisite not met)
        CareEvent step2Event = createTestEvent(CareEventType.PATHOLOGY_REPORT, CareEventStatus.SCHEDULED, LocalDate.now().minusDays(5));
        CareEvent step1Event = createTestEvent(CareEventType.CONSULTATION, CareEventStatus.PENDING, LocalDate.now().minusDays(10));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of(step2Event, step1Event));
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);

        // Verify an OUT_OF_ORDER alert was saved for step 2
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.OUT_OF_ORDER),
                "An OUT_OF_ORDER alert must be created when a step is scheduled before its prerequisite is COMPLETED");
    }

    /**
     * Test 4 (PATH-08): Physician override suppresses alert generation.
     *
     * <p>Same scenario as test 1 (missing event), but a physician override exists for step 1.
     * NO alert must be created for that step.
     */
    @Test
    void testPhysicianOverrideSuppressesAlert_PATH08() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        // Override exists for step 1 (TEST_01)
        when(overrideRepository.existsByPatientIdAndPathwayStepId(PATIENT_ID, "TEST_01")).thenReturn(true);
        when(overrideRepository.existsByPatientIdAndPathwayStepId(PATIENT_ID, "TEST_02")).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);

        // verify: alertRepository.save() must NOT have been called for step 1 (Surgeon Consultation)
        // Step 2 has no anchor (step 1 not completed) so no MISSING_EVENT either
        verify(alertRepository, never()).save(argThat(a ->
                "Surgeon Consultation".equals(a.getPathwayStepName())));
    }

    /**
     * Test 5 (PATH-06): Duplicate alert not created when an OPEN alert already exists.
     *
     * <p>Same scenario as test 1 (missing event), but the dedup check returns true for step 1.
     * alertRepository.save() must NOT be called for that step.
     */
    @Test
    void testDuplicateAlertNotCreated_PATH06() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        // Dedup: an OPEN alert already exists for step 1
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                PATIENT_ID, "Surgeon Consultation", AlertStatus.OPEN)).thenReturn(true);

        activity.evaluate(PATIENT_ID);

        // alertRepository.save() must NOT be called for "Surgeon Consultation" (duplicate suppressed)
        verify(alertRepository, never()).save(argThat(a ->
                "Surgeon Consultation".equals(a.getPathwayStepName())));
    }

    /**
     * Test 6 (D-09): allStepsComplete returns true when all steps have COMPLETED care events.
     *
     * <p>Both steps in the 2-step pathway have COMPLETED events. No alerts should be generated
     * and allStepsComplete must be true.
     */
    @Test
    void testAllStepsComplete_returnsTrue() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(50);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        // Both steps completed
        CareEvent step1Event = createTestEvent(CareEventType.CONSULTATION, CareEventStatus.COMPLETED,
                diagnosisDate.plusDays(10));
        CareEvent step2Event = createTestEvent(CareEventType.PATHOLOGY_REPORT, CareEventStatus.COMPLETED,
                diagnosisDate.plusDays(20));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of(step2Event, step1Event));
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result, "evaluate() must return a non-null result");
        assertTrue(result.allStepsComplete(),
                "allStepsComplete must be true when all pathway steps have COMPLETED care events (D-09)");

        // No alerts should be generated when all steps are complete
        verify(alertRepository, never()).save(any(Alert.class));
    }

    /**
     * Test 7 (PATH-07): evaluate() returns a non-null PathwayEvaluationResult.
     *
     * <p>Confirms that the evaluation succeeds and logging side effect occurs without error.
     * The PATHWAY_EVALUATION log line is a side effect verified by the absence of exceptions.
     */
    @Test
    void testEvaluationLogsResult_PATH07() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(5); // within window — no alerts
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PathwayTemplate template = createTestTemplate(CancerType.BREAST, buildTwoStepTemplateJson());

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(templateRepository.findByCancerType(CancerType.BREAST)).thenReturn(Optional.of(template));
        when(overrideRepository.existsByPatientIdAndPathwayStepId(any(), any())).thenReturn(false);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        // evaluate() must return a non-null result — the log statement is a side effect
        assertNotNull(result, "evaluate() must return a non-null PathwayEvaluationResult (PATH-07)");
        assertNotNull(result.alertsGenerated(),
                "alertsGenerated list must not be null in PathwayEvaluationResult");
    }

    /**
     * Test 8 (D-08): closeOpenAlerts sets status to RESOLVED for all OPEN alerts.
     *
     * <p>When a patient is deactivated, all OPEN alerts must be resolved.
     * This test verifies that saveAll() is called with alerts in RESOLVED status.
     */
    @Test
    void testCloseOpenAlerts_setsResolvedStatus() {
        // Create 2 OPEN alerts
        Alert alert1 = new Alert();
        alert1.setId(UUID.randomUUID());
        alert1.setPatientId(PATIENT_ID);
        alert1.setAlertType(AlertType.MISSING_EVENT);
        alert1.setStatus(AlertStatus.OPEN);
        alert1.setPathwayStepName("Surgeon Consultation");
        alert1.setDeviationDescription("Consultation not recorded.");

        Alert alert2 = new Alert();
        alert2.setId(UUID.randomUUID());
        alert2.setPatientId(PATIENT_ID);
        alert2.setAlertType(AlertType.DELAYED_EVENT);
        alert2.setStatus(AlertStatus.OPEN);
        alert2.setPathwayStepName("Pathology Report");
        alert2.setDeviationDescription("Pathology report delayed.");

        when(alertRepository.findByPatientIdAndStatus(PATIENT_ID, AlertStatus.OPEN))
                .thenReturn(List.of(alert1, alert2));

        activity.closeOpenAlerts(PATIENT_ID);

        // saveAll must be called with the 2 alerts
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Alert>> alertListCaptor = ArgumentCaptor.forClass(List.class);
        verify(alertRepository).saveAll(alertListCaptor.capture());

        List<Alert> savedAlerts = alertListCaptor.getValue();
        assertEquals(2, savedAlerts.size(), "saveAll must be called with both OPEN alerts");
        assertTrue(savedAlerts.stream().allMatch(a -> a.getStatus() == AlertStatus.RESOLVED),
                "All OPEN alerts must have status set to RESOLVED after closeOpenAlerts (D-08)");
        assertTrue(savedAlerts.stream().allMatch(a -> a.getResolvedAt() != null),
                "All resolved alerts must have resolvedAt set");
    }
}
