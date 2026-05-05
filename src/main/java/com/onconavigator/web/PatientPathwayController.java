package com.onconavigator.web;

import com.onconavigator.service.PatientPathwayService;
import com.onconavigator.web.dto.PathwayEdgeRequest;
import com.onconavigator.web.dto.PathwayEdgeResponse;
import com.onconavigator.web.dto.PathwayStepRequest;
import com.onconavigator.web.dto.PathwayStepResponse;
import com.onconavigator.web.dto.SkipStepRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for per-patient pathway step and edge CRUD operations.
 *
 * <p>Exposes the full step/edge graph API under {@code /api/patients/{patientId}/pathway}.
 * All endpoints require an authenticated clinical role (D-03). Write operations signal the
 * Temporal workflow via the service layer to immediately re-evaluate the patient's pathway
 * state (D-01, D-09).
 *
 * <p>Security: All endpoints enforce role-based access for the three clinical roles
 * (CARE_COORDINATOR, NURSE_NAVIGATOR, ADMIN) per the authorization model. BOLA protection
 * is enforced in the service layer — patientId is verified against step/edge ownership
 * before any modification (T-05-15).
 *
 * <p>PHI note: Step names and clinical process data are not PHI. Patient identification
 * in log statements uses UUID only (SEC-06).
 */
@RestController
@RequestMapping("/api/patients/{patientId}/pathway")
public class PatientPathwayController {

    private final PatientPathwayService patientPathwayService;

    public PatientPathwayController(PatientPathwayService patientPathwayService) {
        this.patientPathwayService = patientPathwayService;
    }

    // ── Step endpoints ──────────────────────────────────────────────────────────

    /**
     * Returns all steps for a patient's pathway in topological sort order with DAG depth metadata.
     *
     * @param patientId the patient UUID from the URL path
     * @return list of steps with depth, sortOrder, and prerequisiteStepIds fields
     */
    @GetMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<PathwayStepResponse> getSteps(@PathVariable UUID patientId) {
        return patientPathwayService.getSteps(patientId);
    }

    /**
     * Creates a new step in the patient's pathway as a root node (no prerequisites).
     *
     * <p>Per D-12: new steps default to ACTIVE status. Prerequisites are added via the
     * edge endpoints after the step is created.
     *
     * @param patientId the patient UUID from the URL path
     * @param request   step creation request with name and optional configuration fields
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the created step response with assigned UUID
     */
    @PostMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PathwayStepResponse createStep(
            @PathVariable UUID patientId,
            @Valid @RequestBody PathwayStepRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.createStep(patientId, request, actorId);
    }

    /**
     * Updates the mutable fields of an existing step.
     *
     * <p>Status transitions (skip/unskip) use their dedicated endpoints.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID
     * @param request   updated step fields
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the updated step response
     */
    @PutMapping("/steps/{stepId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse updateStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @Valid @RequestBody PathwayStepRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.updateStep(patientId, stepId, request, actorId);
    }

    /**
     * Removes a step from the patient's pathway.
     *
     * <p>Cascade-removes all edges referencing the step and resolves any OPEN alerts
     * for it. Signals the Temporal workflow to re-evaluate immediately.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to delete
     */
    @DeleteMapping("/steps/{stepId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId) {
        patientPathwayService.deleteStep(patientId, stepId);
    }

    /**
     * Marks a step as SKIPPED with a required clinical reason.
     *
     * <p>Resolves any OPEN alerts for the step. Only ACTIVE steps can be skipped.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to skip
     * @param request   skip reason (required)
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the updated step response with SKIPPED status and skipReason
     */
    @PatchMapping("/steps/{stepId}/skip")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse skipStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @Valid @RequestBody SkipStepRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.skipStep(patientId, stepId, request.reason(), actorId);
    }

    /**
     * Restores a SKIPPED step to ACTIVE status, clearing the skip reason.
     *
     * <p>Only SKIPPED steps can be unskipped. Signals the Temporal workflow to re-evaluate.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to restore
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the updated step response with ACTIVE status
     */
    @PatchMapping("/steps/{stepId}/unskip")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse unskipStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.unskipStep(patientId, stepId, actorId);
    }

    /**
     * Confirms a PROPOSED step, transitioning it to ACTIVE status.
     *
     * <p>Proposed edges are activated automatically on confirmation (D-12).
     * Only NURSE_NAVIGATOR and ADMIN roles can confirm steps -- clinical step
     * activation is a nurse decision, not a data entry function.
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to confirm
     * @param jwt       the authenticated user's JWT
     * @return the updated step response with ACTIVE status
     */
    @PostMapping("/steps/{stepId}/confirm")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse confirmStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.confirmProposedStep(patientId, stepId, actorId);
    }

    /**
     * Rejects a PROPOSED step, transitioning it to REJECTED status (soft delete).
     *
     * <p>REJECTED steps remain in the database for audit trail and dedup but are
     * hidden from the pathway view by default (D-07). The REJECTED status prevents
     * re-proposal from future document uploads (D-09).
     *
     * @param patientId the patient UUID (ownership verification)
     * @param stepId    the step UUID to reject
     * @param jwt       the authenticated user's JWT
     * @return the updated step response with REJECTED status
     */
    @PatchMapping("/steps/{stepId}/reject")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse rejectStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.rejectProposedStep(patientId, stepId, actorId);
    }

    // ── Edge endpoints ──────────────────────────────────────────────────────────

    /**
     * Returns all prerequisite edges for a patient's pathway.
     *
     * @param patientId the patient UUID from the URL path
     * @return list of edge response DTOs
     */
    @GetMapping("/edges")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<PathwayEdgeResponse> getEdges(@PathVariable UUID patientId) {
        return patientPathwayService.getEdges(patientId);
    }

    /**
     * Creates a prerequisite edge between two steps with cycle detection.
     *
     * <p>Both steps must belong to the patient's pathway. The service validates
     * that adding this edge would not create a cycle in the DAG (D-09, T-05-06).
     *
     * @param patientId the patient UUID (ownership verification)
     * @param request   edge creation request with source and target step IDs
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the created edge response with assigned UUID
     */
    @PostMapping("/edges")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PathwayEdgeResponse createEdge(
            @PathVariable UUID patientId,
            @Valid @RequestBody PathwayEdgeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.createEdge(patientId, request, actorId);
    }

    /**
     * Removes a prerequisite edge from the patient's pathway.
     *
     * <p>The service verifies edge ownership against the patientId before deletion
     * to prevent cross-patient edge removal (T-05-15 BOLA mitigation).
     *
     * @param patientId the patient UUID (ownership verification)
     * @param edgeId    the edge UUID to delete
     */
    @DeleteMapping("/edges/{edgeId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEdge(
            @PathVariable UUID patientId,
            @PathVariable UUID edgeId) {
        patientPathwayService.deleteEdge(patientId, edgeId);
    }
}
