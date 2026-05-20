package dev.waiz.datamanager.service;

import java.io.File;
import java.io.IOException;

import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class OcrService {

    public String extractText(String filePath) throws IOException {
        Tesseract tesseract = new Tesseract();
        System.setProperty("jna.library.path", "C:\\Program Files\\Tesseract-OCR");
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng+msa");
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);

        try {
            File imageFile = new File(filePath);
            return tesseract.doOCR(imageFile);
        } catch (TesseractException e) {
            throw new IOException("Tesseract OCR failed: " + e.getMessage(), e);
        }
    }
}