package com.onconavigator.service;

import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.web.dto.PathwayEdgeRequest;
import com.onconavigator.web.dto.PathwayEdgeResponse;
import com.onconavigator.web.dto.PathwayStepRequest;
import com.onconavigator.web.dto.PathwayStepResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service providing full CRUD operations for per-patient pathway steps and edges.
 *
 * <p>Every mutating operation signals the Temporal pathway workflow via
 * {@link PathwayService#signalPathwayStepsChanged(UUID)} so the evaluation engine
 * immediately re-evaluates the patient's pathway state (D-01, D-09).
 *
 * <p>Step removal and skipping cascade-resolve any OPEN alerts for the affected step so
 * nurse navigators are not left with actionable items for steps that no longer exist or
 * are intentionally excluded from the pathway.
 *
 * <p>Edge creation runs DFS cycle detection before persisting to prevent invalid circular
 * DAGs (D-09, T-05-06). Every step/edge operation verifies entity ownership through the
 * patient's pathway to prevent BOLA attacks (T-05-07).
 *
 * <p>PHI safety: All log statements use patient UUIDs and step/edge UUIDs only. Step names
 * are not logged (per SEC-06 UUID-only convention).
 */
@Service
public class PatientPathwayService {

    private static final Logger log = LoggerFactory.getLogger(PatientPathwayService.class);

    private final PatientPathwayRepository pathwayRepository;
    private final PatientPathwayStepRepository stepRepository;
    private final PatientPathwayEdgeRepository edgeRepository;
    private final AlertRepository alertRepository;
    private final PathwayService pathwayService;

    public PatientPathwayService(PatientPathwayRepository pathwayRepository,
                                  PatientPathwayStepRepository stepRepository,
                                  PatientPathwayEdgeRepository edgeRepository,
                                  AlertRepository alertRepository,
                                  PathwayService pathwayService) {
        this.pathwayRepository = pathwayRepository;
        this.stepRepository = stepRepository;
        this.edgeRepository = edgeRepository;
        this.alertRepository = alertRepository;
        this.pathwayService = pathwayService;
    }

    // ---- Step queries ----

    /**
     * Returns all steps for a patient's pathway in topological sort order with depth metadata.
     *
     * <p>Runs Kahn's algorithm to compute the topological ordering and depth for each step.
     * Depth 0 is assigned to root nodes (no prerequisites); deeper nodes receive
     * max(predecessor depths) + 1.
     *
     * @param patientId the patient UUID
     * @return steps in topological order with depth and prerequisite IDs
     * @throws ResponseStatusException 404 if no pathway exists for this patient
     */
    @Transactional(readOnly = true)
    public List<PathwayStepResponse> getSteps(UUID patientId) {
        PatientPathway pathway = requirePathway(patientId);
        List<PatientPathwayStep> steps = stepRepository.findByPathway_Id(pathway.getId());
        List<PatientPathwayEdge> edges = edgeRepository.findByPathway_Id(pathway.getId());
        List<StepWithDepth> ordered = computeTopology(steps, edges);
        return ordered.stream()
                .map(swd -> toStepResponse(swd.step(), swd.depth(), swd.sortOrder(), swd.prerequisiteIds()))
                .toList();
    }

    /**
     * Returns all edges for a patient's pathway.
     *
     * @param patientId the patient UUID
     * @return list of edge response DTOs
     * @throws ResponseStatusException 404 if no pathway exists for this patient
     */
    @Transactional(readOnly = true)
    public List<PathwayEdgeResponse> getEdges(UUID patientId) {
        PatientPathway pathway = requirePathway(patientId);
        return edgeRepository.findByPathway_Id(pathway.getId()).stream()
                .map(this::toEdgeResponse)
                .toList();
    }

    // ---- Step mutations ----

    /**
     * Creates a new step in the patient's pathway as a root node (no prerequisites).
     *
     * <p>Per D-12: new steps default to ACTIVE status.
     *
     * @param patientId the patient UUID
     * @param request   step creation request
     * @param actorId   UUID of the authenticated user
     * @return the created step as a response DTO
     */
    @Transactional
    public PathwayStepResponse createStep(UUID patientId, PathwayStepRequest request, UUID actorId) {
        PatientPathway pathway = requirePathway(patientId);

        PatientPathwayStep step = new PatientPathwayStep();
        step.setPathway(pathway);
        step.setName(request.name());
        step.setDescription(request.description());
        step.setEventType(request.eventType());
        step.setWindowDays(request.windowDays());
        step.setRequired(request.required() != null ? request.required() : true);
        step.setAlertText(request.alertText());
        step.setSuggestedAction(request.suggestedAction());
        step.setStatus(PathwayStepStatus.ACTIVE);
        step.setCreatedBy(actorId);
        step = stepRepository.save(step);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Created step {} in pathway for patient {}", step.getId(), patientId);

        // New root step: depth=0, sortOrder=last, no prerequisites
        int sortOrder = stepRepository.findByPathway_Id(pathway.getId()).size() - 1;
        return toStepResponse(step, 0, sortOrder, List.of());
    }

    /**
     * Updates the mutable fields of an existing step.
     *
     * <p>Status transitions (skip/unskip) are performed via dedicated methods.
     * Optimistic locking via {@code @Version} on {@link PatientPathwayStep} prevents
     * lost updates from concurrent edits (T-05-08).
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID
     * @param request   updated step fields
     * @param actorId   UUID of the authenticated user
     * @return the updated step as a response DTO
     * @throws ResponseStatusException 404 if step not found or not owned by this patient
     */
    @Transactional
    public PathwayStepResponse updateStep(UUID patientId, UUID stepId,
                                          PathwayStepRequest request, UUID actorId) {
        PatientPathwayStep step = requireStep(patientId, stepId);

        // Update only mutable fields — status is changed via skipStep/unskipStep
        step.setName(request.name());
        if (request.description() != null) {
            step.setDescription(request.description());
        }
        if (request.eventType() != null) {
            step.setEventType(request.eventType());
        }
        if (request.windowDays() != null) {
            step.setWindowDays(request.windowDays());
        }
        if (request.required() != null) {
            step.setRequired(request.required());
        }
        if (request.alertText() != null) {
            step.setAlertText(request.alertText());
        }
        if (request.suggestedAction() != null) {
            step.setSuggestedAction(request.suggestedAction());
        }
        step = stepRepository.save(step);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Updated step {} for patient {}", stepId, patientId);

        List<UUID> prereqIds = getPrerequisiteIds(step.getPathway().getId(), stepId);
        return toStepResponse(step, 0, 0, prereqIds);
    }

    /**
     * Removes a step from the patient's pathway and resolves any open alerts for it.
     *
     * <p>Cascade-deletes all edges referencing this step (both incoming and outgoing).
     * Resolves any OPEN alerts for this step name so the nurse is not left with
     * unactionable alerts.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to delete
     * @throws ResponseStatusException 404 if step not found or not owned by this patient
     */
    @Transactional
    public void deleteStep(UUID patientId, UUID stepId) {
        PatientPathwayStep step = requireStep(patientId, stepId);

        // Resolve any OPEN alerts for this step
        resolveAlertsForStep(patientId, step.getName());

        // Remove all edges referencing this step (incoming + outgoing)
        edgeRepository.deleteBySourceStepIdOrTargetStepId(stepId, stepId);

        // Delete the step itself
        stepRepository.delete(step);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Deleted step {} from pathway for patient {}", stepId, patientId);
    }

    /**
     * Marks a step as SKIPPED with a reason, resolving any open alerts for it.
     *
     * @param patientId  the patient UUID (ownership verification)
     * @param stepId     the step UUID to skip
     * @param skipReason clinical reason for skipping this step
     * @param actorId    UUID of the authenticated user
     * @return the updated step as a response DTO
     * @throws ResponseStatusException 404 if step not found or not owned by patient
     * @throws ResponseStatusException 409 if step is not in ACTIVE status
     */
    @Transactional
    public PathwayStepResponse skipStep(UUID patientId, UUID stepId, String skipReason, UUID actorId) {
        PatientPathwayStep step = requireStep(patientId, stepId);

        if (step.getStatus() != PathwayStepStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only ACTIVE steps can be skipped; step " + stepId + " is " + step.getStatus());
        }

        step.setStatus(PathwayStepStatus.SKIPPED);
        step.setSkipReason(skipReason);
        step = stepRepository.save(step);

        // Resolve any OPEN alerts for this step
        resolveAlertsForStep(patientId, step.getName());

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Skipped step {} for patient {}", stepId, patientId);

        List<UUID> prereqIds = getPrerequisiteIds(step.getPathway().getId(), stepId);
        return toStepResponse(step, 0, 0, prereqIds);
    }

    /**
     * Restores a SKIPPED step to ACTIVE status, clearing the skip reason.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to restore
     * @param actorId   UUID of the authenticated user
     * @return the updated step as a response DTO
     * @throws ResponseStatusException 404 if step not found or not owned by patient
     * @throws ResponseStatusException 409 if step is not in SKIPPED status
     */
    @Transactional
    public PathwayStepResponse unskipStep(UUID patientId, UUID stepId, UUID actorId) {
        PatientPathwayStep step = requireStep(patientId, stepId);

        if (step.getStatus() != PathwayStepStatus.SKIPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only SKIPPED steps can be unskipped; step " + stepId + " is " + step.getStatus());
        }

        step.setStatus(PathwayStepStatus.ACTIVE);
        step.setSkipReason(null);
        step = stepRepository.save(step);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Unskipped step {} for patient {}", stepId, patientId);

        List<UUID> prereqIds = getPrerequisiteIds(step.getPathway().getId(), stepId);
        return toStepResponse(step, 0, 0, prereqIds);
    }

    // ---- Edge mutations ----

    /**
     * Creates a prerequisite edge between two steps with cycle detection.
     *
     * <p>Validates that both steps belong to this patient's pathway, deduplicates
     * existing edges, and runs DFS cycle detection before persisting. Self-edges are
     * rejected as a belt-and-suspenders guard (the DB CHECK constraint also prevents them).
     *
     * @param patientId the patient UUID (ownership verification)
     * @param request   edge creation request with source and target step IDs
     * @param actorId   UUID of the authenticated user
     * @return the created edge as a response DTO
     * @throws ResponseStatusException 404 if steps not found or not owned by patient
     * @throws ResponseStatusException 400 if source == target (self-edge)
     * @throws ResponseStatusException 409 if edge already exists or would create a cycle
     */
    @Transactional
    public PathwayEdgeResponse createEdge(UUID patientId, PathwayEdgeRequest request, UUID actorId) {
        PatientPathway pathway = requirePathway(patientId);

        // Verify both steps belong to this patient's pathway (T-05-07 ownership)
        PatientPathwayStep sourceStep = requireStep(patientId, request.sourceStepId());
        PatientPathwayStep targetStep = requireStep(patientId, request.targetStepId());

        // Belt-and-suspenders self-edge check (DB constraint also enforces this)
        if (request.sourceStepId().equals(request.targetStepId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A step cannot be its own prerequisite (self-edge not allowed).");
        }

        List<PatientPathwayEdge> existingEdges = edgeRepository.findByPathway_Id(pathway.getId());

        // Deduplicate: check if this exact edge already exists
        boolean alreadyExists = existingEdges.stream()
                .anyMatch(e -> e.getSourceStepId().equals(request.sourceStepId())
                        && e.getTargetStepId().equals(request.targetStepId()));
        if (alreadyExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This prerequisite relationship already exists.");
        }

        // Cycle detection: DFS to check if adding this edge would form a cycle (T-05-06)
        if (wouldCreateCycle(request.sourceStepId(), request.targetStepId(), existingEdges)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot add this dependency — it would create a circular path.");
        }

        PatientPathwayEdge edge = new PatientPathwayEdge();
        edge.setPathway(pathway);
        edge.setSourceStepId(request.sourceStepId());
        edge.setTargetStepId(request.targetStepId());
        edge.setCreatedBy(actorId);
        edge = edgeRepository.save(edge);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Created edge {} (source={} target={}) for patient {}",
                edge.getId(), request.sourceStepId(), request.targetStepId(), patientId);

        return toEdgeResponse(edge);
    }

    /**
     * Removes a prerequisite edge from the patient's pathway.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param edgeId    the edge UUID to delete
     * @throws ResponseStatusException 404 if edge not found or not owned by this patient
     */
    @Transactional
    public void deleteEdge(UUID patientId, UUID edgeId) {
        PatientPathway pathway = requirePathway(patientId);

        PatientPathwayEdge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Edge not found"));

        // Ownership verification: edge must belong to this patient's pathway (T-05-07)
        if (!edge.getPathway().getId().equals(pathway.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge not found");
        }

        edgeRepository.delete(edge);

        pathwayService.signalPathwayStepsChanged(patientId);

        log.info("Deleted edge {} from pathway for patient {}", edgeId, patientId);
    }

    // ---- Private helpers ----

    /**
     * Runs Kahn's algorithm to produce a topological ordering of the pathway DAG with
     * depth metadata for each step.
     *
     * <p>Depth is computed as: root nodes get depth 0; each other node gets
     * {@code max(predecessor depths) + 1}. Steps not reachable in topological order
     * (e.g., isolated disconnected cycles, which cycle detection prevents at write time)
     * are appended at the end with depth {@code maxDepth + 1}.
     *
     * @param steps all steps in the pathway
     * @param edges all edges in the pathway
     * @return list of (step, depth, sortOrder, prerequisiteIds) in topological order
     */
    private List<StepWithDepth> computeTopology(List<PatientPathwayStep> steps,
                                                  List<PatientPathwayEdge> edges) {
        if (steps.isEmpty()) {
            return List.of();
        }

        // Build maps for O(1) lookup
        Map<UUID, PatientPathwayStep> stepById = new HashMap<>();
        for (PatientPathwayStep s : steps) {
            stepById.put(s.getId(), s);
        }

        // in-degree[stepId] = count of incoming edges
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (PatientPathwayStep s : steps) {
            inDegree.put(s.getId(), 0);
        }

        // adjacency[sourceId] -> set of targetIds (outgoing edges)
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        // reverse: for each step, which source steps point to it (predecessors)
        Map<UUID, List<UUID>> predecessors = new HashMap<>();
        for (PatientPathwayStep s : steps) {
            adjacency.put(s.getId(), new HashSet<>());
            predecessors.put(s.getId(), new ArrayList<>());
        }

        for (PatientPathwayEdge e : edges) {
            adjacency.computeIfAbsent(e.getSourceStepId(), k -> new HashSet<>())
                    .add(e.getTargetStepId());
            inDegree.merge(e.getTargetStepId(), 1, Integer::sum);
            predecessors.computeIfAbsent(e.getTargetStepId(), k -> new ArrayList<>())
                    .add(e.getSourceStepId());
        }

        // depth[stepId] = DAG depth
        Map<UUID, Integer> depth = new HashMap<>();
        for (PatientPathwayStep s : steps) {
            depth.put(s.getId(), 0);
        }

        // Kahn's BFS: start from zero-in-degree nodes
        Queue<UUID> queue = new LinkedList<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
                depth.put(entry.getKey(), 0);
            }
        }

        List<StepWithDepth> result = new ArrayList<>();
        int sortOrder = 0;

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            PatientPathwayStep currentStep = stepById.get(current);
            if (currentStep == null) continue;

            // Compute prerequisite IDs for this step
            List<UUID> prereqIds = predecessors.getOrDefault(current, List.of());

            result.add(new StepWithDepth(currentStep, depth.get(current), sortOrder++, prereqIds));

            for (UUID neighbor : adjacency.getOrDefault(current, Set.of())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                // Depth of neighbor = max(predecessor depths) + 1
                int newDepth = depth.get(current) + 1;
                depth.merge(neighbor, newDepth, Math::max);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Append any steps not processed (isolated nodes not in any edge)
        // This handles steps with no edges at all (both in-degree and out-degree are 0)
        // that were not added to the initial queue — shouldn't happen but defensive
        if (result.size() < steps.size()) {
            int maxDepth = result.stream().mapToInt(StepWithDepth::depth).max().orElse(0);
            for (PatientPathwayStep s : steps) {
                boolean already = result.stream().anyMatch(swd -> swd.step().getId().equals(s.getId()));
                if (!already) {
                    result.add(new StepWithDepth(s, maxDepth + 1, sortOrder++, List.of()));
                }
            }
        }

        return result;
    }

    /**
     * DFS-based cycle detection: checks whether adding the proposed edge
     * {@code sourceStepId -> targetStepId} to the existing edge set would create a cycle.
     *
     * <p>Builds the adjacency map including the proposed edge, then runs DFS from
     * {@code targetStepId} to see if it can reach {@code sourceStepId}. If yes,
     * the proposed edge would close a cycle.
     *
     * @param sourceStepId UUID of the proposed edge source
     * @param targetStepId UUID of the proposed edge target
     * @param existingEdges current edges in the pathway
     * @return {@code true} if adding this edge would create a cycle
     */
    private boolean wouldCreateCycle(UUID sourceStepId, UUID targetStepId,
                                     List<PatientPathwayEdge> existingEdges) {
        // Build adjacency list with the proposed edge included
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (PatientPathwayEdge edge : existingEdges) {
            adjacency.computeIfAbsent(edge.getSourceStepId(), k -> new HashSet<>())
                    .add(edge.getTargetStepId());
        }
        adjacency.computeIfAbsent(sourceStepId, k -> new HashSet<>())
                .add(targetStepId);

        // DFS from targetStepId: if we can reach sourceStepId, a cycle exists
        Set<UUID> visited = new HashSet<>();
        return dfsReaches(targetStepId, sourceStepId, adjacency, visited);
    }

    private boolean dfsReaches(UUID current, UUID target,
                                Map<UUID, Set<UUID>> adjacency, Set<UUID> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;
        for (UUID neighbor : adjacency.getOrDefault(current, Set.of())) {
            if (dfsReaches(neighbor, target, adjacency, visited)) return true;
        }
        return false;
    }

    /**
     * Resolves all OPEN alerts for the given patient and step name.
     *
     * <p>Sets status = RESOLVED with a system note. Used when a step is deleted or skipped
     * so nurses are not left with unactionable alerts.
     */
    private void resolveAlertsForStep(UUID patientId, String stepName) {
        List<com.onconavigator.domain.Alert> openAlerts =
                alertRepository.findByPatientIdAndPathwayStepNameAndStatus(
                        patientId, stepName, AlertStatus.OPEN);
        for (com.onconavigator.domain.Alert alert : openAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(OffsetDateTime.now());
            alert.setResolutionNotes("Step removed from pathway");
            alertRepository.save(alert);
        }
        if (!openAlerts.isEmpty()) {
            log.info("Resolved {} open alerts for step removal, patient {}", openAlerts.size(), patientId);
        }
    }

    /**
     * Retrieves prerequisite (incoming) step IDs for the given step.
     *
     * <p>Used by updateStep, skipStep, unskipStep where we need accurate prerequisite lists
     * but don't run a full Kahn's traversal.
     */
    private List<UUID> getPrerequisiteIds(UUID pathwayId, UUID stepId) {
        return edgeRepository.findByPathway_Id(pathwayId).stream()
                .filter(e -> e.getTargetStepId().equals(stepId))
                .map(PatientPathwayEdge::getSourceStepId)
                .collect(Collectors.toList());
    }

    /**
     * Looks up the pathway for a patient, throwing 404 if not found.
     */
    private PatientPathway requirePathway(UUID patientId) {
        return pathwayRepository.findByPatient_Id(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No pathway found for patient"));
    }

    /**
     * Looks up a step by stepId and verifies it belongs to the patient's pathway (T-05-07).
     *
     * @throws ResponseStatusException 404 if step not found or belongs to a different patient
     */
    private PatientPathwayStep requireStep(UUID patientId, UUID stepId) {
        PatientPathway pathway = requirePathway(patientId);
        PatientPathwayStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Step not found"));
        // Ownership verification: step must belong to this patient's pathway
        if (!step.getPathway().getId().equals(pathway.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found");
        }
        return step;
    }

    /**
     * Maps a {@link PatientPathwayStep} with topology metadata to a {@link PathwayStepResponse}.
     */
    private PathwayStepResponse toStepResponse(PatientPathwayStep step, int depth, int sortOrder,
                                                List<UUID> prerequisiteIds) {
        return new PathwayStepResponse(
                step.getId(),
                step.getPathway().getId(),
                step.getName(),
                step.getDescription(),
                step.getEventType() != null ? step.getEventType().name() : null,
                step.getWindowDays(),
                step.isRequired(),
                step.getStatus().name(),
                step.getSkipReason(),
                step.getAlertText(),
                step.getSuggestedAction(),
                step.getCompletedAt(),
                step.getCompletedCareEventId(),
                depth,
                sortOrder,
                prerequisiteIds,
                step.getCreatedAt()
        );
    }

    /**
     * Maps a {@link PatientPathwayEdge} to a {@link PathwayEdgeResponse}.
     */
    private PathwayEdgeResponse toEdgeResponse(PatientPathwayEdge edge) {
        return new PathwayEdgeResponse(
                edge.getId(),
                edge.getPathway().getId(),
                edge.getSourceStepId(),
                edge.getTargetStepId(),
                edge.getCreatedAt()
        );
    }

    // ---- Private record ----

    /**
     * Internal container for Kahn's algorithm output: a step with its computed DAG depth,
     * topological sort order, and list of direct prerequisite step IDs.
     */
    private record StepWithDepth(
            PatientPathwayStep step,
            int depth,
            int sortOrder,
            List<UUID> prerequisiteIds
    ) {}
}
