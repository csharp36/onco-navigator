package com.onconavigator.service;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.web.dto.PathwayStatusResponse;
import com.onconavigator.web.dto.PathwayStepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Derives per-step pathway status from per-patient relational DAG data.
 *
 * <p>This service is the read-only counterpart to PathwayEvaluationActivityImpl.
 * It does not write alerts — it produces a status view for the dashboard's patient
 * detail pathway timeline, including DAG depth and topological sort order for
 * frontend DAG visualization.
 *
 * <p>PHI safety: Logs only patient UUIDs. Care event and patient PHI fields are
 * not referenced in any log statement.
 */
@Service
public class PathwayStatusService {

    private static final Logger log = LoggerFactory.getLogger(PathwayStatusService.class);

    private final PatientRepository patientRepository;
    private final PatientPathwayRepository pathwayRepository;
    private final PatientPathwayStepRepository stepRepository;
    private final PatientPathwayEdgeRepository edgeRepository;
    private final AlertRepository alertRepository;

    public PathwayStatusService(PatientRepository patientRepository,
                                PatientPathwayRepository pathwayRepository,
                                PatientPathwayStepRepository stepRepository,
                                PatientPathwayEdgeRepository edgeRepository,
                                AlertRepository alertRepository) {
        this.patientRepository = patientRepository;
        this.pathwayRepository = pathwayRepository;
        this.stepRepository = stepRepository;
        this.edgeRepository = edgeRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * Returns the per-step pathway status for a patient.
     *
     * <p>Queries per-patient steps and edges from relational tables, computes topological
     * sort order with depth tracking via Kahn's BFS algorithm, and returns a DAG-aware
     * step list for the frontend pathway visualization.
     *
     * @param patientId the patient UUID
     * @return full pathway status response with one entry per step, in topological order
     * @throws ResponseStatusException 404 if patient not found
     */
    @Transactional(readOnly = true)
    public PathwayStatusResponse getPathwayStatus(UUID patientId) {
        // 1. Load patient or 404
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        PatientPathway pathway = pathwayRepository.findByPatient_Id(patientId).orElse(null);
        if (pathway == null) {
            log.info("No pathway found for patient {}, returning empty status", patientId);
            return new PathwayStatusResponse(patientId, List.of());
        }

        // Get ALL steps (not just ACTIVE -- frontend shows COMPLETED, PROPOSED, SKIPPED too)
        List<PatientPathwayStep> allSteps = stepRepository.findByPathway_Id(pathway.getId());
        List<PatientPathwayEdge> allEdges = edgeRepository.findByPathway_Id(pathway.getId());

        if (allSteps.isEmpty()) {
            return new PathwayStatusResponse(patientId, List.of());
        }

        // 2. Compute topological order and depth (Kahn's BFS algorithm)
        // Build adjacency list and in-degree map
        Map<UUID, List<UUID>> adjacency = new HashMap<>();
        Map<UUID, Integer> inDegree = new HashMap<>();
        Map<UUID, Set<UUID>> prereqMap = new HashMap<>();

        for (PatientPathwayStep step : allSteps) {
            adjacency.put(step.getId(), new ArrayList<>());
            inDegree.put(step.getId(), 0);
        }
        for (PatientPathwayEdge edge : allEdges) {
            // Only add edge if both endpoints exist in current step set
            if (adjacency.containsKey(edge.getSourceStepId())
                    && adjacency.containsKey(edge.getTargetStepId())) {
                adjacency.get(edge.getSourceStepId()).add(edge.getTargetStepId());
                inDegree.merge(edge.getTargetStepId(), 1, Integer::sum);
                prereqMap.computeIfAbsent(edge.getTargetStepId(), k -> new HashSet<>())
                        .add(edge.getSourceStepId());
            }
        }

        // Kahn's BFS with depth tracking
        Queue<UUID> queue = new ArrayDeque<>();
        Map<UUID, Integer> depthMap = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
                depthMap.put(entry.getKey(), 0);
            }
        }

