package com.onconavigator.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * OCR text extraction using Tesseract (via Tess4J) with confidence scoring.
 *
 * <p>Creates a NEW {@link Tesseract} instance per call for thread safety. Tesseract
 * instances maintain internal JNA pointers that are not thread-safe, and virtual threads
 * are enabled in this application ({@code spring.threads.virtual.enabled=true}). Sharing
 * a single instance across virtual threads causes native memory corruption
 * (see RESEARCH.md Pitfall 6).
 *
 * <p>Confidence scoring uses a text-quality heuristic: extracted text with more than
 * 100 characters is assigned a confidence of 75 (adequate for most clinical documents),
 * while shorter extractions receive a confidence of 40 (indicating poor OCR quality that
 * should trigger Claude vision fallback).
 *
 * <p>HIPAA note: Extracted text may contain PHI. This service does not log any content.
 * Log statements contain only non-PHI metadata (confidence scores, error messages).
 */
@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);

    /**
     * Confidence threshold below which Claude vision fallback should be used.
     * Documents with OCR confidence below this value are considered low quality.
     */
    public static final int OCR_CONFIDENCE_THRESHOLD = 60;

    private final String tessDataPath;

    public OcrExtractionService(
            @Value("${onconavigator.tessdata.path:/usr/share/tesseract-ocr/5/tessdata}") String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    /**
     * Result of an OCR extraction operation.
     *
     * @param text           the extracted text content (empty string if extraction failed)
     * @param meanConfidence heuristic confidence score (0-100); 0 indicates failure
     */
    public record OcrResult(String text, int meanConfidence) {}

    /**
     * Perform OCR on an image using a fresh Tesseract instance.
     *
     * <p>Creates a new Tesseract instance per call (thread safety with virtual threads).
     * Uses LSTM OCR engine mode for best accuracy on modern document layouts.
     *
     * @param image the input image (typically a rendered PDF page or uploaded JPEG/PNG)
     * @return OCR result with extracted text and confidence score
     */
    public OcrResult performOcr(BufferedImage image) {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage("eng");
            tesseract.setOcrEngineMode(1); // LSTM engine
            String text = tesseract.doOCR(image);

            // Confidence heuristic: substantial text output indicates successful recognition.
            // Tess4J's getAPI() is not reliably accessible across all builds;
            // use text quality heuristic instead of native API confidence.
            int confidence = (text != null && text.strip().length() > 100) ? 75 : 40;

            return new OcrResult(text != null ? text : "", confidence);
        } catch (TesseractException e) {
            log.warn("OCR extraction failed: {}", e.getMessage());
            return new OcrResult("", 0);
        }
    }
}
