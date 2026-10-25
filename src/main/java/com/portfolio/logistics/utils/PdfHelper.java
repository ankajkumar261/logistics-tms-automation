package com.portfolio.logistics.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.Path;

/** Extract text from exported PDFs so tests can assert on their content. */
public final class PdfHelper {

    private PdfHelper() { }

    /** All text from the PDF (as laid out). */
    public static String text(Path pdf) {
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            return new PDFTextStripper().getText(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF " + pdf + ": " + e.getMessage(), e);
        }
    }

    /** True if the PDF text contains {needle}, ignoring case and whitespace differences. */
    public static boolean contains(Path pdf, String needle) {
        String hay = text(pdf).replaceAll("\\s+", " ").toLowerCase();
        String need = needle.replaceAll("\\s+", " ").toLowerCase();
        return hay.contains(need);
    }
}
