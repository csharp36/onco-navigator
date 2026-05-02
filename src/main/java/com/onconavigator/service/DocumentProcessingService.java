package com.onconavigator.service;

import com.onconavigator.ai.model.DocumentClassification;
import com.onconavigator.ai.service.ClaudeVisionService;
import com.onconavigator.ai.service.DocumentClassificationService;
import com.onconavigator.domain.ClinicalDocument;
import com.onconavigator.domain.Patient;
import com.onconavigator.repository.ClinicalDocumentRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.service.DocumentPatientMatchService.MatchResult;
import com.onconavigator.web.dto.DocumentUploadResponse;
import com.onconavigator.web.dto.DocumentUploadResponse.PatientCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the full document processing pipeline: extraction, classification,
 * patient matching, and persistence.
 *
 * <p>Pipeline steps (per D-01 through D-16):
 * <ol>
 *   <li>Validate upload (size, content type)</li>
 *   <li>Text extraction: PDFBox for text-selectable PDFs, Tesseract OCR for scanned/image,
 *       Claude vision fallback for low-confidence OCR</li>
 *   <li>Classification: Claude classifies document type and extracts patient identifiers
 *       (gated by feature flag for BAA compliance)</li>
 *   <li>Patient matching: HMAC MRN lookup first, name+DOB fallback second</li>
 *   <li>Persist ClinicalDocument entity with file bytes and metadata</li>
 * </ol>
 *
 * <p>HIPAA note: Log statements contain ONLY document UUIDs, document types, and match
 * statuses. File content, extracted text, and patient identifiers are NEVER logged.
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );

    private final PdfExtractionService pdfExtractionService;
    private final OcrExtractionService ocrExtractionService;
    private final ClaudeVisionService claudeVisionService;
    private final DocumentClassificationService classificationService;
    private final DocumentPatientMatchService patientMatchService;
    private final ClinicalDocumentRepository documentRepository;
    private final PatientRepository patientRepository;

    public DocumentProcessingService(PdfExtractionService pdfExtractionService,
                                     OcrExtractionService ocrExtractionService,
                                     ClaudeVisionService claudeVisionService,
                                     DocumentClassificationService classificationService,
                                     DocumentPatientMatchService patientMatchService,
                                     ClinicalDocumentRepository documentRepository,
                                     PatientRepository patientRepository) {
        this.pdfExtractionService = pdfExtractionService;
        this.ocrExtractionService = ocrExtractionService;
        this.claudeVisionService = claudeVisionService;
        this.classificationService = classificationService;
        this.patientMatchService = patientMatchService;
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Process a clinical document upload through the full extraction-classify-match pipeline.
     *
     * @param file               the uploaded file (PDF, JPEG, or PNG)
     * @param preSelectedPatientId patient ID when dropped on patient detail page (D-09), or null
     * @param actorId            UUID of the authenticated user performing the upload
     * @return response with document ID, classification, match status, and candidates
     * @throws ResponseStatusException BAD_REQUEST if file validation fails
     */
    @Transactional
    public DocumentUploadResponse processUpload(MultipartFile file, UUID preSelectedPatientId, UUID actorId) {
        // 1. Validate upload (T-04-09)
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        }

        String contentType = file.getContentType();

        // 2. Text extraction pipeline (D-01, D-02, D-03)
        ExtractionResult extraction = extractText(bytes, contentType);

        // 3. Classification (D-13)
        DocumentClassification classification = null;
        if (extraction.text != null && !extraction.text.isBlank()) {
            classification = classificationService.classify(extraction.text);
        }

        // 4. Patient matching (D-05, D-06, D-07)
        String matchStatus;
        UUID matchedPatientId = null;
        List<PatientCandidate> candidates = List.of();

        if (preSelectedPatientId != null) {
            // Dropped on patient detail page -- skip matching (D-09)
            matchStatus = "PRE_SELECTED";
            matchedPatientId = preSelectedPatientId;
        } else if (classification != null) {
            MatchResult matchResult = patientMatchService.matchPatient(classification);
            matchStatus = matchResult.status();
            matchedPatientId = matchResult.matchedPatientId();
            candidates = matchResult.candidates();
        } else {
            matchStatus = "NO_MATCH";
        }

        // 5. Determine document type and classification source
        String documentType = null;
        String classificationSource = "unclassified";
        if (classification != null) {
            documentType = classification.documentType();
            classificationSource = "claude";
        }

        // 6. Persist ClinicalDocument entity
        ClinicalDocument doc = new ClinicalDocument();
        doc.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        doc.setContentType(contentType);
        doc.setFileSizeBytes(bytes.length);
        doc.setDocumentType(documentType);
        doc.setClassificationSource(classificationSource);
        doc.setContent(bytes);
        doc.setExtractedText(extraction.text);
        doc.setExtractionConfidence(extraction.confidence);
        doc.setCreatedBy(actorId);

        // Link to patient if we have a match
        UUID effectivePatientId = matchedPatientId != null ? matchedPatientId : preSelectedPatientId;
        if (effectivePatientId != null) {
            Patient patient = patientRepository.findById(effectivePatientId).orElse(null);
            if (patient != null) {
                doc.setPatient(patient);
            }
        }

        // Save only if patient is linked (patient_id is NOT NULL in schema)
        if (doc.getPatient() != null) {
            doc = documentRepository.save(doc);
            log.info("Document {} processed: type={} matchStatus={}", doc.getId(), documentType, matchStatus);
            return new DocumentUploadResponse(
                    doc.getId(),
                    classification,
                    matchStatus,
                    candidates,
                    matchedPatientId
            );
        }

        // CR-03: Cannot persist without a patient -- return null documentId so the frontend
        // knows the document is not yet stored. The frontend must handle documentId === null
        // by disabling document preview and not passing it to care event creation.
        log.info("Document processed but not persisted (no patient link): type={} matchStatus={}",
                documentType, matchStatus);
        return new DocumentUploadResponse(
                null,
                classification,
                matchStatus,
                candidates,
                matchedPatientId
        );
    }

    /**
     * Validate the uploaded file meets size and content type requirements.
     *
     * @throws ResponseStatusException BAD_REQUEST if validation fails
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File exceeds maximum size of 20 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Accepted: PDF, JPEG, PNG");
        }
    }

    /**
     * Execute the multi-stage text extraction pipeline.
     *
     * <p>Pipeline branches based on content type:
     * <ul>
     *   <li>PDF: Try PDFBox text extraction first. If insufficient (&lt;50 chars),
     *       render first page to image and run OCR. If OCR confidence is below threshold,
     *       try Claude vision as fallback.</li>
     *   <li>Image (JPEG/PNG): Run OCR directly. If confidence is below threshold,
     *       try Claude vision as fallback.</li>
     * </ul>
     *
     * @return extraction result with text and optional confidence score
     */
    private ExtractionResult extractText(byte[] bytes, String contentType) {
        if ("application/pdf".equals(contentType)) {
            return extractFromPdf(bytes);
        } else if ("image/jpeg".equals(contentType) || "image/png".equals(contentType)) {
            return extractFromImage(bytes, contentType);
        }
        return new ExtractionResult(null, null);
    }

    /**
     * Extract text from a PDF using PDFBox, with OCR and Claude vision fallbacks.
     */
    private ExtractionResult extractFromPdf(byte[] pdfBytes) {
        // Try PDFBox text extraction first
        if (pdfExtractionService.hasSelectableText(pdfBytes)) {
            String text = pdfExtractionService.extractText(pdfBytes);
            if (text != null && text.strip().length() > 50) {
                return new ExtractionResult(text, null); // No confidence for PDFBox path
            }
        }

        // PDFBox insufficient -- render to image and try OCR
        BufferedImage image;
        try {
            image = pdfExtractionService.renderFirstPage(pdfBytes);
        } catch (RuntimeException e) {
            log.warn("PDF page rendering failed, cannot OCR: {}", e.getMessage());
            return new ExtractionResult(null, null);
        }

        OcrExtractionService.OcrResult ocrResult = ocrExtractionService.performOcr(image);

        if (ocrResult.meanConfidence() >= OcrExtractionService.OCR_CONFIDENCE_THRESHOLD) {
            return new ExtractionResult(ocrResult.text(), ocrResult.meanConfidence());
        }

        // OCR confidence below threshold -- try Claude vision fallback
        byte[] imageBytes = convertImageToBytes(image);
        if (imageBytes != null) {
            String visionText = claudeVisionService.extractTextFromImage(imageBytes, MimeTypeUtils.IMAGE_PNG);
            if (visionText != null && !visionText.isBlank()) {
                return new ExtractionResult(visionText, ocrResult.meanConfidence());
            }
        }

        // All extraction methods failed -- return OCR result if any, or null (D-04 blank wizard)
        if (ocrResult.text() != null && !ocrResult.text().isBlank()) {
            return new ExtractionResult(ocrResult.text(), ocrResult.meanConfidence());
        }
        return new ExtractionResult(null, null);
    }

    /**
     * Extract text from an image file (JPEG/PNG) using OCR with Claude vision fallback.
     */
    private ExtractionResult extractFromImage(byte[] imageBytes, String contentType) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            log.warn("Failed to read uploaded image: {}", e.getMessage());
            return new ExtractionResult(null, null);
        }

        if (image == null) {
            log.warn("ImageIO returned null for uploaded image");
            return new ExtractionResult(null, null);
        }

        OcrExtractionService.OcrResult ocrResult = ocrExtractionService.performOcr(image);

        if (ocrResult.meanConfidence() >= OcrExtractionService.OCR_CONFIDENCE_THRESHOLD) {
            return new ExtractionResult(ocrResult.text(), ocrResult.meanConfidence());
        }

        // OCR confidence below threshold -- try Claude vision
        MimeType mimeType = "image/jpeg".equals(contentType)
                ? MimeTypeUtils.IMAGE_JPEG
                : MimeTypeUtils.IMAGE_PNG;
        String visionText = claudeVisionService.extractTextFromImage(imageBytes, mimeType);
        if (visionText != null && !visionText.isBlank()) {
            return new ExtractionResult(visionText, ocrResult.meanConfidence());
        }

        // Return OCR result if any, or null (D-04 blank wizard)
        if (ocrResult.text() != null && !ocrResult.text().isBlank()) {
            return new ExtractionResult(ocrResult.text(), ocrResult.meanConfidence());
        }
        return new ExtractionResult(null, null);
    }

    /**
     * Convert a BufferedImage to PNG byte array for Claude vision API.
     */
    private byte[] convertImageToBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("Failed to convert image to bytes for Claude vision: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Internal extraction result holding text and optional OCR confidence.
     */
    private record ExtractionResult(String text, Integer confidence) {}
}
