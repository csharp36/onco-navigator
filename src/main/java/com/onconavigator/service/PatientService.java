package com.onconavigator.service;

import com.onconavigator.domain.CareEvent;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.PatientStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.CareEventRepository;
import com.onconavigator.repository.ClinicalDocumentRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.security.HmacTokenService;
import com.onconavigator.web.dto.CareEventResponse;
import com.onconavigator.web.dto.CreateCareEventRequest;
import com.onconavigator.web.dto.CreatePatientRequest;
import com.onconavigator.web.dto.DeactivatePatientRequest;
import com.onconavigator.web.dto.PatientResponse;
import com.onconavigator.web.dto.UpdateCareEventStatusRequest;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Service for patient lifecycle management.
 *
 * <p>Orchestrates patient CRUD operations with HMAC token computation and Temporal
 * workflow lifecycle integration. All write operations that create or modify patients
 * and care events also signal the Temporal pathway workflow.
 *
 * <p>PHI safety: Log statements contain ONLY patient UUIDs and care event UUIDs.
 * Patient names, DOBs, and MRNs must never appear in log statements.
 */
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final AlertRepository alertRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final PathwayService pathwayService;
    private final HmacTokenService hmacTokenService;

    public PatientService(PatientRepository patientRepository,
                          CareEventRepository careEventRepository,
                          AlertRepository alertRepository,
                          ClinicalDocumentRepository documentRepository,
                          PathwayService pathwayService,
                          HmacTokenService hmacTokenService) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.alertRepository = alertRepository;
        this.documentRepository = documentRepository;
        this.pathwayService = pathwayService;
        this.hmacTokenService = hmacTokenService;
    }

    /**
     * Creates a new patient, computes the HMAC token for deterministic MRN search,
     * and starts pathway monitoring via Temporal.
     *
     * <p>The Temporal workflow start call is made after the patient record is saved.
     * If a workflow is already running for this patient ID (e.g., idempotent retry),
     * the WorkflowExecutionAlreadyStarted exception is caught and logged as a warning.
     *
     * @param req     request with patient demographics and enrollment details
     * @param actorId UUID of the authenticated user performing the operation (for audit)
     * @return PatientResponse with the created patient data
     */
    public PatientResponse createPatient(CreatePatientRequest req, UUID actorId) {
        Patient patient = new Patient();
        patient.setFirstName(req.firstName());
        patient.setLastName(req.lastName());
        patient.setDateOfBirth(req.dateOfBirth());
        patient.setMrn(req.mrn());
        patient.setMrnHmacToken(hmacTokenService.computeMrnToken(req.mrn()));
        patient.setCancerType(req.cancerType());
        patient.setCancerStage(req.cancerStage());
        patient.setDiagnosisDate(req.diagnosisDate());
        patient.setAssignedNavigatorId(req.assignedNavigatorId());
        patient.setTreatingPhysician(req.treatingPhysician());
        patient.setStatus(PatientStatus.ACTIVE);
        patient.setCreatedBy(actorId);

        Patient saved = patientRepository.save(patient);

        try {
            pathwayService.startPathwayMonitoring(saved.getId(), saved.getCancerType());
        } catch (WorkflowExecutionAlreadyStarted e) {
            log.warn("Pathway workflow already started for patient {} — idempotent, ignoring",
                    saved.getId());
        }

        log.info("Created patient {} and started pathway monitoring", saved.getId());

        return toPatientResponse(saved);
    }

    /**
     * Returns all patients with computed summary status.
     *
     * @return list of all patients as response DTOs
     */
    public List<PatientResponse> findAll() {
        return patientRepository.findAll().stream()
                .map(this::toPatientResponse)
                .toList();
    }

    /**
     * Returns a single patient by ID.
     *
     * @param id the patient UUID
     * @return the patient response DTO
     * @throws ResponseStatusException 404 if not found
     */
    public PatientResponse findById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return toPatientResponse(patient);
    }

    /**
     * Searches for a patient by MRN using the HMAC index token.
     *
     * <p>MRN is AES-GCM encrypted with random IV — direct equality search is impossible.
     * The HMAC token is computed from the plaintext MRN and used for deterministic lookup.
     *
     * @param mrn the plaintext MRN to search for
     * @return list containing the matching patient, or empty list if not found
     */
    public List<PatientResponse> findByMrn(String mrn) {
        String hmacToken = hmacTokenService.computeMrnToken(mrn);
        return patientRepository.findByMrnHmacToken(hmacToken)
                .map(this::toPatientResponse)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Deactivates a patient and signals the Temporal workflow to terminate.
     *
     * @param id      the patient UUID
     * @param req     deactivation request with reason
     * @param actorId UUID of the authenticated user performing the operation
     * @throws ResponseStatusException 404 if patient not found
     */
    public void deactivatePatient(UUID id, DeactivatePatientRequest req, UUID actorId) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        patient.setStatus(PatientStatus.INACTIVE);
        patientRepository.save(patient);

        pathwayService.deactivatePatient(id, req.reason());

        log.info("Deactivated patient {}", id);
    }

    /**
     * Adds a care event for a patient and signals the Temporal pathway workflow.
     *
     * @param patientId the patient UUID
     * @param req       request with care event details
     * @param actorId   UUID of the authenticated user performing the operation
     * @return CareEventResponse with the created care event data
     * @throws ResponseStatusException 404 if patient not found
     */
    public CareEventResponse addCareEvent(UUID patientId, CreateCareEventRequest req, UUID actorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        CareEvent event = new CareEvent();
        event.setPatient(patient);
        event.setEventType(req.eventType());
        event.setEventDate(req.eventDate());
        event.setStatus(req.status());
        event.setNotes(req.notes());
        event.setDocumentId(req.documentId());
        event.setCreatedBy(actorId);

        CareEvent saved = careEventRepository.save(event);

        // Link the document back to this care event (bidirectional association)
        if (req.documentId() != null) {
            documentRepository.findById(req.documentId()).ifPresent(doc -> {
                doc.setCareEventId(saved.getId());
                documentRepository.save(doc);
            });
        }

        pathwayService.signalCareEventChanged(patientId, saved.getId());

        log.info("Added care event {} for patient {}", saved.getId(), patientId);

        return toCareEventResponse(saved);
    }

    /**
     * Updates the status of a care event and signals the Temporal pathway workflow.
     *
     * <p>Security: verifies that the care event belongs to the specified patientId
     * before updating, preventing cross-patient modification via crafted URL (T-03-10).
     *
     * @param patientId   the patient UUID from the URL path
     * @param ceId        the care event UUID
     * @param req         request with the new status
     * @param actorId     UUID of the authenticated user performing the operation
     * @return CareEventResponse with the updated care event data
     * @throws ResponseStatusException 404 if not found, 400 if care event does not belong to patient
     */
    public CareEventResponse updateCareEventStatus(UUID patientId, UUID ceId,
                                                   UpdateCareEventStatusRequest req, UUID actorId) {
        CareEvent event = careEventRepository.findById(ceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care event not found"));

        // Security check: verify care event belongs to the specified patient (T-03-10)
        if (!event.getPatient().getId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Care event does not belong to the specified patient");
        }

        event.setStatus(req.status());
        CareEvent saved = careEventRepository.save(event);

        pathwayService.signalCareEventChanged(patientId, ceId);

        log.info("Updated care event {} status for patient {}", ceId, patientId);

        return toCareEventResponse(saved);
    }

    /**
     * Lists all care events for a patient, ordered by event date descending.
     *
     * @param patientId the patient UUID
     * @return list of care events as response DTOs
     */
    public List<CareEventResponse> listCareEvents(UUID patientId) {
        return careEventRepository.findByPatient_IdOrderByEventDateDesc(patientId).stream()
                .map(this::toCareEventResponse)
                .toList();
    }

    /**
     * Counts active patients by status.
     *
     * <p>Used by DashboardController — controllers route through services, not repositories.
     *
     * @return count of ACTIVE patients
     */
    public long countActivePatients() {
        return patientRepository.countByStatus(PatientStatus.ACTIVE);
    }

    // ---- Private helpers ----

    /**
     * Maps a Patient entity to a PatientResponse DTO.
     * PHI fields (firstName, lastName, dateOfBirth, mrn) are already decrypted
     * by the EncryptionConverter when loaded from the database.
     */
    private PatientResponse toPatientResponse(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getDateOfBirth(),
                p.getMrn(),
                p.getCancerType(),
                p.getCancerStage(),
                p.getDiagnosisDate(),
                p.getAssignedNavigatorId(),
                p.getTreatingPhysician(),
                p.getStatus(),
                computeSummaryStatus(p),
                p.getCreatedAt()
        );
    }

    /**
     * Maps a CareEvent entity to a CareEventResponse DTO.
     * The notes field is already decrypted by EncryptionConverter when loaded.
     */
    private CareEventResponse toCareEventResponse(CareEvent e) {
        return new CareEventResponse(
                e.getId(),
                e.getPatient().getId(),
                e.getEventType(),
                e.getEventDate(),
                e.getStatus(),
                e.getNotes(),
                e.getPathwayStepId(),
                e.getCreatedAt()
        );
    }

    /**
     * Computes the display summary status for a patient.
     *
     * <ul>
     *   <li>INACTIVE patients → "Inactive"</li>
     *   <li>Patients with any OPEN alert → "Alert Active"</li>
     *   <li>Otherwise → "On Track"</li>
     * </ul>
     */
    private String computeSummaryStatus(Patient p) {
        if (p.getStatus() == PatientStatus.INACTIVE) {
            return "Inactive";
        }
        boolean hasOpenAlert = !alertRepository.findByPatientIdAndStatus(p.getId(), AlertStatus.OPEN).isEmpty();
        return hasOpenAlert ? "Alert Active" : "On Track";
    }
}
