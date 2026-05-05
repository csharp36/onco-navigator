package com.onconavigator.web;

import com.onconavigator.domain.ClinicalDocument;
import com.onconavigator.domain.Patient;
import com.onconavigator.repository.ClinicalDocumentRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.service.DocumentProcessingService;
import com.onconavigator.web.dto.DocumentSummaryResponse;
import com.onconavigator.web.dto.DocumentUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for clinical document upload and retrieval.
 *
 * <p>Provides three endpoints:
 * <ul>
 *   <li>POST /api/documents/upload — multipart file upload, triggers extraction-classify-match pipeline</li>
 *   <li>GET /api/documents/{documentId}/content — streams document bytes with correct content-type</li>
 *   <li>GET /api/documents/patient/{patientId} — lists document summaries for a patient (no blob)</li>
 * </ul>
 *
 * <p>All endpoints require {@code NURSE_NAVIGATOR}, {@code CARE_COORDINATOR}, or {@code ADMIN} role (BOLA mitigation T-04-11).
 * The content endpoint has additional in-method role verification as defense-in-depth.
 *
 * <p>HIPAA note: Log statements contain ONLY document UUIDs, actor UUIDs, and patient UUIDs.
 * File content, filenames, and extracted text are NEVER logged (PHI may be present in filenames
 * and document content).
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);

    // CR-02: Whitelist of safe content types that can be served inline.
    // Anything not on this list is forced to application/octet-stream (triggers browser download).
    private static final Set<String> SAFE_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    private final DocumentProcessingService documentProcessingService;
    private final ClinicalDocumentRepository documentRepository;
    private final PatientRepository patientRepository;

    public DocumentUploadController(DocumentProcessingService documentProcessingService,
                                     ClinicalDocumentRepository documentRepository,
                                     PatientRepository patientRepository) {
        this.documentProcessingService = documentProcessingService;
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Upload a clinical document (PDF, JPEG, or PNG) for processing.
     *
     * <p>The document goes through the extraction-classify-match pipeline:
     * text extraction (PDFBox/Tesseract/Claude vision), Claude classification,
     * and patient matching. Returns an ACCEPTED status since processing may be non-trivial.
     *
     * @param file      the uploaded file (multipart/form-data)
     * @param patientId optional pre-selected patient (when dropped on patient detail page, D-09)
     * @param jwt       the authenticated user's JWT
     * @return upload response with document ID, classification, and match status
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentUploadResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "patientId", required = false) UUID patientId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        log.info("Document upload initiated by actor {} for patient {}", actorId, patientId);
        return documentProcessingService.processUpload(file, patientId, actorId);
    }

    /**
     * Stream the content of a clinical document with the correct content-type header.
     *
     * <p>BOLA mitigation (T-04-11): Beyond the {@code @PreAuthorize} annotation check,
     * this method performs an additional in-method role verification as defense-in-depth.
     * V1 pilot uses role-based access (single practice, all coordinators access all patients).
     *
     * <p>TODO: V2 — Add patient-level BOLA enforcement for multi-practice deployment.
     * Verify doc.getPatient().getId() is in the user's assigned patient list.
     *
     * @param documentId the document UUID
     * @param jwt        the authenticated user's JWT
     * @return document bytes with Content-Type and Content-Disposition headers
     */
    @GetMapping("/{documentId}/content")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public ResponseEntity<byte[]> getDocumentContent(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        ClinicalDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        // BOLA defense-in-depth: verify requesting user has a permitted role.
        // ADMIN role has unrestricted access. CARE_COORDINATOR role is permitted for all patients
        // in V1 pilot (single practice, <500 patients). For V2 multi-practice, add patient-level
        // access control (e.g., verify doc.getPatientId() is in the user's assigned patient list).
        @SuppressWarnings("unchecked")
        List<String> roles = extractRoles(jwt);
        boolean hasPermittedRole = roles != null && roles.stream().anyMatch(r ->
                r.contains("NURSE_NAVIGATOR") || r.contains("CARE_COORDINATOR") || r.contains("ADMIN"));
        if (!hasPermittedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Insufficient role to access document content");
        }

        log.info("Document content retrieved: documentId={} by actor={}", documentId, jwt.getSubject());

        // CR-01: Sanitize filename to prevent HTTP header injection (response splitting, XSS).
        // Strip path separators, newlines, double-quotes, and shell-unsafe characters.
        String safeFilename = doc.getOriginalFilename()
                .replaceAll("[\\r\\n\"\\\\/:*?<>|]", "_");

        // CR-02: Validate stored content type against whitelist before serving.
        // Unknown/spoofed types are forced to octet-stream (triggers browser download, prevents XSS).
        String contentType = doc.getContentType();
        if (!SAFE_CONTENT_TYPES.contains(contentType)) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(safeFilename)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(doc.getContent());
    }

    /**
     * Get document metadata by ID (no blob content).
     */
    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public DocumentSummaryResponse getDocument(@PathVariable UUID documentId) {
        ClinicalDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return new DocumentSummaryResponse(
                doc.getId(), doc.getOriginalFilename(), doc.getContentType(),
                doc.getFileSizeBytes(), doc.getDocumentType(),
                doc.getClassificationSource(), doc.getCareEventId(), doc.getCreatedAt(),
                doc.getAlreadyCoveredEventTypes());
    }

    /**
     * Link an unlinked document to a patient. Used after the create-new-patient flow
     * where the document was uploaded before the patient existed.
     */
    @PatchMapping("/{documentId}/link-patient")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public DocumentSummaryResponse linkDocumentToPatient(
            @PathVariable UUID documentId,
            @RequestBody Map<String, UUID> body) {
        UUID patientId = body.get("patientId");
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }

        ClinicalDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        doc.setPatient(patient);
        doc = documentRepository.save(doc);

        return new DocumentSummaryResponse(
                doc.getId(), doc.getOriginalFilename(), doc.getContentType(),
                doc.getFileSizeBytes(), doc.getDocumentType(),
                doc.getClassificationSource(), doc.getCareEventId(), doc.getCreatedAt(),
                doc.getAlreadyCoveredEventTypes());
    }

    /**
     * List document summaries for a patient (no blob content loaded).
     *
     * @param patientId the patient UUID
     * @return list of document summaries, ordered most recent first
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public List<DocumentSummaryResponse> getDocumentsForPatient(@PathVariable UUID patientId) {
        return documentRepository.findByPatient_IdOrderByCreatedAtDesc(patientId).stream()
                .map(doc -> new DocumentSummaryResponse(
                        doc.getId(), doc.getOriginalFilename(), doc.getContentType(),
                        doc.getFileSizeBytes(), doc.getDocumentType(),
                        doc.getClassificationSource(), doc.getCareEventId(), doc.getCreatedAt(),
                        doc.getAlreadyCoveredEventTypes()))
                .toList();
    }

    /**
     * Extract roles from the JWT claim structure.
     *
     * <p>Keycloak stores roles in the nested {@code realm_access.roles} claim. Spring Security
     * may flatten this or leave it nested depending on configuration. This method handles both cases.
     *
     * @param jwt the authenticated user's JWT
     * @return list of role names, or null if not found
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        // Try direct claim first (some Spring Security configs flatten it)
        List<String> roles = jwt.getClaimAsStringList("realm_access.roles");
        if (roles != null) {
            return roles;
        }
        // Fallback: extract from nested realm_access claim structure
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List<?>) {
                return (List<String>) rolesObj;
            }
        }
        return null;
    }
}
