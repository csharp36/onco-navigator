package com.onconavigator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PdfExtractionService}.
 *
 * <p>Tests use in-memory PDFs created via PDFBox (no external test fixtures required).
 * All tests exercise the PDFBox 3.x API ({@code Loader.loadPDF}, not the removed
 * {@code PDDocument.load()}).
 *
 * <p>Test cases cover:
 * <ul>
 *   <li>Text extraction from a valid text-based PDF</li>
 *   <li>{@code hasSelectableText} returns true for text-based PDFs</li>
 *   <li>{@code hasSelectableText} returns false for empty-page (image-only) PDFs</li>
 *   <li>RuntimeException thrown for invalid/corrupt input bytes</li>
 * </ul>
 */
class PdfExtractionServiceTest {

    private PdfExtractionService service;

    @BeforeEach
    void setUp() {
        service = new PdfExtractionService();
    }

    /**
     * Create a minimal in-memory PDF with text content using PDFBox 3.x API.
     */
    private byte[] createTextPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Create a minimal in-memory PDF with NO text content (blank page).
     */
    private byte[] createEmptyPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void extractText_returnsText_fromValidPdf() throws IOException {
        byte[] pdfBytes = createTextPdf("Test clinical document text");

        String result = service.extractText(pdfBytes);

        assertThat(result).contains("Test clinical document text");
    }

    @Test
    void hasSelectableText_returnsTrue_whenTextPresent() throws IOException {
        // Create a PDF with enough text to exceed the 50-char threshold
        byte[] pdfBytes = createTextPdf(
                "This is a test clinical document with enough text to pass the selectability threshold easily.");

        boolean result = service.hasSelectableText(pdfBytes);

        assertThat(result).isTrue();
    }

    @Test
    void hasSelectableText_returnsFalse_whenNoText() throws IOException {
        byte[] pdfBytes = createEmptyPagePdf();

        boolean result = service.hasSelectableText(pdfBytes);

        assertThat(result).isFalse();
    }

    @Test
    void extractText_throwsOnInvalidInput() {
        byte[] garbageBytes = "This is not a PDF file at all".getBytes();

        assertThatThrownBy(() -> service.extractText(garbageBytes))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PDF text extraction failed");
    }
}
