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
 * <p>Deviation detection (Phase 5 DAG-based):
 * <ul>
 *   <li>MISSING_EVENT: Required ACTIVE step, no matching care event, time window exceeded</li>
 *   <li>DELAYED_EVENT: A care event exists for a step but is not COMPLETED and time has elapsed</li>
 *   <li>OUT_OF_ORDER: A care event exists for a step whose prerequisites are not yet completed</li>
 * </ul>
 *
 * <p>Step readiness (D-11): A step is "ready" for evaluation only when ALL prerequisite
 * steps have status COMPLETED or SKIPPED. Root steps (no prerequisites) anchor to
 * the patient's diagnosis date. Steps with prerequisites anchor to the LATEST
 * prerequisite completion date.
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

    public PathwayEvaluationActivityImpl(
            PatientRepository patientRepository,
            CareEventRepository careEventRepository,
            AlertRepository alertRepository,
            PatientPathwayRepository pathwayRepository,
            PatientPathwayStepRepository stepRepository,
            PatientPathwayEdgeRepository edgeRepository,
            ObjectMapper objectMapper,
            AlertGenerationAiService alertGenerationAiService) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.alertRepository = alertRepository;
        this.pathwayRepository = pathwayRepository;
        this.stepRepository = stepRepository;
        this.edgeRepository = edgeRepository;
        this.objectMapper = objectMapper;
        this.alertGenerationAiService = alertGenerationAiService;
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
                anchorDate = patient.getDiagnosisDate();
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

            // MISSING_EVENT: required step, no matching care event, time window exceeded
            if (step.isRequired() && !hasMatch && step.getWindowDays() != null
                    && daysSinceAnchor > step.getWindowDays()) {
                String summary = createAlertIfNotDuplicate(patient, step, AlertType.MISSING_EVENT,
                        "Missing: " + step.getName() + " (expected within " + step.getWindowDays() + " days)");
                if (summary != null) {
                    alertsGenerated.add(summary);
                }
            }

            // DELAYED_EVENT: event exists but not completed, time window exceeded
            List<CareEvent> nonCompletedMatches = careEvents.stream()
                    .filter(e -> e.getEventType() == step.getEventType()
                              && e.getStatus() != CareEventStatus.COMPLETED)
                    .toList();
            if (!nonCompletedMatches.isEmpty() && step.getWindowDays() != null
                    && daysSinceAnchor > step.getWindowDays()) {
                String summary = createAlertIfNotDuplicate(patient, step, AlertType.DELAYED_EVENT,
                        "Delayed: " + step.getName() + " (scheduled but not completed)");
                if (summary != null) {
                    alertsGenerated.add(summary);
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

        // Build alert text: use step's alertText if available, or try AI, or use default
        String description = buildAlertDescription(step, alertType, defaultDescription, patient);

        Alert alert = new Alert();
        alert.setPatientId(patient.getId());
        alert.setAlertType(alertType);
        alert.setPathwayStepName(step.getName());
        alert.setDeviationDescription(description);
        alert.setSuggestedAction(step.getSuggestedAction() != null
                ? step.getSuggestedAction() : "Review patient pathway and take corrective action.");
        alert.setStatus(AlertStatus.OPEN);
        alertRepository.save(alert);

        log.info("ALERT_CREATED: patient={} step={} type={}", alertType, patient.getId(), step.getId());
        return alertType.name() + ": step '" + step.getName() + "' for patient " + patient.getId();
    }

    /**
     * Builds the alert deviation description for a pathway step deviation.
     *
     * <p>AI-01: Template text ({@code step.getAlertText()}) is the primary source for
     * standard deviations where it is non-null and non-blank.
     *
     * <p>AI-02/AI-03: For non-standard deviations, calls {@link AlertGenerationAiService}
     * with zero-PHI parameters to generate a Claude-powered deviation description.
     *
     * <p>AI-04: When Claude is unavailable (circuit breaker open), falls back to the
     * {@code defaultDescription} parameter.
     *
     * <p>ZERO-PHI: Only anonymized clinical context is sent to Claude:
     * cancer type enum, step name, alert type enum, window days, and step names.
     * NO patient identifiers (name, MRN, DOB) are referenced.
     *
     * @param step               the pathway step in deviation
     * @param alertType          the type of deviation
     * @param defaultDescription fallback description
     * @param patient            patient entity (for cancerType enum only — no PHI accessed)
     * @return the alert description string
     */
    private String buildAlertDescription(PatientPathwayStep step, AlertType alertType,
            String defaultDescription, Patient patient) {
        // AI-01: Template text is the primary source for standard deviations
        if (step.getAlertText() != null && !step.getAlertText().isBlank()) {
            return step.getAlertText();
        }

        // AI-02/AI-03: Non-standard deviation — try Claude for generated text
        // ZERO-PHI: Only anonymized clinical context is sent
        AlertText claudeText = alertGenerationAiService.generateAlertDescription(
                patient.getCancerType().name(),           // non-PHI: cancer type enum
                step.getName(),                           // non-PHI: pathway step name
                alertType.name(),                         // non-PHI: deviation type enum
                step.getWindowDays() != null ? String.valueOf(step.getWindowDays()) : "unknown",
                List.of(),                                // no completed step names available here
                List.of()                                 // no missing step names available here
        );

        if (claudeText != null) {
            log.info("ALERT_CLAUDE_GENERATED: patient={} step={}", patient.getId(), step.getId());
            return claudeText.deviationDescription();
        }

        // AI-04: Circuit breaker fallback — use default description
        log.info("ALERT_FALLBACK_TEMPLATE: patient={} step={}", patient.getId(), step.getId());
        return defaultDescription;
    }
}
