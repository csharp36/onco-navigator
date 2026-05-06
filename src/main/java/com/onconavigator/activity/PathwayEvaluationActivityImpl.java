package com.onconavigator.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.ai.model.AlertText;
import com.onconavigator.ai.service.AlertGenerationAiService;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evaluates a patient's per-patient pathway DAG, detecting missing, delayed, and
 * out-of-order events. All database access happens here — the workflow passes only the patient UUID.
 *
 * <p>PHI note: This activity logs only patient UUIDs and step UUIDs. Never log patient names,
 * DOBs, or MRNs. PHI fields on {@link Patient} are not referenced in any log statement.
 *
 * <p>Deviation detection (Phase 5 DAG-based, Phase 7 status-aware):
 * <ul>
 *   <li>OUT_OF_ORDER: A care event exists for a step whose prerequisites are not yet completed</li>
 *   <li>CANCELLED_EVENT: A care event for a step has been cancelled (immediate corrective alert)</li>
 *   <li>DELAYED_EVENT: A SCHEDULED/PENDING event's expectedCompletionDate has passed</li>
 *   <li>DEADLINE_APPROACHING: A step's time window expires within 48 hours</li>
 *   <li>SCHEDULING_UNCONFIRMED: Scheduling not confirmed 7 days after referral (root) or eventDate</li>
 *   <li>MISSING_EVENT: Required ACTIVE step, no matching care event, time window exceeded</li>
 *   <li>RESULTS_NOT_READY: Pending results expected after an upcoming visit within 14 days</li>
 * </ul>
 *
 * <p>Step readiness (D-11): A step is "ready" for evaluation only when ALL prerequisite
 * steps have status COMPLETED or SKIPPED. Root steps (no prerequisites) anchor to
 * the patient's referralReceivedAt (with diagnosisDate fallback per D-03). Steps with
 * prerequisites anchor to the LATEST prerequisite completion date.
 *
 * <p>Deduplication: Before creating any alert, an existence check confirms no OPEN alert
 * already exists for (patient, step name). Idempotent — safe for Temporal retries.
 */
@Component
public class PathwayEvaluationActivityImpl implements PathwayEvaluationActivity {

