package com.onconavigator.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * PDF text extraction and page rendering using Apache PDFBox 3.x.
 *
 * <p>Provides two extraction capabilities:
 * <ul>
 *   <li>Text extraction from digitally-generated (text-selectable) PDFs via {@link PDFTextStripper}</li>
 *   <li>Page rendering to {@link BufferedImage} for OCR input or thumbnail generation</li>
 * </ul>
 *
 * <p>CRITICAL: Uses PDFBox 3.x API ({@link Loader#loadPDF}) — NOT the removed
 * {@code PDDocument.load()} from PDFBox 2.x (see RESEARCH.md Pitfall 2).
 *
 * <p>HIPAA note: Extracted text may contain PHI. This service does not log any content.
 * Callers must handle extracted text in compliance with PHI protection requirements.
 */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extract all text from a PDF document using PDFBox text stripper.
     *
     * @param fileBytes the raw PDF file bytes
     * @return extracted text content, sorted by position
     * @throws RuntimeException if PDF parsing fails (message contains no PHI)
     */
    public String extractText(byte[] fileBytes) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(fileBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("PDF text extraction failed: {}", e.getMessage());
            throw new RuntimeException("PDF text extraction failed", e);
        }
    }

    /**
     * Check whether a PDF has selectable (extractable) text content.
     *
     * <p>A heuristic based on extracted text length: if the stripped text contains more
     * than 50 characters, the PDF is considered text-based. Scanned/image-only PDFs
     * typically yield empty or very short strings from PDFTextStripper.
     *
     * @param fileBytes the raw PDF file bytes
     * @return true if the PDF contains more than 50 characters of selectable text
     */
    public boolean hasSelectableText(byte[] fileBytes) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(fileBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text != null && text.strip().length() > 50;
        } catch (IOException e) {
            log.warn("PDF text check failed, treating as non-selectable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Render the first page of a PDF as a 300 DPI RGB image.
     *
     * <p>Used as input for Tesseract OCR (scanned documents) and for PDF thumbnail
     * generation. Subsampling is enabled for performance on high-resolution renders.
     *
     * @param pdfBytes the raw PDF file bytes
     * @return BufferedImage of the first page at 300 DPI
     * @throws RuntimeException if rendering fails (message contains no PHI)
     */
    public BufferedImage renderFirstPage(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            return renderer.renderImageWithDPI(0, 300, ImageType.RGB);
        } catch (IOException e) {
            log.error("PDF page rendering failed: {}", e.getMessage());
            throw new RuntimeException("PDF page rendering failed", e);
        }
    }
}
