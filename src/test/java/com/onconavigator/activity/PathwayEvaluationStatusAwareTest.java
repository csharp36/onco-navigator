package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 unit tests for status-aware evaluation logic in {@link PathwayEvaluationActivityImpl}.
 *
 * <p>Tests verify all four new alert types (CANCELLED_EVENT, DEADLINE_APPROACHING,
 * SCHEDULING_UNCONFIRMED, RESULTS_NOT_READY), the referralReceivedAt anchor fallback,
 * SCHEDULED/PENDING suppression of MISSING_EVENT, and the CANCELLED/DELAYED mutual
 * exclusion (Pitfall 7).
 *
 * <p>Follows the same manual Mockito.mock() pattern used in {@link PathwayEvaluationActivityImplTest}.
 *
 * <p>PHI safety: All test data uses synthetic UUIDs and placeholder strings. No real PHI.
 */
class PathwayEvaluationStatusAwareTest {

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

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATHWAY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
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

    private Patient createTestPatient(LocalDate diagnosisDate, OffsetDateTime referralReceivedAt) {
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setCancerType(CancerType.BREAST);
        patient.setDiagnosisDate(diagnosisDate);
        patient.setReferralReceivedAt(referralReceivedAt);
        patient.setCancerStage("IIA");
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        patient.setMrn("MRN-TEST");
        patient.setDateOfBirth("1970-01-01");
        return patient;
    }

    private PatientPathway createTestPathway() {
        PatientPathway pathway = new PatientPathway();
        pathway.setId(PATHWAY_ID);
        return pathway;
    }

    private PatientPathwayStep createActiveRootStep(UUID id, String name, CareEventType eventType,
                                                     int windowDays, PatientPathway pathway) {
        PatientPathwayStep step = new PatientPathwayStep();
        step.setId(id);
        step.setPathway(pathway);
        step.setName(name);
        step.setEventType(eventType);
        step.setWindowDays(windowDays);
        step.setStatus(PathwayStepStatus.ACTIVE);
        step.setRequired(true);
        step.setAlertText(null); // null triggers Claude/fallback path
        return step;
    }

    private CareEvent createCareEvent(CareEventType eventType, CareEventStatus status,
                                       LocalDate eventDate) {
        CareEvent event = new CareEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setStatus(status);
        event.setEventDate(eventDate);
        return event;
    }

