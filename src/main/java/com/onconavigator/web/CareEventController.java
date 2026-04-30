package com.onconavigator.web;

import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.CareEventResponse;
import com.onconavigator.web.dto.CreateCareEventRequest;
import com.onconavigator.web.dto.UpdateCareEventStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for care event management, nested under the patient resource.
 *
 * <p>Care events are the primary data entry path in V1 — nurse navigators manually
 * record events (consultations, surgeries, pathology results) as they occur.
 * Each create or update signals the Temporal pathway workflow to re-evaluate
 * the patient's care pathway immediately (event-driven side of D-05).
 *
 * <p>Write operations restricted to {@code CARE_COORDINATOR} and {@code ADMIN}.
 * Read operations require only authentication (all clinical staff need event history).
 *
 * <p>Security: PatientService.updateCareEventStatus verifies the care event belongs
 * to the patientId URL path variable before updating (T-03-10 BOLA mitigation).
 */
@RestController
@RequestMapping("/api/patients/{patientId}/care-events")
public class CareEventController {

    private final PatientService patientService;

    public CareEventController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * Lists all care events for a patient, ordered by event date descending.
     *
     * @param patientId the patient UUID from the URL path
     * @return list of care events as response DTOs
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CareEventResponse> listCareEvents(@PathVariable UUID patientId) {
        return patientService.listCareEvents(patientId);
    }

    /**
     * Records a new care event for a patient and signals the pathway workflow.
     *
     * <p>The signal causes the Temporal workflow to re-evaluate the patient's pathway
     * state immediately rather than waiting for the next daily sweep.
     *
     * @param patientId the patient UUID from the URL path
     * @param request   the care event details
     * @param jwt       the authenticated user's JWT (for actor identity)
     * @return the created care event response with assigned UUID
     */
    @PostMapping
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CareEventResponse addCareEvent(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateCareEventRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.addCareEvent(patientId, request, actorId);
    }

    /**
     * Updates the status of an existing care event and signals the pathway workflow.
     *
     * <p>Used to advance a care event through its lifecycle (e.g., PENDING → SCHEDULED
     * → COMPLETED). Each status change re-triggers pathway evaluation.
     *
     * @param patientId   the patient UUID from the URL path (used for ownership verification)
     * @param careEventId the care event UUID
     * @param request     the new status
     * @param jwt         the authenticated user's JWT (for actor identity)
     * @return the updated care event response
     */
    @PatchMapping("/{careEventId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public CareEventResponse updateCareEventStatus(
            @PathVariable UUID patientId,
            @PathVariable UUID careEventId,
            @Valid @RequestBody UpdateCareEventStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.updateCareEventStatus(patientId, careEventId, request, actorId);
    }
}
