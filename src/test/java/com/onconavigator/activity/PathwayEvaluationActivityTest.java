package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.ai.service.AlertGenerationAiService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PathwayEvaluationActivityImpl}.
 *
 * <p>All repositories are Mockito mocks — no database or Spring context required.
 * Tests verify the deviation detection logic directly against controlled fixture data.
 *
 * <p>Updated in Phase 6 to use per-patient DAG pathway model (PatientPathwayStep/Edge)
 * instead of the previous template-based model (PathwayTemplateRepository).
 *
 * <p>Test cases cover:
 * <ul>
 *   <li>PATH-03: MISSING_EVENT alert created when a required step has no care event and time has elapsed</li>
 *   <li>PATH-04: DELAYED_EVENT alert created when an event exists but is not COMPLETED and time has elapsed</li>
 *   <li>PATH-05: OUT_OF_ORDER alert created when an event exists for a step whose prerequisites are incomplete</li>
 *   <li>PATH-06: Duplicate alert suppressed when an OPEN alert already exists for (patient, step)</li>
 *   <li>PATH-07: evaluate() returns a non-null PathwayEvaluationResult</li>
 *   <li>D-09: allStepsComplete is true when no ACTIVE steps remain</li>
 *   <li>D-08: closeOpenAlerts sets status to RESOLVED for all OPEN alerts</li>
 * </ul>
 *
 * <p>PHI safety: All test data uses synthetic UUIDs and placeholder strings. No real PHI.
 */
class PathwayEvaluationActivityTest {

    private PatientRepository patientRepository;
    private CareEventRepository careEventRepository;
    private AlertRepository alertRepository;
    private PatientPathwayRepository pathwayRepository;
    private PatientPathwayStepRepository stepRepository;
    private PatientPathwayEdgeRepository edgeRepository;
    private ObjectMapper objectMapper;
    private AlertGenerationAiService alertGenerationAiService;
    private PathwayEvaluationActivityImpl activity;

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATHWAY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

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
        activity = new PathwayEvaluationActivityImpl(
                patientRepository, careEventRepository, alertRepository,
                pathwayRepository, stepRepository, edgeRepository,
                objectMapper, alertGenerationAiService);
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

    /** Build a PatientPathway linked to the test patient. */
    private PatientPathway createTestPathway() {
        PatientPathway pathway = new PatientPathway();
        pathway.setId(PATHWAY_ID);
        return pathway;
    }

    /**
     * Build a PatientPathwayStep with ACTIVE status, the given event type, windowDays,
     * and alertText. Uses a fixed UUID for predictable test setup.
     */
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

    /** Build a CareEvent with the given type, status, and event date. */
    private CareEvent createTestEvent(CareEventType type, CareEventStatus status, LocalDate date) {
        CareEvent event = new CareEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setStatus(status);
        event.setEventDate(date);
        return event;
    }