    private static final Logger log = LoggerFactory.getLogger(PathwayEvaluationActivityImpl.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final AlertRepository alertRepository;
    private final PatientPathwayRepository pathwayRepository;
    private final PatientPathwayStepRepository stepRepository;
    private final PatientPathwayEdgeRepository edgeRepository;
    private final ObjectMapper objectMapper;
    private final AlertGenerationAiService alertGenerationAiService;
    private final NotificationService notificationService;

    public PathwayEvaluationActivityImpl(
            PatientRepository patientRepository,
            CareEventRepository careEventRepository,
            AlertRepository alertRepository,
            PatientPathwayRepository pathwayRepository,
            PatientPathwayStepRepository stepRepository,
            PatientPathwayEdgeRepository edgeRepository,
            ObjectMapper objectMapper,
            AlertGenerationAiService alertGenerationAiService,
            NotificationService notificationService) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.alertRepository = alertRepository;
        this.pathwayRepository = pathwayRepository;
        this.stepRepository = stepRepository;
        this.edgeRepository = edgeRepository;
        this.objectMapper = objectMapper;
        this.alertGenerationAiService = alertGenerationAiService;
        this.notificationService = notificationService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluation order per ready step:
     * <ol>
     *   <li>Query per-patient ACTIVE steps from relational tables</li>
     *   <li>Load DAG edges to determine prerequisite relationships</li>
     *   <li>Identify "ready" steps: ACTIVE steps where all prerequisites are COMPLETED or SKIPPED</li>
     *   <li>For each ready step: detect OUT_OF_ORDER, MISSING_EVENT, DELAYED_EVENT deviations</li>
     *   <li>Time window anchors to latest prerequisite completion date; root steps use diagnosis date</li>
     * </ol>
     */
    @Override
    @Transactional
    public PathwayEvaluationResult evaluate(UUID patientId) {
        // 1. Fetch patient
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        // 2. Find per-patient pathway
        PatientPathway pathway = pathwayRepository.findByPatient_Id(patientId).orElse(null);

        // Empty pathway (D-06 "Build from documents"): no steps to evaluate
        if (pathway == null) {
            log.info("No pathway found for patient {}, skipping evaluation", patientId);
            return new PathwayEvaluationResult(false, List.of());
        }

        // 3. Query ACTIVE steps only (PROPOSED steps skipped, SKIPPED steps skipped, COMPLETED already done)
        List<PatientPathwayStep> activeSteps = stepRepository.findByPathway_IdAndStatus(
                pathway.getId(), PathwayStepStatus.ACTIVE);

        if (activeSteps.isEmpty()) {
            // All steps are either COMPLETED, SKIPPED, or PROPOSED
            // Check if any steps exist at all and whether they are all terminal
            List<PatientPathwayStep> allSteps = stepRepository.findByPathway_Id(pathway.getId());
            boolean allComplete = !allSteps.isEmpty() && allSteps.stream()
                    .allMatch(s -> s.getStatus() == PathwayStepStatus.COMPLETED
                               || s.getStatus() == PathwayStepStatus.SKIPPED);
            return new PathwayEvaluationResult(allComplete, List.of());
        }

        // 4. Query edges for the pathway
        List<PatientPathwayEdge> edges = edgeRepository.findByPathway_Id(pathway.getId());

        // 5. Build set of COMPLETED and SKIPPED step IDs (for prerequisite resolution)
        List<PatientPathwayStep> completedSteps = stepRepository.findByPathway_IdAndStatus(
                pathway.getId(), PathwayStepStatus.COMPLETED);
        Set<UUID> completedStepIds = completedSteps.stream()
                .map(PatientPathwayStep::getId).collect(Collectors.toSet());

        // SKIPPED steps are treated as "satisfied" prerequisites
        List<PatientPathwayStep> skippedSteps = stepRepository.findByPathway_IdAndStatus(
                pathway.getId(), PathwayStepStatus.SKIPPED);
        Set<UUID> satisfiedStepIds = new HashSet<>(completedStepIds);
        skippedSteps.forEach(s -> satisfiedStepIds.add(s.getId()));

        // 6. Build prerequisite map: for each target step, find its prerequisite step IDs
        Map<UUID, Set<UUID>> prerequisites = new HashMap<>();
        for (PatientPathwayEdge edge : edges) {
            prerequisites.computeIfAbsent(edge.getTargetStepId(), k -> new HashSet<>())
                    .add(edge.getSourceStepId());
        }

        // 7. Identify "ready" steps: ACTIVE steps where ALL prerequisites are satisfied
        List<PatientPathwayStep> readySteps = activeSteps.stream()
                .filter(step -> {
                    Set<UUID> prereqs = prerequisites.getOrDefault(step.getId(), Set.of());
                    return prereqs.isEmpty() || satisfiedStepIds.containsAll(prereqs);
                })
                .toList();

        // 8. Fetch all care events for the patient
        List<CareEvent> careEvents = careEventRepository.findByPatient_IdOrderByEventDateDesc(patientId);

        // Build event type -> completed events map
        Map<CareEventType, List<CareEvent>> completedEventsByType = careEvents.stream()
                .filter(e -> e.getStatus() == CareEventStatus.COMPLETED)
                .collect(Collectors.groupingBy(CareEvent::getEventType));

        // All care events indexed by eventType (includes non-completed) — for Phase 7 status-aware branching
        Map<CareEventType, List<CareEvent>> allEventsByType = careEvents.stream()
                .collect(Collectors.groupingBy(CareEvent::getEventType));

        // 9. For each ready step, detect deviations
        List<String> alertsGenerated = new ArrayList<>();
        for (PatientPathwayStep step : readySteps) {
            if (step.getEventType() == null) continue; // Steps without event type skip matching

            // Find matching care events (by eventType)
            List<CareEvent> matchingCompleted = completedEventsByType.getOrDefault(step.getEventType(), List.of());

            // Check if step already has an explicit link or has a matching completed event
            boolean hasMatch = step.getCompletedCareEventId() != null || !matchingCompleted.isEmpty();

            // Resolve anchor date (D-11):
            // - Root steps (no prerequisites): anchor to diagnosis date
            // - Steps with prerequisites: anchor to LATEST prerequisite completion date
            Set<UUID> prereqs = prerequisites.getOrDefault(step.getId(), Set.of());
            LocalDate anchorDate;
            if (prereqs.isEmpty()) {
                anchorDate = resolveRootAnchor(patient); // D-03: referral date primary, diagnosis fallback
            } else {
                anchorDate = completedSteps.stream()
                        .filter(cs -> prereqs.contains(cs.getId()))
                        .map(cs -> cs.getCompletedAt() != null ? cs.getCompletedAt().toLocalDate() : null)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(patient.getDiagnosisDate()); // Fallback to diagnosis date
            }

            if (anchorDate == null) continue; // Cannot evaluate without anchor

            long daysSinceAnchor = ChronoUnit.DAYS.between(anchorDate, LocalDate.now());

            // OUT_OF_ORDER: step has a care event but not all prerequisites are completed
            if (hasMatch && !prereqs.isEmpty() && !completedStepIds.containsAll(prereqs)) {
                String summary = createAlertIfNotDuplicate(patient, step, AlertType.OUT_OF_ORDER,
                        "Out of order: " + step.getName() + " completed before prerequisites");
                if (summary != null) {
                    alertsGenerated.add(summary);
                }
                // One alert type per step per cycle — skip MISSING/DELAYED for the same step
                continue;
            }

            // ---- Phase 7: Status-aware evaluation (replaces flat MISSING/DELAYED blocks) ----

            // Find the best matching care event for this step's eventType (prefer non-COMPLETED to detect in-progress)
            List<CareEvent> stepEvents = allEventsByType.getOrDefault(step.getEventType(), List.of());
            CareEvent activeEvent = stepEvents.stream()
                    .filter(e -> e.getStatus() != CareEventStatus.COMPLETED)
                    .findFirst()
                    .orElse(null);

            if (activeEvent != null) {
                CareEventStatus eventStatus = activeEvent.getStatus();

                if (eventStatus == CareEventStatus.CANCELLED) {
                    // D-05: CANCELLED triggers immediate corrective alert
                    String desc = step.getName() + " was cancelled. Reschedule or update the patient's pathway.";
                    String summary = createAlertIfNotDuplicate(patient, step, AlertType.CANCELLED_EVENT, desc);
                    if (summary != null) alertsGenerated.add(summary);
                    continue; // Pitfall 7: mutually exclusive with DELAYED_EVENT
                }

                if (eventStatus == CareEventStatus.SCHEDULED || eventStatus == CareEventStatus.PENDING) {
                    // D-04: Suppress MISSING_EVENT — step is in progress

                    // D-06: DEADLINE_APPROACHING check (48-hour warning before window expires)
                    if (step.getWindowDays() != null && anchorDate != null) {
                        long daysLeft = step.getWindowDays() - daysSinceAnchor;
                        if (daysLeft >= 0 && daysLeft <= 2) {
                            String desc = step.getName() + " is due within 48 hours. Window expires in " + daysLeft + " day(s).";
                            String summary = createAlertIfNotDuplicate(patient, step, AlertType.DEADLINE_APPROACHING, desc);
                            if (summary != null) alertsGenerated.add(summary);
                        }
                    }

                    // D-04: DELAYED via expectedCompletionDate — past-due SCHEDULED/PENDING fires DELAYED_EVENT
                    if (activeEvent.getExpectedCompletionDate() != null
                            && LocalDate.now().isAfter(activeEvent.getExpectedCompletionDate())) {
                        String desc = "Delayed: " + step.getName() + " (expected by " + activeEvent.getExpectedCompletionDate() + ")";
                        String summary = createAlertIfNotDuplicate(patient, step, AlertType.DELAYED_EVENT, desc);
                        if (summary != null) alertsGenerated.add(summary);
                    } else if (step.getWindowDays() != null && anchorDate != null
                            && daysSinceAnchor > step.getWindowDays()) {
                        // Window-based DELAYED: event exists but still PENDING/SCHEDULED past the window
                        String desc = "Delayed: " + step.getName() + " (past " + step.getWindowDays() + "-day window, still " + eventStatus + ")";
                        String summary = createAlertIfNotDuplicate(patient, step, AlertType.DELAYED_EVENT, desc);
                        if (summary != null) alertsGenerated.add(summary);
                    }

                    // D-11/D-12: SCHEDULING_UNCONFIRMED — 7-day clock from referral (root) or eventDate (subsequent)
                    boolean notConfirmed = !Boolean.TRUE.equals(activeEvent.getSchedulingConfirmed());
                    if (notConfirmed) {
                        LocalDate confirmDeadline;
                        if (prereqs.isEmpty() && patient.getReferralReceivedAt() != null) {
                            // D-11: Initial referral — 7 days from referral_received_at
                            confirmDeadline = patient.getReferralReceivedAt().toLocalDate().plusDays(7);
                        } else {
                            // D-12: Subsequent procedures — 7 days from care event eventDate
                            confirmDeadline = activeEvent.getEventDate() != null
                                    ? activeEvent.getEventDate().plusDays(7) : null;
                        }
                        if (confirmDeadline != null && LocalDate.now().isAfter(confirmDeadline)) {
                            String facilityInfo = activeEvent.getExternalFacilityName() != null
                                    ? activeEvent.getExternalFacilityName() : "the outside facility";
                            String desc = "Scheduling not confirmed with " + facilityInfo + " for " + step.getName() + ".";
                            String summary = createAlertIfNotDuplicate(patient, step, AlertType.SCHEDULING_UNCONFIRMED, desc);
                            if (summary != null) alertsGenerated.add(summary);
                        }
                    }

                    continue; // Step is in progress — no MISSING_EVENT
                }
            } else {
                // No non-completed event found for this step's eventType
                // Check if step has a completed event (already satisfied)
                boolean hasCompletedEvent = completedEventsByType.containsKey(step.getEventType())
                        && !completedEventsByType.get(step.getEventType()).isEmpty();

                if (!hasCompletedEvent) {
                    // MISSING_EVENT check (existing logic, preserved)
                    if (step.isRequired() && step.getWindowDays() != null
                            && anchorDate != null && daysSinceAnchor > step.getWindowDays()) {
                        String desc = "Missing: " + step.getName() + " (expected within " + step.getWindowDays() + " days)";
                        String summary = createAlertIfNotDuplicate(patient, step, AlertType.MISSING_EVENT, desc);
                        if (summary != null) alertsGenerated.add(summary);
                    } else if (step.getWindowDays() != null && anchorDate != null) {
                        // D-06: DEADLINE_APPROACHING for steps with no event at all
                        long daysLeft = step.getWindowDays() - daysSinceAnchor;
                        if (daysLeft >= 0 && daysLeft <= 2) {
                            String desc = step.getName() + " window expires in " + daysLeft + " day(s). No event recorded yet.";
                            String summary = createAlertIfNotDuplicate(patient, step, AlertType.DEADLINE_APPROACHING, desc);
                            if (summary != null) alertsGenerated.add(summary);
                        }
                    }
                }
            }
        }

        // ---- D-08/D-09: Cross-event RESULTS_NOT_READY check (once per patient, not per step) ----
        LocalDate today = LocalDate.now();
        LocalDate lookaheadCutoff = today.plusDays(14); // D-09: 14-day lookahead

        // Upcoming visits within 14 days: SCHEDULED/PENDING CONSULTATION or FOLLOW_UP events
        List<CareEvent> upcomingVisits = careEvents.stream()
                .filter(e -> e.getEventType() == CareEventType.CONSULTATION
                          || e.getEventType() == CareEventType.FOLLOW_UP)
                .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED
                          || e.getStatus() == CareEventStatus.PENDING)
                .filter(e -> e.getEventDate() != null
                          && !e.getEventDate().isBefore(today)
                          && !e.getEventDate().isAfter(lookaheadCutoff))
                .toList();

        if (!upcomingVisits.isEmpty()) {
            LocalDate earliestVisit = upcomingVisits.stream()
                    .map(CareEvent::getEventDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            if (earliestVisit != null) {
                boolean resultsNotReady = careEvents.stream()
                        .filter(e -> e.getEventType() == CareEventType.PATHOLOGY_REPORT
                                  || e.getEventType() == CareEventType.LAB_WORK
                                  || e.getEventType() == CareEventType.IMAGING)
                        .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED
                                  || e.getStatus() == CareEventStatus.PENDING)
                        .filter(e -> e.getExpectedCompletionDate() != null)
                        .anyMatch(e -> e.getExpectedCompletionDate().isAfter(earliestVisit));

                if (resultsNotReady) {
                    // Patient-level alert: use sentinel step name for dedup (Pitfall 4)
                    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                            patient.getId(), "__RESULTS_NOT_READY__", AlertStatus.OPEN);
                    if (!isDuplicate) {
                        String rnrDesc = "Pending test results are not expected before an upcoming visit. "
                                + "Review with physician whether the visit should proceed or be rescheduled.";
                        String rnrAction = "Contact the ordering facility for an estimated result date.";
                        String rnrMissing = cap150(rnrDesc, "missingSummary", patient.getId());

                        Alert rnrAlert = new Alert();
                        rnrAlert.setPatientId(patient.getId());
                        rnrAlert.setAlertType(AlertType.RESULTS_NOT_READY);
                        rnrAlert.setPathwayStepName("__RESULTS_NOT_READY__");
                        rnrAlert.setDeviationDescription(rnrDesc);
                        rnrAlert.setSuggestedAction(cap150(rnrAction, "suggestedAction", patient.getId()));
                        rnrAlert.setMissingSummary(rnrMissing);
                        rnrAlert.setStatus(AlertStatus.OPEN);
                        alertRepository.save(rnrAlert);

                        // Dispatch notification (D-06)
                        notificationService.dispatchForAlert(rnrAlert,
                                patient.getFirstName() + " " + patient.getLastName(),
                                patient.getMrn());

                        alertsGenerated.add("RESULTS_NOT_READY: patient " + patient.getId());
                        log.info("ALERT_CREATED: patient={} type=RESULTS_NOT_READY", patient.getId());
                    }
                }
            }
        }

