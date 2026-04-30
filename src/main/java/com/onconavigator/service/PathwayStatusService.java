package com.onconavigator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.Alert;
import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.dto.AnchorType;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.PathwayTemplateRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.web.dto.PathwayStatusResponse;
import com.onconavigator.web.dto.PathwayStepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Derives per-step pathway status by cross-referencing the pathway template JSONB
 * with a patient's recorded care events and open alerts.
 *
 * <p>This service is the read-only counterpart to PathwayEvaluationActivityImpl.
 * It does not write alerts — it produces a status view for the dashboard's patient
 * detail pathway timeline.
 *
 * <p>PHI safety: Logs only patient UUIDs. Care event and patient PHI fields are
 * not referenced in any log statement.
 */
@Service
public class PathwayStatusService {

    private static final Logger log = LoggerFactory.getLogger(PathwayStatusService.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final PathwayTemplateRepository templateRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public PathwayStatusService(PatientRepository patientRepository,
                                CareEventRepository careEventRepository,
                                PathwayTemplateRepository templateRepository,
                                AlertRepository alertRepository,
                                ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.templateRepository = templateRepository;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the per-step pathway status for a patient.
     *
     * <p>Steps are derived by cross-referencing the template JSONB with the patient's
     * recorded care events. Each step is evaluated for completion, timing, and active alerts.
     *
     * @param patientId the patient UUID
     * @return full pathway status response with one entry per template step
     * @throws ResponseStatusException 404 if patient or pathway template not found
     */
    public PathwayStatusResponse getPathwayStatus(UUID patientId) {
        // 1. Load patient or 404
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 2. Load pathway template for this cancer type or 404
        PathwayTemplate template = templateRepository.findByCancerType(patient.getCancerType())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pathway template not found for cancer type"));

        // 3. Deserialize JSONB template data into PathwayStep list
        List<PathwayStep> steps;
        try {
            steps = objectMapper.readValue(template.getTemplateData(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize pathway template for patient {}: {}",
                    patientId, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load pathway template");
        }

        // 4. Load care events (ordered most-recent first)
        List<CareEvent> careEvents = careEventRepository.findByPatient_IdOrderByEventDateDesc(patientId);

        // 5. Load open alerts for this patient
        List<Alert> openAlerts = alertRepository.findByPatientIdAndStatus(patientId, AlertStatus.OPEN);

        // 6. Build a map of stepId -> completed care event for quick lookup
        Map<String, CareEvent> completedByStepId = buildCompletedStepMap(steps, careEvents);

        // 7. Derive status for each step
        List<PathwayStepStatus> stepStatuses = steps.stream()
                .map(step -> deriveStepStatus(step, careEvents, openAlerts,
                        patient.getDiagnosisDate(), completedByStepId, steps))
                .toList();

        log.info("Computed pathway status for patient {} ({} steps)", patientId, stepStatuses.size());

        return new PathwayStatusResponse(patientId, stepStatuses);
    }

    // ---- Private helpers ----

    /**
     * Builds a map from stepId to the COMPLETED CareEvent for each step.
     * Used for anchor date resolution and prerequisite checks.
     */
    private Map<String, CareEvent> buildCompletedStepMap(List<PathwayStep> steps,
                                                          List<CareEvent> careEvents) {
        Map<String, CareEvent> completedByStepId = new HashMap<>();
        for (PathwayStep step : steps) {
            for (CareEvent event : careEvents) {
                if (event.getEventType() == step.eventType()
                        && event.getStatus() == CareEventStatus.COMPLETED) {
                    completedByStepId.put(step.stepId(), event);
                    break; // First completed event for this type is sufficient
                }
            }
        }
        return completedByStepId;
    }

    /**
     * Derives the status of a single pathway step.
     *
     * <p>Status determination:
     * <ul>
     *   <li>COMPLETED — a COMPLETED care event exists for the step's event type</li>
     *   <li>OVERDUE — required step, no completed event, and timing window has expired</li>
     *   <li>MISSING — same as OVERDUE but used for non-required steps (informational)</li>
     *   <li>UPCOMING — no completed event, timing window not yet expired</li>
     * </ul>
     *
     * @param step              the pathway step to evaluate
     * @param careEvents        all care events for the patient
     * @param openAlerts        all open alerts for the patient
     * @param diagnosisDate     patient's diagnosis date (used for DIAGNOSIS_DATE anchor)
     * @param completedByStepId map of stepId to completed care event
     * @param allSteps          all steps (for PREVIOUS_STEP anchor resolution)
     * @return PathwayStepStatus with derived status, timing info, and alert flag
     */
    private PathwayStepStatus deriveStepStatus(PathwayStep step,
                                               List<CareEvent> careEvents,
                                               List<Alert> openAlerts,
                                               LocalDate diagnosisDate,
                                               Map<String, CareEvent> completedByStepId,
                                               List<PathwayStep> allSteps) {
        // Check if completed
        CareEvent completedEvent = completedByStepId.get(step.stepId());
        if (completedEvent != null) {
            LocalDate completionDate = completedEvent.getEventDate();
            LocalDate anchorDate = resolveAnchorDate(step, diagnosisDate, completedByStepId, allSteps);
            String timingInfo = buildCompletedTimingInfo(completionDate, anchorDate, step.windowDays());
            boolean hasActiveAlert = hasActiveAlertForStep(openAlerts, step.name());
            return new PathwayStepStatus(
                    step.stepId(),
                    step.stepNumber(),
                    step.name(),
                    "COMPLETED",
                    completionDate,
                    timingInfo,
                    hasActiveAlert
            );
        }

        // Step not completed — determine OVERDUE, MISSING, or UPCOMING
        LocalDate anchorDate = resolveAnchorDate(step, diagnosisDate, completedByStepId, allSteps);

        if (anchorDate == null) {
            // Cannot compute anchor — prerequisite step not yet completed; step is upcoming
            boolean hasActiveAlert = hasActiveAlertForStep(openAlerts, step.name());
            return new PathwayStepStatus(
                    step.stepId(),
                    step.stepNumber(),
                    step.name(),
                    "UPCOMING",
                    null,
                    "Waiting for prerequisite step",
                    hasActiveAlert
            );
        }

        long elapsedDays = ChronoUnit.DAYS.between(anchorDate, LocalDate.now());
        boolean windowExpired = elapsedDays > step.windowDays();
        boolean hasActiveAlert = hasActiveAlertForStep(openAlerts, step.name());

        if (windowExpired) {
            long daysOverdue = elapsedDays - step.windowDays();
            String timingInfo = daysOverdue + (daysOverdue == 1 ? " day overdue" : " days overdue");
            String status = step.required() ? "OVERDUE" : "MISSING";
            return new PathwayStepStatus(
                    step.stepId(),
                    step.stepNumber(),
                    step.name(),
                    status,
                    null,
                    timingInfo,
                    hasActiveAlert
            );
        } else {
            long daysRemaining = step.windowDays() - elapsedDays;
            String timingInfo = "Due in " + daysRemaining + (daysRemaining == 1 ? " day" : " days");
            return new PathwayStepStatus(
                    step.stepId(),
                    step.stepNumber(),
                    step.name(),
                    "UPCOMING",
                    null,
                    timingInfo,
                    hasActiveAlert
            );
        }
    }

    /**
     * Resolves the anchor date for a pathway step's time window calculation.
     *
     * @param step              the step whose anchor date is needed
     * @param diagnosisDate     patient's diagnosis date
     * @param completedByStepId map of stepId to completed care event
     * @param allSteps          all steps in the pathway
     * @return the anchor date, or null if the anchor step is not yet completed
     */
    private LocalDate resolveAnchorDate(PathwayStep step,
                                        LocalDate diagnosisDate,
                                        Map<String, CareEvent> completedByStepId,
                                        List<PathwayStep> allSteps) {
        AnchorType anchorType = step.anchorType();
        if (anchorType == null) {
            return diagnosisDate;
        }
        return switch (anchorType) {
            case DIAGNOSIS_DATE -> diagnosisDate;
            case PREVIOUS_STEP -> {
                int previousStepNumber = step.stepNumber() - 1;
                String previousStepId = allSteps.stream()
                        .filter(s -> s.stepNumber() == previousStepNumber)
                        .map(PathwayStep::stepId)
                        .findFirst()
                        .orElse(null);
                if (previousStepId == null) {
                    yield diagnosisDate; // First step — fall back to diagnosis date
                }
                CareEvent previousCompleted = completedByStepId.get(previousStepId);
                yield previousCompleted != null ? previousCompleted.getEventDate() : null;
            }
            case SPECIFIC_STEP -> {
                String anchorStepId = step.anchorStepId();
                if (anchorStepId == null) {
                    yield diagnosisDate;
                }
                CareEvent anchorCompleted = completedByStepId.get(anchorStepId);
                yield anchorCompleted != null ? anchorCompleted.getEventDate() : null;
            }
        };
    }

    /**
     * Builds a timing info string for a completed step relative to its expected window.
     *
     * <p>Examples: "Completed Day 10 of 14-day window", "Completed 3 days late"
     */
    private String buildCompletedTimingInfo(LocalDate completionDate, LocalDate anchorDate,
                                             int windowDays) {
        if (anchorDate == null || completionDate == null) {
            return "Completed";
        }
        long elapsedDays = ChronoUnit.DAYS.between(anchorDate, completionDate);
        if (elapsedDays <= windowDays) {
            return "Completed Day " + elapsedDays + " of " + windowDays + "-day window";
        } else {
            long daysLate = elapsedDays - windowDays;
            return "Completed " + daysLate + (daysLate == 1 ? " day late" : " days late");
        }
    }

    /**
     * Checks whether any open alert matches the given step name.
     */
    private boolean hasActiveAlertForStep(List<Alert> openAlerts, String stepName) {
        return openAlerts.stream()
                .anyMatch(a -> stepName.equals(a.getPathwayStepName()));
    }
}
