package com.onconavigator.web;

import com.onconavigator.service.PathwayStatusService;
import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.CreatePatientRequest;
import com.onconavigator.web.dto.DeactivatePatientRequest;
import com.onconavigator.web.dto.PatientResponse;
import com.onconavigator.web.dto.PathwayStatusResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for patient CRUD operations and pathway status.
 *
 * <p>All endpoints require a valid JWT. Write operations (POST, PATCH) are restricted
 * to {@code CARE_COORDINATOR} and {@code ADMIN} roles. Read operations include
 * {@code NURSE_NAVIGATOR} to allow alert-queuing nurses to view patient details.
 *
 * <p>RBAC uses {@code hasRole('CARE_COORDINATOR')} — Spring Security prepends ROLE_
 * automatically. Never use {@code hasRole('ROLE_CARE_COORDINATOR')} (double prefix).
 *
 * <p>PHI note: Controllers log only actor UUIDs. Patient PHI is not referenced in
 * any log statement at this layer — handled by the service layer.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final PathwayStatusService pathwayStatusService;

    public PatientController(PatientService patientService,
                             PathwayStatusService pathwayStatusService) {
        this.patientService = patientService;
        this.pathwayStatusService = pathwayStatusService;
    }

    /**
     * Enrolls a new patient in pathway monitoring.
     *
     * <p>Creates the patient record, computes the HMAC token for deterministic MRN search,
     * and starts the Temporal pathway monitoring workflow.
     *
     * @param request the patient enrollment request with demographics
     * @param jwt     the authenticated user's JWT token (for actor identity)
     * @return the created patient response with assigned UUID
     */
    @PostMapping
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.createPatient(request, actorId);
    }

    /**
     * Lists all patients, optionally filtered by MRN.
     *
     * <p>When {@code mrn} is provided, uses HMAC-based deterministic search.
     * When absent, returns all patients with computed summary status.
     *
     * @param mrn optional MRN search parameter (plaintext; HMAC computed server-side)
     * @return list of matching patients as response DTOs
     */
    @GetMapping
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<PatientResponse> listPatients(
            @RequestParam(required = false) String mrn) {
        if (mrn != null && !mrn.isBlank()) {
            return patientService.findByMrn(mrn);
        }
        return patientService.findAll();
    }

    /**
     * Returns a single patient by UUID.
     *
     * @param id the patient UUID
     * @return the patient response DTO, or 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PatientResponse getPatient(@PathVariable UUID id) {
        return patientService.findById(id);
    }

    /**
     * Deactivates a patient and sends a deactivation signal to the Temporal workflow.
     *
     * <p>Marks the patient as INACTIVE. The Temporal workflow will close all open
     * alerts and terminate. The patient record is retained for audit purposes.
     *
     * @param id      the patient UUID
     * @param request the deactivation request with reason
     * @param jwt     the authenticated user's JWT token (for actor identity)
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePatient(@PathVariable UUID id,
                                  @Valid @RequestBody DeactivatePatientRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        patientService.deactivatePatient(id, request, actorId);
    }

    /**
     * Returns the per-step pathway status for a patient.
     *
     * <p>Each step is evaluated against the pathway template and the patient's
     * recorded care events. Steps report COMPLETED, OVERDUE, MISSING, or UPCOMING.
     *
     * @param id the patient UUID
     * @return pathway status response with one entry per template step
     */
    @GetMapping("/{id}/pathway-status")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStatusResponse getPathwayStatus(@PathVariable UUID id) {
        return pathwayStatusService.getPathwayStatus(id);
    }
}