        boolean allStepsComplete = activeSteps.isEmpty();
        log.info("PATHWAY_EVALUATION: patient={} readySteps={} alertsGenerated={} allComplete={}",
                patientId, readySteps.size(), alertsGenerated.size(), allStepsComplete);

        return new PathwayEvaluationResult(allStepsComplete, alertsGenerated);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves open alerts to RESOLVED status when a patient is deactivated (D-08).
     * Resolution is logged without PHI — only the count and patient UUID.
     */
    @Override
    @Transactional
    public void closeOpenAlerts(UUID patientId) {
        List<Alert> openAlerts = alertRepository.findByPatientIdAndStatus(patientId, AlertStatus.OPEN);
        OffsetDateTime now = OffsetDateTime.now();
        for (Alert alert : openAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alert.setResolutionNotes("Patient deactivated -- workflow cancelled");
        }
        alertRepository.saveAll(openAlerts);
        log.info("Closed {} open alerts for deactivated patient {}", openAlerts.size(), patientId);
    }

    // ---- Private helpers ----

    /**
     * Creates an alert if no open alert already exists for this patient and step name.
     *
     * <p>Deduplication uses {@code existsByPatientIdAndPathwayStepNameAndStatus} to
     * avoid creating duplicate OPEN alerts on Temporal retries.
     *
     * @param patient            the patient entity
     * @param step               the pathway step in deviation
     * @param alertType          the type of deviation detected
     * @param defaultDescription fallback description if AI generation fails
     * @return alert summary string if created, null if duplicate
     */
    private String createAlertIfNotDuplicate(Patient patient, PatientPathwayStep step,
            AlertType alertType, String defaultDescription) {
        boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                patient.getId(), step.getName(), AlertStatus.OPEN);
        if (isDuplicate) return null;

