package com.onconavigator.activity;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertGenerationActivityImpl}.
 *
 * <p>Tests verify standalone alert creation with deduplication (PATH-06):
 * <ul>
 *   <li>New alert is created and persisted when no duplicate exists</li>
 *   <li>Duplicate alert creation is skipped when an OPEN alert already exists (PATH-06)</li>
 *   <li>The correct AlertType is set based on the alertTypeStr parameter</li>
 * </ul>
 *
 * <p>PHI safety: All test parameters use non-PHI values (synthetic UUIDs, template text, step names).
 */
class AlertGenerationActivityTest {

    private AlertRepository alertRepository;
    private NotificationService notificationService;
    private PatientRepository patientRepository;
    private AlertGenerationActivityImpl activity;

    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String STEP_NAME = "Surgeon Consultation";
    private static final String DEVIATION_DESC = "Consultation not recorded within 14 days of diagnosis.";
    private static final String SUGGESTED_ACTION = "Contact surgeon to schedule consultation.";
    private static final String WORKFLOW_RUN_ID = "test-workflow-run-id-001";

    @BeforeEach
    void setUp() {
        alertRepository = Mockito.mock(AlertRepository.class);
        notificationService = Mockito.mock(NotificationService.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        activity = new AlertGenerationActivityImpl(alertRepository, notificationService, patientRepository);
    }

    /**
     * Test 1: New alert is created and saved when no duplicate exists.
     *
     * <p>When existsByPatientIdAndPathwayStepNameAndStatus returns false,
     * alertRepository.save() must be called with a properly populated Alert entity.
     */
    @Test
    void testGenerateAlert_createsNewAlert() {
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                PATIENT_ID, STEP_NAME, AlertStatus.OPEN)).thenReturn(false);

        activity.generateAlert(PATIENT_ID, STEP_NAME, "MISSING_EVENT",
                DEVIATION_DESC, SUGGESTED_ACTION, null, WORKFLOW_RUN_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertNotNull(savedAlert, "A new Alert entity must be saved");
        assertEquals(PATIENT_ID, savedAlert.getPatientId(),
                "Alert must be associated with the correct patient UUID");
        assertEquals(STEP_NAME, savedAlert.getPathwayStepName(),
                "Alert must reference the correct pathway step name");
        assertEquals(DEVIATION_DESC, savedAlert.getDeviationDescription(),
                "Alert must contain the deviation description");
        assertEquals(SUGGESTED_ACTION, savedAlert.getSuggestedAction(),
                "Alert must contain the suggested action");
        assertEquals(WORKFLOW_RUN_ID, savedAlert.getWorkflowRunId(),
                "Alert must record the workflow run ID for traceability");
    }

    /**
     * Test 2 (PATH-06): Duplicate alert creation is skipped when an OPEN alert already exists.
     *
     * <p>When existsByPatientIdAndPathwayStepNameAndStatus returns true for OPEN status,
     * alertRepository.save() must NOT be called. The method must return silently.
     */
    @Test
    void testGenerateAlert_skipsDuplicateAlert_PATH06() {
        // Simulate existing OPEN alert for this patient + step
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                PATIENT_ID, STEP_NAME, AlertStatus.OPEN)).thenReturn(true);

        activity.generateAlert(PATIENT_ID, STEP_NAME, "MISSING_EVENT",
                DEVIATION_DESC, SUGGESTED_ACTION, null, WORKFLOW_RUN_ID);

        // save() must never be called — duplicate suppressed (PATH-06)
        verify(alertRepository, never()).save(any(Alert.class));
    }

    /**
     * Test 3: The correct AlertType is set based on the alertTypeStr parameter.
     *
     * <p>AlertGenerationActivityImpl converts the String parameter to AlertType via
     * AlertType.valueOf(alertTypeStr). This test verifies DELAYED_EVENT is correctly set.
     */
    @Test
    void testGenerateAlert_setsCorrectAlertType() {
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                PATIENT_ID, STEP_NAME, AlertStatus.OPEN)).thenReturn(false);

        activity.generateAlert(PATIENT_ID, STEP_NAME, "DELAYED_EVENT",
                "Pathology report not received within the expected window.", SUGGESTED_ACTION, null, WORKFLOW_RUN_ID);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertEquals(AlertType.DELAYED_EVENT, savedAlert.getAlertType(),
                "Alert type must be DELAYED_EVENT when alertTypeStr is 'DELAYED_EVENT'");
    }
}
