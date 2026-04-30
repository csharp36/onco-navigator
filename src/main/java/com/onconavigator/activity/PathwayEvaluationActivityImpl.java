package com.onconavigator.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.dto.AnchorType;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.PathwayTemplateRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.repository.PhysicianOverrideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates a patient's care pathway against the template, detecting missing, delayed, and
 * out-of-order events. All database access happens here — the workflow passes only the patient UUID.
 *
 * <p>PHI note: This activity logs only patient UUIDs and step names. Never log patient names,
 * DOBs, or MRNs. PHI fields on {@link Patient} are not referenced in any log statement.
 *
 * <p>Deviation detection:
 * <ul>
 *   <li>MISSING_EVENT (PATH-03): Required step has no care event and elapsed time exceeds windowDays</li>
 *   <li>DELAYED_EVENT (PATH-04): A care event exists for a step but is not COMPLETED and time has elapsed</li>
 *   <li>OUT_OF_ORDER (PATH-05): A care event exists for a step whose prerequisites are not yet completed</li>
 * </ul>
 *
 * <p>Physician overrides (PATH-08): A step with an override record in physician_overrides is skipped
 * entirely — no alert is generated regardless of deviation type.
 *
 * <p>Deduplication (PATH-06): Before creating any alert, an existence check confirms no OPEN alert
 * already exists for (patient, step name). Idempotent — safe for Temporal retries.
 */
@Component
public class PathwayEvaluationActivityImpl implements PathwayEvaluationActivity {