    private void setupCommonMocks(Patient patient, PatientPathway pathway,
                                   List<PatientPathwayStep> activeSteps,
                                   List<CareEvent> careEvents) {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.ACTIVE))
                .thenReturn(activeSteps);
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.COMPLETED))
                .thenReturn(List.of());
        when(stepRepository.findByPathway_IdAndStatus(PATHWAY_ID, PathwayStepStatus.SKIPPED))
                .thenReturn(List.of());
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(careEventRepository.findByPatient_IdOrderByEventDateDesc(PATIENT_ID))
                .thenReturn(careEvents);
        when(alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(any(), any(), any()))
                .thenReturn(false);
    }

    // ---- Tests ----

    /**
     * D-03: When referralReceivedAt is set, it is used as root anchor instead of diagnosisDate.
     * 10 days since referral, 14-day window -> no MISSING_EVENT (10 < 14).
     */
    @Test
    void testReferralReceivedAtUsedAsRootAnchor() {
        OffsetDateTime referralAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        LocalDate diagnosisDate = LocalDate.now().minusDays(30); // would trigger MISSING_EVENT if used
        Patient patient = createTestPatient(diagnosisDate, referralAt);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, pathway);

        setupCommonMocks(patient, pathway, List.of(step), List.of());

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();
        // No MISSING_EVENT because anchor is referralReceivedAt (10 days) < windowDays (14)
        verify(alertRepository, never()).save(any(Alert.class));
    }

    /**
     * D-03 fallback: When referralReceivedAt is null, diagnosisDate is used.
     * 20 days since diagnosis, 14-day window -> MISSING_EVENT fires (20 > 14).
     */
    @Test
    void testDiagnosisDateFallbackWhenNoReferral() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(20);
        Patient patient = createTestPatient(diagnosisDate, null); // no referral
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, pathway);

        setupCommonMocks(patient, pathway, List.of(step), List.of());

        // AI service returns null -> fallback description used
        when(alertGenerationAiService.generateAlertDescription(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(null);

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getAlertType()).isEqualTo(AlertType.MISSING_EVENT);
    }

    /**
     * D-04: A SCHEDULED care event suppresses MISSING_EVENT for the same step.
     * Even though the time window is exceeded, MISSING_EVENT does not fire
     * because a SCHEDULED event exists.
     */
    @Test
    void testScheduledEventSuppressesMissingEvent() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(20);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, pathway);

        CareEvent scheduledEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.SCHEDULED, LocalDate.now().plusDays(5));
        // Ensure scheduling is confirmed to avoid SCHEDULING_UNCONFIRMED
        scheduledEvent.setSchedulingConfirmed(Boolean.TRUE);

        setupCommonMocks(patient, pathway, List.of(step), List.of(scheduledEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify no MISSING_EVENT was saved
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        List<Alert> savedAlerts = getSavedAlerts(alertCaptor);

        boolean hasMissingEvent = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.MISSING_EVENT);
        assertThat(hasMissingEvent).isFalse();
    }

    /**
     * D-05: A CANCELLED care event fires CANCELLED_EVENT immediately.
     */
    @Test
    void testCancelledEventFiresImmediateAlert() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(5);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 14, pathway);

        CareEvent cancelledEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.CANCELLED, LocalDate.now().minusDays(1));

        setupCommonMocks(patient, pathway, List.of(step), List.of(cancelledEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getAlertType()).isEqualTo(AlertType.CANCELLED_EVENT);
    }

    /**
     * Pitfall 7: CANCELLED_EVENT is mutually exclusive with DELAYED_EVENT.
     * Even if the time window is exceeded, only CANCELLED_EVENT fires.
     */
    @Test
    void testCancelledMutuallyExclusiveWithDelayed() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(10);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 5, pathway); // 10 > 5 = window exceeded

        CareEvent cancelledEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.CANCELLED, LocalDate.now().minusDays(2));

        setupCommonMocks(patient, pathway, List.of(step), List.of(cancelledEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(alertCaptor.capture());

        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getAlertType()).isEqualTo(AlertType.CANCELLED_EVENT);
        // No DELAYED_EVENT should exist
    }

    /**
     * D-06: DEADLINE_APPROACHING fires when time window expires within 48 hours (0-2 days left).
     * 10 days since diagnosis, 12-day window -> 2 days left -> fires.
     */
    @Test
    void testDeadlineApproachingFiresAt48Hours() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(10);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 12, pathway); // 12-10=2 days left

        setupCommonMocks(patient, pathway, List.of(step), List.of());

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        List<Alert> savedAlerts = alertCaptor.getAllValues();
        boolean hasDeadlineApproaching = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.DEADLINE_APPROACHING);
        assertThat(hasDeadlineApproaching).isTrue();
    }

    /**
     * D-11: SCHEDULING_UNCONFIRMED fires when scheduling is not confirmed 7+ days after referral
     * for root steps.
     */
    @Test
    void testSchedulingUnconfirmedAfter7DaysFromReferral() {
        OffsetDateTime referralAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        LocalDate diagnosisDate = LocalDate.now().minusDays(15);
        Patient patient = createTestPatient(diagnosisDate, referralAt);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 30, pathway); // wide window to avoid other alerts

        CareEvent scheduledEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.SCHEDULED, LocalDate.now().plusDays(5));
        scheduledEvent.setSchedulingConfirmed(Boolean.FALSE);
        scheduledEvent.setExternalFacilityName("City Hospital");

        setupCommonMocks(patient, pathway, List.of(step), List.of(scheduledEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        List<Alert> savedAlerts = alertCaptor.getAllValues();
        boolean hasSchedulingUnconfirmed = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.SCHEDULING_UNCONFIRMED);
        assertThat(hasSchedulingUnconfirmed).isTrue();
    }

    /**
     * D-11: When schedulingConfirmed is true, SCHEDULING_UNCONFIRMED does not fire.
     */
    @Test
    void testSchedulingConfirmedSuppressesUnconfirmedAlert() {
        OffsetDateTime referralAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        LocalDate diagnosisDate = LocalDate.now().minusDays(15);
        Patient patient = createTestPatient(diagnosisDate, referralAt);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Surgeon Consultation",
                CareEventType.CONSULTATION, 30, pathway);

        CareEvent scheduledEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.SCHEDULED, LocalDate.now().plusDays(5));
        scheduledEvent.setSchedulingConfirmed(Boolean.TRUE); // confirmed

        setupCommonMocks(patient, pathway, List.of(step), List.of(scheduledEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify no SCHEDULING_UNCONFIRMED alert was saved
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        List<Alert> savedAlerts = getSavedAlerts(alertCaptor);

        boolean hasSchedulingUnconfirmed = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.SCHEDULING_UNCONFIRMED);
        assertThat(hasSchedulingUnconfirmed).isFalse();
    }

    /**
     * D-08/D-09: RESULTS_NOT_READY fires when pending results are expected after an upcoming visit
     * within the 14-day lookahead window.
     */
    @Test
    void testResultsNotReadyFires() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(5);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        // Need an ACTIVE step so the code doesn't short-circuit
        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Initial Consultation",
                CareEventType.CONSULTATION, 30, pathway);

        // Upcoming visit: SCHEDULED CONSULTATION 5 days from now
        CareEvent visitEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.SCHEDULED, LocalDate.now().plusDays(5));

        // Pending result: SCHEDULED PATHOLOGY_REPORT with expectedCompletionDate 10 days from now
        // (after the visit)
        CareEvent resultEvent = createCareEvent(CareEventType.PATHOLOGY_REPORT,
                CareEventStatus.SCHEDULED, LocalDate.now().minusDays(2));
        resultEvent.setExpectedCompletionDate(LocalDate.now().plusDays(10));

        setupCommonMocks(patient, pathway, List.of(step),
                List.of(visitEvent, resultEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(alertCaptor.capture());

        List<Alert> savedAlerts = alertCaptor.getAllValues();
        boolean hasResultsNotReady = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.RESULTS_NOT_READY);
        assertThat(hasResultsNotReady).isTrue();

        // Verify the sentinel step name
        Alert rnrAlert = savedAlerts.stream()
                .filter(a -> a.getAlertType() == AlertType.RESULTS_NOT_READY)
                .findFirst()
                .orElseThrow();
        assertThat(rnrAlert.getPathwayStepName()).isEqualTo("__RESULTS_NOT_READY__");
    }

    /**
     * D-08/D-09 negative: RESULTS_NOT_READY does NOT fire when results are expected before the visit.
     */
    @Test
    void testResultsNotReadyDoesNotFireWhenResultsBeforeVisit() {
        LocalDate diagnosisDate = LocalDate.now().minusDays(5);
        Patient patient = createTestPatient(diagnosisDate, null);
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep step = createActiveRootStep(STEP1_ID, "Initial Consultation",
                CareEventType.CONSULTATION, 30, pathway);

        // Upcoming visit: SCHEDULED CONSULTATION 5 days from now
        CareEvent visitEvent = createCareEvent(CareEventType.CONSULTATION,
                CareEventStatus.SCHEDULED, LocalDate.now().plusDays(5));

        // Pending result: expected BEFORE the visit (3 days from now < 5 days)
        CareEvent resultEvent = createCareEvent(CareEventType.PATHOLOGY_REPORT,
                CareEventStatus.SCHEDULED, LocalDate.now().minusDays(2));
        resultEvent.setExpectedCompletionDate(LocalDate.now().plusDays(3));

        setupCommonMocks(patient, pathway, List.of(step),
                List.of(visitEvent, resultEvent));

        PathwayEvaluationResult result = activity.evaluate(PATIENT_ID);

        assertThat(result).isNotNull();

        // Verify no RESULTS_NOT_READY alert
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        List<Alert> savedAlerts = getSavedAlerts(alertCaptor);

        boolean hasResultsNotReady = savedAlerts.stream()
                .anyMatch(a -> a.getAlertType() == AlertType.RESULTS_NOT_READY);
        assertThat(hasResultsNotReady).isFalse();
    }

    // ---- Helper for safely capturing alerts (may have zero saves) ----

    private List<Alert> getSavedAlerts(ArgumentCaptor<Alert> captor) {
        try {
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            return captor.getAllValues();
        } catch (AssertionError e) {
            // No saves occurred — return empty list
            return List.of();
        }
    }
}