        // Determine alert text from three sources: template, Claude AI, or fallback
        String description;
        String missingSummary;
        String suggestedAction;

        if (step.getAlertText() != null && !step.getAlertText().isBlank()) {
            // AI-01: Template-first path — use step's configured alert text
            description = step.getAlertText();
            missingSummary = description.length() > 150
                    ? description.substring(0, 150).trim() : description;
            suggestedAction = step.getSuggestedAction() != null
                    ? step.getSuggestedAction() : "Review patient pathway and take corrective action.";
        } else {
            // AI-02/AI-03: Try Claude for generated text (ZERO-PHI boundary)
            AlertText claudeText = alertGenerationAiService.generateAlertDescription(
                    patient.getCancerType().name(),
                    step.getName(),
                    alertType.name(),
                    step.getWindowDays() != null ? String.valueOf(step.getWindowDays()) : "unknown",
                    List.of(),
                    List.of()
            );

            if (claudeText != null) {
                // Claude-generated path
                log.info("ALERT_CLAUDE_GENERATED: patient={} step={}", patient.getId(), step.getId());
                description = claudeText.deviationDescription();
                missingSummary = claudeText.missingSummary();
                suggestedAction = claudeText.suggestedAction();
            } else {
                // AI-04: Circuit breaker fallback — use default description
                log.info("ALERT_FALLBACK_TEMPLATE: patient={} step={}", patient.getId(), step.getId());
                description = defaultDescription;
                missingSummary = defaultDescription.length() > 150
                        ? defaultDescription.substring(0, 150).trim() : defaultDescription;
                suggestedAction = "Review patient pathway and take corrective action.";
            }
        }