        List<UUID> sortedIds = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            sortedIds.add(current);
            for (UUID neighbor : adjacency.getOrDefault(current, List.of())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                depthMap.put(neighbor, Math.max(
                        depthMap.getOrDefault(neighbor, 0),
                        depthMap.get(current) + 1));
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // For steps not reached by topological sort (orphaned), assign depth 0
        for (PatientPathwayStep step : allSteps) {
            if (!depthMap.containsKey(step.getId())) {
                sortedIds.add(step.getId());
                depthMap.put(step.getId(), 0);
            }
        }

        // 3. Build step map for lookup
        Map<UUID, PatientPathwayStep> stepMap = allSteps.stream()
                .collect(Collectors.toMap(PatientPathwayStep::getId, s -> s));

        // 4. Get open alerts for the patient
        List<Alert> openAlerts = alertRepository.findByPatientIdAndStatus(patientId, AlertStatus.OPEN);
        Set<String> alertStepNames = openAlerts.stream()
                .map(Alert::getPathwayStepName).collect(Collectors.toSet());

        // 5. Build response with timing info in topological order
        List<PathwayStepStatus> stepStatuses = new ArrayList<>();
        for (int i = 0; i < sortedIds.size(); i++) {
            UUID stepId = sortedIds.get(i);
            PatientPathwayStep step = stepMap.get(stepId);
            if (step == null) continue;

            String timingInfo = computeTimingInfo(step, patient, prereqMap, stepMap);
            List<String> prereqIds = prereqMap.getOrDefault(step.getId(), Set.of())
                    .stream().map(UUID::toString).toList();

            stepStatuses.add(new PathwayStepStatus(
                    step.getId().toString(),
                    step.getName(),
                    step.getStatus().name(),
                    depthMap.getOrDefault(step.getId(), 0),
                    i,  // sortOrder
                    step.getCompletedAt() != null ? step.getCompletedAt().toLocalDate() : null,
                    timingInfo,
                    alertStepNames.contains(step.getName()),
                    step.getSkipReason(),
                    prereqIds
            ));
        }

        log.info("Computed DAG pathway status for patient {} ({} steps)", patientId, stepStatuses.size());

        return new PathwayStatusResponse(patientId, stepStatuses);
    }

    // ---- Private helpers ----

    /**
     * Computes human-readable timing information for a pathway step.
     *
     * <p>For COMPLETED steps: shows the completion date.
     * For SKIPPED steps: shows the skip reason if available.
     * For PROPOSED steps: shows "Pending confirmation".
     * For ACTIVE steps: resolves anchor date (D-11) and computes days remaining or overdue.
     *
     * <p>Anchor date resolution (D-11):
     * Root steps (no prerequisites) anchor to the patient's diagnosis date.
     * Steps with prerequisites anchor to the LATEST prerequisite completion date.
     *
     * @param step      the step to compute timing for
     * @param patient   the patient entity (for diagnosis date)
     * @param prereqMap map of stepId to set of prerequisite step IDs
     * @param stepMap   map of stepId to step entity for prerequisite lookups
     * @return human-readable timing string
     */
    private String computeTimingInfo(PatientPathwayStep step, Patient patient,
            Map<UUID, Set<UUID>> prereqMap, Map<UUID, PatientPathwayStep> stepMap) {
        if (step.getStatus() == com.onconavigator.domain.enums.PathwayStepStatus.COMPLETED) {
            return step.getCompletedAt() != null
                    ? "Completed " + step.getCompletedAt().toLocalDate()
                    : "Completed";
        }
        if (step.getStatus() == com.onconavigator.domain.enums.PathwayStepStatus.SKIPPED) {
            return "Skipped" + (step.getSkipReason() != null ? ": " + step.getSkipReason() : "");
        }
        if (step.getStatus() == com.onconavigator.domain.enums.PathwayStepStatus.PROPOSED) {
            return "Pending confirmation";
        }
        // ACTIVE: compute timing based on anchor date
        if (step.getWindowDays() == null) return "No time window";

        // Resolve anchor date (D-11)
        Set<UUID> prereqs = prereqMap.getOrDefault(step.getId(), Set.of());
        LocalDate anchorDate;
        if (prereqs.isEmpty()) {
            anchorDate = patient.getDiagnosisDate();
        } else {
            anchorDate = prereqs.stream()
                    .map(stepMap::get)
                    .filter(Objects::nonNull)
                    .filter(ps -> ps.getStatus() == com.onconavigator.domain.enums.PathwayStepStatus.COMPLETED
                               && ps.getCompletedAt() != null)
                    .map(ps -> ps.getCompletedAt().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);
        }

        if (anchorDate == null) {
            return "Waiting on prerequisites";
        }

        LocalDate dueDate = anchorDate.plusDays(step.getWindowDays());
        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
        if (daysUntilDue > 0) {
            return "Due in " + daysUntilDue + " day" + (daysUntilDue != 1 ? "s" : "");
        } else if (daysUntilDue == 0) {
            return "Due today";
        } else {
            long overdue = Math.abs(daysUntilDue);
            return overdue + " day" + (overdue != 1 ? "s" : "") + " overdue";
        }
    }
}