    private static final Logger log = LoggerFactory.getLogger(PathwayEvaluationActivityImpl.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final AlertRepository alertRepository;
    private final PathwayTemplateRepository templateRepository;
    private final PhysicianOverrideRepository overrideRepository;
    private final ObjectMapper objectMapper;

    public PathwayEvaluationActivityImpl(
            PatientRepository patientRepository,
            CareEventRepository careEventRepository,
            AlertRepository alertRepository,
            PathwayTemplateRepository templateRepository,
            PhysicianOverrideRepository overrideRepository,
            ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.alertRepository = alertRepository;
        this.templateRepository = templateRepository;
        this.overrideRepository = overrideRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluation order per step:
     * <ol>
     *   <li>Check physician override — skip step if found (PATH-08)</li>
     *   <li>Check if step is COMPLETED — mark and move on</li>
     *   <li>Detect OUT_OF_ORDER — event exists but prerequisites are incomplete (PATH-05)</li>
     *   <li>Detect MISSING_EVENT / DELAYED_EVENT based on anchor date and windowDays (PATH-03, PATH-04)</li>
     * </ol>
     */
    @Override
    @Transactional
    public PathwayEvaluationResult evaluate(UUID patientId) {
        // 1. Fetch patient
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        // 2. Fetch care events (ordered most-recent first by repository contract)
        List<CareEvent> careEvents = careEventRepository.findByPatient_IdOrderByEventDateDesc(patientId);

        // 3. Fetch pathway template for this cancer type
        PathwayTemplate template = templateRepository.findByCancerType(patient.getCancerType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pathway template found for patient (templateId lookup failed)"));

        // 4. Deserialize JSONB template data into typed step list
        List<PathwayStep> steps;
        try {
            steps = objectMapper.readValue(template.getTemplateData(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to deserialize pathway template (templateId=" + template.getId() + ")", e);
        }

        // 5. Build helper structures
        //    - eventsByType: all care events grouped by care event type
        //    - completedByStepId: the COMPLETED care event for each step (matched by eventType)
        //    - completedStepIds: set of step IDs that have a COMPLETED care event
        Map<CareEventType, List<CareEvent>> eventsByType = new EnumMap<>(CareEventType.class);
        for (CareEvent event : careEvents) {
            eventsByType.computeIfAbsent(event.getEventType(), k -> new ArrayList<>()).add(event);
        }

        Map<String, CareEvent> completedByStepId = new java.util.HashMap<>();
        Set<String> completedStepIds = new HashSet<>();

        // Pre-scan to identify completed steps (needed for prerequisite and anchor lookups)
        for (PathwayStep step : steps) {
            List<CareEvent> eventsForType = eventsByType.getOrDefault(step.eventType(), List.of());
            for (CareEvent event : eventsForType) {
                if (event.getStatus() == CareEventStatus.COMPLETED) {
                    completedByStepId.put(step.stepId(), event);
                    completedStepIds.add(step.stepId());
                    break; // First COMPLETED event for this type is sufficient
                }
            }
        }

        // 6. Evaluate each step in order
        List<String> alertsGenerated = new ArrayList<>();

        for (PathwayStep step : steps) {

            // a. Check physician override (PATH-08)
            if (overrideRepository.existsByPatientIdAndPathwayStepId(patientId, step.stepId())) {
                log.debug("Override exists for patient {} step {}, skipping evaluation",
                        patientId, step.stepId());
                continue;
            }

            // b. Skip if this step is already COMPLETED
            if (completedStepIds.contains(step.stepId())) {
                continue;
            }

            // c. Detect OUT_OF_ORDER (PATH-05):
            //    An event exists for this step's type (in any non-CANCELLED status),
            //    but one or more prerequisites are not yet in completedStepIds.
            List<CareEvent> eventsForType = eventsByType.getOrDefault(step.eventType(), List.of());
            boolean eventExists = eventsForType.stream()
                    .anyMatch(e -> e.getStatus() != CareEventStatus.CANCELLED);

            if (eventExists && !step.prerequisites().isEmpty()) {
                boolean prerequisitesMissing = step.prerequisites().stream()
                        .anyMatch(prereq -> !completedStepIds.contains(prereq));
                if (prerequisitesMissing) {
                    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                            patientId, step.name(), AlertStatus.OPEN);
                    if (!isDuplicate) {
                        Alert alert = buildAlert(patientId, step, AlertType.OUT_OF_ORDER);
                        alertRepository.save(alert);
                        String summary = "OUT_OF_ORDER: step '" + step.name() + "' for patient " + patientId;
                        alertsGenerated.add(summary);
                        log.info("ALERT_CREATED: patient={} step={} type=OUT_OF_ORDER",
                                patientId, step.stepId());
                    }
                    // Do not double-alert with MISSING/DELAYED for the same step in the same
                    // evaluation run — one alert type per step per cycle prevents nurse navigator
                    // confusion and avoids conflicting guidance on the same deviation.
                    continue;
                }
            }

            // d. Detect MISSING_EVENT / DELAYED_EVENT (PATH-03, PATH-04)
            //    Only for required steps — optional steps do not generate timing alerts.
            if (!step.required()) {
                continue;
            }

            LocalDate anchorDate = resolveAnchorDate(step, patient, completedByStepId, steps);
            if (anchorDate == null) {
                // Cannot compute anchor — prerequisite step not yet completed; skip timing check
                continue;
            }

            long elapsedDays = ChronoUnit.DAYS.between(anchorDate, LocalDate.now());

            if (!eventExists) {
                // No care event at all for this step
                if (elapsedDays > step.windowDays()) {
                    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                            patientId, step.name(), AlertStatus.OPEN);
                    if (!isDuplicate) {
                        Alert alert = buildAlert(patientId, step, AlertType.MISSING_EVENT);
                        alertRepository.save(alert);
                        String summary = "MISSING_EVENT: step '" + step.name() + "' for patient " + patientId;
                        alertsGenerated.add(summary);
                        log.info("ALERT_CREATED: patient={} step={} type=MISSING_EVENT elapsedDays={}",
                                patientId, step.stepId(), elapsedDays);
                    }
                }
            } else {
                // Event exists but step is not COMPLETED — check for delayed event
                boolean hasScheduledOrPending = eventsForType.stream()
                        .anyMatch(e -> e.getStatus() == CareEventStatus.SCHEDULED
                                || e.getStatus() == CareEventStatus.PENDING);
                if (hasScheduledOrPending && elapsedDays > step.windowDays()) {
                    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                            patientId, step.name(), AlertStatus.OPEN);
                    if (!isDuplicate) {
                        Alert alert = buildAlert(patientId, step, AlertType.DELAYED_EVENT);
                        alertRepository.save(alert);
                        String summary = "DELAYED_EVENT: step '" + step.name() + "' for patient " + patientId;
                        alertsGenerated.add(summary);
                        log.info("ALERT_CREATED: patient={} step={} type=DELAYED_EVENT elapsedDays={}",
                                patientId, step.stepId(), elapsedDays);
                    }
                }
            }
        }

        // 7. allStepsComplete: every step (required or optional) has a COMPLETED care event
        boolean allStepsComplete = steps.stream()
                .allMatch(step -> completedStepIds.contains(step.stepId()));

        // 8. Log evaluation result (PATH-07)
        log.info("PATHWAY_EVALUATION: patient={} stepsEvaluated={} alertsGenerated={} allComplete={}",
                patientId, steps.size(), alertsGenerated.size(), allStepsComplete);

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
     * Resolves the anchor date for a pathway step based on its {@link AnchorType}.
     *
     * @param step              the step requiring an anchor date
     * @param patient           the patient (used for DIAGNOSIS_DATE anchor)
     * @param completedByStepId map of stepId to its completed care event
     * @param steps             all steps in the pathway (for PREVIOUS_STEP lookup)
     * @return the anchor date, or {@code null} if the anchor step is not yet completed
     */
    private LocalDate resolveAnchorDate(
            PathwayStep step,
            Patient patient,
            Map<String, CareEvent> completedByStepId,
            List<PathwayStep> steps) {

        return switch (step.anchorType()) {
            case DIAGNOSIS_DATE -> patient.getDiagnosisDate();
            case PREVIOUS_STEP -> {
                // Find the step with stepNumber == this step's stepNumber - 1
                int previousStepNumber = step.stepNumber() - 1;
                String previousStepId = steps.stream()
                        .filter(s -> s.stepNumber() == previousStepNumber)
                        .map(PathwayStep::stepId)
                        .findFirst()
                        .orElse(null);
                if (previousStepId == null) {
                    yield null; // No previous step found — first step in pathway
                }
                CareEvent previousCompleted = completedByStepId.get(previousStepId);
                yield previousCompleted != null ? previousCompleted.getEventDate() : null;
            }
            case SPECIFIC_STEP -> {
                String anchorStepId = step.anchorStepId();
                if (anchorStepId == null) {
                    yield null;
                }
                CareEvent anchorCompleted = completedByStepId.get(anchorStepId);
                yield anchorCompleted != null ? anchorCompleted.getEventDate() : null;
            }
        };
    }

    /**
     * Constructs an Alert entity from a pathway step and alert type.
     * Uses pathway template text for descriptions — no PHI is included.
     *
     * @param patientId the patient UUID
     * @param step      the pathway step in deviation
     * @param alertType the type of deviation detected
     * @return an unsaved Alert entity ready for persistence
     */
    private Alert buildAlert(UUID patientId, PathwayStep step, AlertType alertType) {
        Alert alert = new Alert();
        alert.setPatientId(patientId);
        alert.setAlertType(alertType);
        alert.setPathwayStepName(step.name());
        alert.setDeviationDescription(step.alertText());
        alert.setSuggestedAction(step.suggestedAction());
        return alert;
    }
}