        Alert alert = new Alert();
        alert.setPatientId(patient.getId());
        alert.setAlertType(alertType);
        alert.setPathwayStepName(step.getName());
        alert.setDeviationDescription(description);
        alert.setSuggestedAction(cap150(suggestedAction, "suggestedAction", patient.getId()));
        alert.setMissingSummary(cap150(missingSummary, "missingSummary", patient.getId()));
        alert.setStatus(AlertStatus.OPEN);
        alertRepository.save(alert);

        // Dispatch notification (D-06: immediate after save)
        notificationService.dispatchForAlert(alert,
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getMrn());

        log.info("ALERT_CREATED: patient={} step={} type={}", alertType, patient.getId(), step.getId());
        return alertType.name() + ": step '" + step.getName() + "' for patient " + patient.getId();
    }

    /**
     * Truncates a string to 150 characters with a warning log if truncation occurs.
     * Enforces the PW-ALL-007 constraint: both missingSummary and suggestedAction
     * must be at most 150 characters.
     *
     * @param value     the string to cap
     * @param fieldName the field name for logging
     * @param patientId the patient UUID for logging
     * @return the capped string, or null if value was null
     */
    private String cap150(String value, String fieldName, UUID patientId) {
        if (value == null) return null;
        if (value.length() > 150) {
            log.warn("ALERT_FIELD_TRUNCATED: field={} patient={}", fieldName, patientId);
            return value.substring(0, 150);
        }
        return value;
    }

    /**
     * Resolves the root anchor date for pathway steps with no prerequisites.
     *
     * <p>D-03: referralReceivedAt is the primary time anchor when set.
     * Falls back to diagnosisDate for patients enrolled before referral tracking
     * or patients created without a referral PDF.
     *
     * @param patient the patient entity
     * @return the anchor date for root steps, never null (diagnosisDate is always set)
     */
    private LocalDate resolveRootAnchor(Patient patient) {
        if (patient.getReferralReceivedAt() != null) {
            return patient.getReferralReceivedAt().toLocalDate();
        }
        return patient.getDiagnosisDate();
    }
}