    private static final UUID STEP1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STEP2_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /**
     * Test 1 (PATH-03): Scenario A — Missing event.
     *
     * <p>Patient diagnosed 35 days ago. Step 1 (ACTIVE, CONSULTATION, windowDays=14) has no
     * matching care event. A MISSING_EVENT alert must be created.
     */
    @Test
    void testMissingEventDetected_PATH03() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step1 = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14,
                "Surgeon consultation not recorded within 14 days of diagnosis.", pathway);

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step1));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of());
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);
        assertFalse(result.allStepsComplete(), "allStepsComplete must be false when events are missing");

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.MISSING_EVENT),
                "A MISSING_EVENT alert must be created for a step with no care event past the window");
    }

    /**
     * Test 2 (PATH-04): Scenario C — Delayed event.
     *
     * <p>Step 1 (CONSULTATION) is COMPLETED; Step 2 (PATHOLOGY_REPORT) is ACTIVE but has
     * a PENDING care event and 18 days have elapsed (windowDays=14). A DELAYED_EVENT alert
     * must be created for step 2.
     */
    @Test
    void testDelayedEventDetected_PATH04() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(50);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        // Step 2 is ACTIVE; step 1 is COMPLETED (serves as anchor via edge)
        PatientPathwayStep step1Completed = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Consultation alert text", pathway);
        step1Completed.setStatus(PathwayStepStatus.COMPLETED);

        PatientPathwayStep step2 = createActiveStep(STEP2_ID, "Pathology Report",
                CareEventType.PATHOLOGY_REPORT, 14,
                "Pathology report not received within 14 days.", pathway);

        // Edge: step1 -> step2 (step2 depends on step1)
        PatientPathwayEdge edge = new PatientPathwayEdge();
        edge.setId(UUID.randomUUID());
        edge.setPathway(pathway);
        edge.setSourceStepId(STEP1_ID);
        edge.setTargetStepId(STEP2_ID);

        // Step 2 has a PENDING care event — not yet COMPLETED and past the 14-day window
        LocalDate step1CompletedDate = LocalDate.now().minusDays(18);
        CareEvent step2Event = createTestEvent(CareEventType.PATHOLOGY_REPORT,
                CareEventStatus.PENDING, step1CompletedDate.plusDays(1));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step2));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of(step1Completed));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of(edge));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of(step2Event));
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.DELAYED_EVENT),
                "A DELAYED_EVENT alert must be created for a PENDING event that is past the window");
    }

    /**
     * Test 3 (PATH-05): Scenario B — Out-of-order event.
     *
     * <p>In the Phase 6 DAG evaluation model, OUT_OF_ORDER is detected for a "ready" step
     * that has a matching care event, but whose prerequisite step is not COMPLETED.
     *
     * <p>Setup:
     * <ul>
     *   <li>Step 1: SKIPPED (satisfies step2's readiness check, but is NOT COMPLETED)</li>
     *   <li>Step 2: ACTIVE, prerequisite=step1. Ready because step1 is SKIPPED.</li>
     *   <li>Step 2 has a COMPLETED PATHOLOGY_REPORT care event (hasMatch=true)</li>
     * </ul>
     *
     * <p>OUT_OF_ORDER fires because step2 has a match AND step1 (its prerequisite) is NOT
     * in completedStepIds (only in skippedStepIds). This represents the "did step 2 before
     * step 1 was formally completed" scenario.
     */
    @Test
    void testOutOfOrderDetected_PATH05() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(20);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        // Step 1 is SKIPPED — satisfies step2's readiness check but is NOT COMPLETED
        PatientPathwayStep step1Skipped = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Consultation alert text", pathway);
        step1Skipped.setStatus(PathwayStepStatus.SKIPPED);

        // Step 2 is ACTIVE with a prerequisite of step1
        PatientPathwayStep step2 = createActiveStep(STEP2_ID, "Pathology Report",
                CareEventType.PATHOLOGY_REPORT, 14, "Pathology report alert text", pathway);

        // Edge: step1 -> step2 (step2 depends on step1)
        PatientPathwayEdge edge = new PatientPathwayEdge();
        edge.setId(UUID.randomUUID());
        edge.setPathway(pathway);
        edge.setSourceStepId(STEP1_ID);
        edge.setTargetStepId(STEP2_ID);

        // Step 2 has a COMPLETED PATHOLOGY_REPORT care event (hasMatch = true)
        CareEvent step2Event = createTestEvent(CareEventType.PATHOLOGY_REPORT,
                CareEventStatus.COMPLETED, LocalDate.now().minusDays(5));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step2)); // step2 is the only ACTIVE step
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of()); // step1 is SKIPPED, not COMPLETED
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of(step1Skipped));
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of(edge));
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(List.of(step2Event));
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();
        assertTrue(savedAlerts.stream().anyMatch(a -> a.getAlertType() == AlertType.OUT_OF_ORDER),
                "An OUT_OF_ORDER alert must be created when step 2 has a completed event but step 1 (prerequisite) is SKIPPED not COMPLETED");
    }

    /**
     * Test 4: Duplicate alert suppressed when an OPEN alert already exists.
     *
     * <p>Same scenario as test 1 (missing event), but the dedup check returns true for step 1.
     * alertRepository.save() must NOT be called for that step (PATH-06).
     */
    @Test
    void testDuplicateAlertNotCreated_PATH06() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(35);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step1 = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Alert text", pathway);

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step1));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of());
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        // Dedup: an OPEN alert already exists for step 1
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                PATIENT_ID, "Surgeon Consultation", AlertStatus.OPEN)).thenReturn(true);

        activity.evaluate(PATIENT_ID);

        // alertRepository.save() must NOT be called (duplicate suppressed)
        verify(alertRepository, never()).save(argThat(a ->
                "Surgeon Consultation".equals(a.getPathwayStepName())));
    }

    /**
     * Test 5 (PATH-07): evaluate() returns a non-null PathwayEvaluationResult.
     *
     * <p>Patient diagnosed 5 days ago (within window). No alerts should be generated.
     */
    @Test
    void testEvaluationLogsResult_PATH07() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(5);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step1 = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Alert text", pathway);

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of(step1));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of());
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID)).thenReturn(List.of());
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any())).thenReturn(false);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result, "evaluate() must return a non-null PathwayEvaluationResult (PATH-07)");
        assertNotNull(result.alertsGenerated(),
                "alertsGenerated list must not be null in PathwayEvaluationResult");
    }

    /**
     * Test 6 (D-09): allStepsComplete returns true when no ACTIVE steps remain.
     *
     * <p>When all steps are COMPLETED, stepRepository returns empty ACTIVE list and
     * non-empty COMPLETED list. allStepsComplete must be true.
     */
    @Test
    void testAllStepsComplete_returnsTrue() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(50);
        Patient patient = createTestPatient(CancerType.BREAST, diagnosisDate);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step1Completed = createActiveStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, "Alert text", pathway);
        step1Completed.setStatus(PathwayStepStatus.COMPLETED);

        PatientPathwayStep step2Completed = createActiveStep(STEP2_ID, "Pathology Report",
                CareEventType.PATHOLOGY_REPORT, 14, "Alert text", pathway);
        step2Completed.setStatus(PathwayStepStatus.COMPLETED);

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        // No ACTIVE steps remain
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(List.of());
        // But there ARE steps in the pathway (non-empty allSteps check via findByPathway_Id)
        when(stepRepository.findByPathway_Id(PATHWAY_ID))
                .thenReturn(List.of(step1Completed, step2Completed));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result, "evaluate() must return a non-null result");
        assertTrue(result.allStepsComplete(),
                "allStepsComplete must be true when all pathway steps are COMPLETED (D-09)");

        // No alerts should be generated when all steps are complete
        verify(alertRepository, never()).save(any(Alert.class));
    }

    /**
     * Test 7 (D-08): closeOpenAlerts sets status to RESOLVED for all OPEN alerts.
     *
     * <p>When a patient is deactivated, all OPEN alerts must be resolved.
     */
    @Test
    void testCloseOpenAlerts_setsResolvedStatus() {
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

    /**
     * Test 8: No pathway found — returns empty result without evaluation.
     */
    @Test
    void testNoPathway_returnsEmptyResult() {
        Patient patient = createTestPatient(CancerType.BREAST, LocalDate.now().minusDays(30));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.empty());

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertNotNull(result);
        assertFalse(result.allStepsComplete());
        assertTrue(result.alertsGenerated().isEmpty());
        verify(alertRepository, never()).save(any(Alert.class));
    }
}
